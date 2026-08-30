import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:logging/logging.dart';

import '../../core/async_value.dart';
import '../../shared/user_role.dart';
import '../auth/auth_service.dart';
import '../limits/limit_quota.dart';
import '../sharing/resource_permission.dart';
import 'local_shopping_list_item.dart';
import 'shopping_list_item_store_service.dart';
import 'shopping_list_item_widget.dart';
import 'shopping_list_list_service.dart';
import 'shopping_list_repository.dart';
import 'shopping_list_sync_service.dart';
import 'undoable_action.dart';

class ShoppingListDetailService {
  static final _log = Logger('recipai.shopping_list.detail');

  final ShoppingListRepository _shoppingListRepository;
  final AuthService _authService;
  final ShoppingListListService _shoppingListListService;
  final ShoppingListItemStoreService _store;
  final ShoppingListSyncService _syncService;

  ShoppingListDetailService({
    required ShoppingListRepository shoppingListRepository,
    required AuthService authService,
    required ShoppingListListService shoppingListListService,
    required ShoppingListItemStoreService store,
    required ShoppingListSyncService syncService,
  }) : _shoppingListRepository = shoppingListRepository,
       _authService = authService,
       _shoppingListListService = shoppingListListService,
       _store = store,
       _syncService = syncService;

  /// The current user's role for the open list, gating owner-only actions
  /// (e.g. "Delete List"). Defaults to [UserRole.editor] — i.e. no delete
  /// rights — and is upgraded once [loadPermissions] resolves and confirms the
  /// user is an owner. Sourced from the `/permissions` request, not the detail
  /// fetch.
  final ValueNotifier<UserRole> _currentUserRole = ValueNotifier(
    UserRole.editor,
  );

  ValueListenable<UserRole> get currentUserRole => _currentUserRole;

  final ValueNotifier<AsyncValue<List<ResourcePermission>>> _permissions =
      ValueNotifier(const AsyncValue.loading());

  ValueListenable<AsyncValue<List<ResourcePermission>>> get permissions =>
      _permissions;

  final ValueNotifier<AsyncValue<List<LocalShoppingListItem>>> _items =
      ValueNotifier(const AsyncValue.loading());

  ValueListenable<AsyncValue<List<LocalShoppingListItem>>> get items => _items;

  final ValueNotifier<LimitQuota?> _itemQuota = ValueNotifier(null);

  ValueListenable<LimitQuota?> get itemQuota => _itemQuota;

  String? _openListId;
  ValueListenable<List<LocalShoppingListItem>>? _watchedListenable;
  VoidCallback? _itemsListener;

  /// The most recent destructive action's captured inverse, replayable by
  /// [undoLast]. A plain field, not a notifier — nothing observes it.
  UndoableAction? _pendingUndo;

  bool _isRenameRunning = false;
  bool _isDeleteRunning = false;
  bool _isLoadPermissionsRunning = false;
  bool _isShareShoppingListRunning = false;
  bool _isUnshareShoppingListRunning = false;

  Future<void> openShoppingList(String listId) async {
    _openListId = listId;
    // The service is a lazy singleton reused across every detail-screen visit, so the previous
    // list's quota has to go before the new one's is known — comparing this list's item count
    // against another list's quota is the one direction the fail-open rule must exclude.
    _itemQuota.value = null;
    await _store.openList(listId);
    final listenable = _store.watch(listId);
    _watchedListenable = listenable;
    _itemsListener = () => _items.value = AsyncValue.data(listenable.value);
    listenable.addListener(_itemsListener!);
    _items.value = AsyncValue.data(listenable.value);
    _syncService.startPolling(listId);
    unawaited(_syncService.requestDrain(listId));

    LimitQuota? quota;
    try {
      final token = await _authService.idToken;
      quota = await _shoppingListRepository.fetchItemQuota(listId, token);
    } catch (error, stackTrace) {
      _log.warning(
        'Failed to load item quota (listId=$listId)',
        error,
        stackTrace,
      );
    }
    // A quota that arrives after the user has moved on belongs to a list nobody is looking at.
    if (_openListId == listId) {
      _itemQuota.value = quota;
    }
  }

  /// This list's sync status (syncing / notSyncing / failure), driving the
  /// detail screen's persistent bottom failure banner.
  ValueListenable<SyncStatus> get syncStatus =>
      _syncService.syncStatusFor(_openListId!);

  /// Rejection events for this list only; the screen, while mounted,
  /// subscribes and shows a toast per event.
  Stream<RejectionEvent> get rejections =>
      _syncService.rejections.where((e) => e.listId == _openListId);

  /// Re-kicks this list's stalled queue (from the failure banner's retry
  /// button).
  Future<void> retrySync() => _syncService.retry(_openListId!);

  /// Adds an item, inserting it after [afterLocalId] when given.
  Future<void> addItem(ItemChanged parsed, {String? afterLocalId}) async {
    _log.info('addItem name="${parsed.name}"');
    final listId = _openListId;
    if (listId == null) return;
    await _store.applyCreate(
      listId,
      name: parsed.name,
      quantity: parsed.quantity,
      unit: parsed.unit,
      afterLocalId: afterLocalId,
    );
    unawaited(_syncService.requestDrain(listId));
  }

  Future<void> editItem(String localId, ItemChanged parsed) async {
    _log.info('editItem (localId=$localId) name="${parsed.name}"');
    await _store.applyEdit(
      _openListId!,
      localId,
      name: parsed.name,
      quantity: parsed.quantity,
      unit: parsed.unit,
    );
    _requestDrainForOpenList();
  }

  Future<void> toggleChecked(String localId, bool checked) async {
    _log.info('toggleChecked (localId=$localId, checked=$checked)');
    await _store.applyChecked(_openListId!, localId, checked);
    _requestDrainForOpenList();
  }

  Future<UndoableAction?> deleteItem(String localId) async {
    _log.info('deleteItem (localId=$localId)');
    final removed = await _store.applyDelete(_openListId!, localId);
    _requestDrainForOpenList();
    if (removed == null) return null;
    return _pendingUndo = DeletedItemsUndo([removed]);
  }

  /// Reorders the item at [oldIndex] to [newIndex] within [items] (a section
  /// sorted by position), computing its new fractional position from the
  /// neighbours at the destination.
  Future<void> reorderItem(
    List<LocalShoppingListItem> items,
    int oldIndex,
    int newIndex,
  ) async {
    final item = items[oldIndex];
    _log.info('reorderItem (localId=${item.localId}, newIndex=$newIndex)');
    if (oldIndex == newIndex) return;
    final newPosition = _reorderPosition(items, oldIndex, newIndex);
    await _store.applyReorder(_openListId!, item.localId, newPosition);
    _requestDrainForOpenList();
  }

  /// Computes the fractional position for an item moved from [oldIndex] to
  /// [correctedNewIndex] (after applying Flutter's reorder index correction).
  /// Uses the sorted list WITHOUT the moved item to find the neighbours.
  double _reorderPosition(
    List<LocalShoppingListItem> items,
    int oldIndex,
    int correctedNewIndex,
  ) {
    final without = List.of(items)..removeAt(oldIndex);
    if (without.isEmpty) return 1.0;
    if (correctedNewIndex <= 0) return without.first.position / 2.0;
    if (correctedNewIndex >= without.length) return without.last.position + 1.0;
    return (without[correctedNewIndex - 1].position +
            without[correctedNewIndex].position) /
        2.0;
  }

  Future<UndoableAction?> deleteAllChecked() async {
    final listId = _openListId;
    if (listId == null) return null;
    final removed = await _store.deleteAllChecked(listId);
    _requestDrainForOpenList();
    _log.info('deleteAllChecked (count=${removed.length})');
    if (removed.isEmpty) return null;
    return _pendingUndo = DeletedItemsUndo(removed);
  }

  Future<UndoableAction?> uncheckAll() async {
    final listId = _openListId;
    if (listId == null) return null;
    final flipped = await _store.uncheckAll(listId);
    _requestDrainForOpenList();
    _log.info('uncheckAll (count=${flipped.length})');
    if (flipped.isEmpty) return null;
    return _pendingUndo = UncheckedItemsUndo(flipped);
  }

  /// Replays the most recent destructive action's inverse, if any: deleted
  /// items are re-created (fresh identity), unchecked items are re-checked.
  /// Clears the slot before replaying, so a late or duplicate tap is a no-op.
  Future<void> undoLast() async {
    final action = _pendingUndo;
    if (action == null) return;
    _pendingUndo = null;

    final listId = _openListId;
    if (listId == null) return;

    _log.info('undoLast (${action.runtimeType}, count=${action.itemCount})');
    switch (action) {
      case DeletedItemsUndo(:final items):
        await _store.applyRestore(listId, items);
      case UncheckedItemsUndo(:final localIds):
        await _store.applyCheckedAll(listId, localIds, true);
    }
    _requestDrainForOpenList();
  }

  void _requestDrainForOpenList() {
    final listId = _openListId;
    if (listId != null) unawaited(_syncService.requestDrain(listId));
  }

  Future<void> renameShoppingList(String id, String newName) async {
    if (_isRenameRunning) return;
    _isRenameRunning = true;

    _log.info('renameShoppingList start (listId=$id)');
    try {
      final token = await _authService.idToken;
      await _shoppingListRepository.updateShoppingList(id, newName, token);
      await _shoppingListListService.loadShoppingLists();
    } catch (e) {
      _log.warning('renameShoppingList failed (listId=$id)', e);
      rethrow;
    } finally {
      _isRenameRunning = false;
    }
  }

  Future<void> deleteShoppingList(String id) async {
    if (_isDeleteRunning) return;
    _isDeleteRunning = true;

    _log.info('deleteShoppingList start (listId=$id)');
    try {
      final token = await _authService.idToken;
      await _shoppingListRepository.deleteShoppingList(id, token);
      await _shoppingListListService.loadShoppingLists();
    } catch (e) {
      _log.warning('deleteShoppingList failed (listId=$id)', e);
      rethrow;
    } finally {
      _isDeleteRunning = false;
    }
  }

  Future<void> loadPermissions(String id) async {
    if (_isLoadPermissionsRunning) return;
    _isLoadPermissionsRunning = true;

    _permissions.value = const AsyncValue.loading();

    _permissions.value = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      final permissions = await _shoppingListRepository.fetchPermissions(
        id,
        token,
      );
      for (final permission in permissions) {
        if (permission.pending) continue;
        if (permission.email == _authService.email) {
          _currentUserRole.value = permission.role;
          break;
        }
      }
      return permissions;
    });

    _isLoadPermissionsRunning = false;
  }

  Future<void> shareShoppingList(String email) async {
    if (_isShareShoppingListRunning) return;
    _isShareShoppingListRunning = true;

    final shoppingListId = _openListId;
    if (shoppingListId == null) {
      _isShareShoppingListRunning = false;
      throw Exception('Shopping list not loaded');
    }

    final result = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      return _shoppingListRepository.shareShoppingList(
        shoppingListId,
        email,
        token,
      );
    });

    if (result is AsyncData) {
      await loadPermissions(shoppingListId);
      await _shoppingListListService.loadShoppingLists();
    }

    _isShareShoppingListRunning = false;

    if (result is AsyncError) {
      throw result.error;
    }
  }

  Future<void> unshareShoppingList(String email) async {
    if (_isUnshareShoppingListRunning) return;
    _isUnshareShoppingListRunning = true;

    final shoppingListId = _openListId;
    if (shoppingListId == null) {
      _isUnshareShoppingListRunning = false;
      throw Exception('Shopping list not loaded');
    }

    final result = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      return _shoppingListRepository.unshareShoppingList(
        shoppingListId,
        email,
        token,
      );
    });

    if (result is AsyncData) {
      await loadPermissions(shoppingListId);
      await _shoppingListListService.loadShoppingLists();
    }

    _isUnshareShoppingListRunning = false;

    if (result is AsyncError) {
      throw result.error;
    }
  }

  void dispose() {
    _pendingUndo = null;
    if (_openListId != null) {
      _syncService.stopPolling(_openListId!);
    }
    if (_itemsListener != null && _watchedListenable != null) {
      _watchedListenable!.removeListener(_itemsListener!);
    }
    _currentUserRole.dispose();
    _permissions.dispose();
    _items.dispose();
    _itemQuota.dispose();
  }
}
