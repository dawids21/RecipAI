import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:logging/logging.dart';

import '../../core/async_value.dart';
import '../../core/widgets/sharing_dialog.dart';
import '../../shared/user_role.dart';
import '../auth/auth_service.dart';
import 'local_shopping_list_item.dart';
import 'shopping_list_item_store_service.dart';
import 'shopping_list_item_widget.dart';
import 'shopping_list_list_service.dart';
import 'shopping_list_repository.dart';
import 'shopping_list_sync_service.dart';

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
  /// rights — and is upgraded once [loadSharedUsers] resolves and confirms the
  /// user is an owner. Sourced from the `/users` request, not the detail fetch.
  final ValueNotifier<UserRole> _currentUserRole = ValueNotifier(
    UserRole.editor,
  );

  ValueListenable<UserRole> get currentUserRole => _currentUserRole;

  final ValueNotifier<AsyncValue<List<SharedUser>>> _sharedUsers =
      ValueNotifier(const AsyncValue.loading());

  ValueListenable<AsyncValue<List<SharedUser>>> get sharedUsers => _sharedUsers;

  final ValueNotifier<AsyncValue<List<LocalShoppingListItem>>> _items =
      ValueNotifier(const AsyncValue.loading());

  ValueListenable<AsyncValue<List<LocalShoppingListItem>>> get items => _items;

  String? _openListId;
  ValueListenable<List<LocalShoppingListItem>>? _watchedListenable;
  VoidCallback? _itemsListener;

  bool _isRenameRunning = false;
  bool _isDeleteRunning = false;
  bool _isLoadSharedUsersRunning = false;
  bool _isShareShoppingListRunning = false;
  bool _isUnshareShoppingListRunning = false;

  Future<void> openShoppingList(String listId) async {
    _openListId = listId;
    await _store.openList(listId);
    final listenable = _store.watch(listId);
    _watchedListenable = listenable;
    _itemsListener = () => _items.value = AsyncValue.data(listenable.value);
    listenable.addListener(_itemsListener!);
    _items.value = AsyncValue.data(listenable.value);
    _syncService.startPolling(listId);
    unawaited(_syncService.requestDrain(listId));
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
    final listId = _openListId;
    if (listId == null) return;
    _log.info('addItem name="${parsed.name}"');
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

  Future<void> deleteItem(String localId) async {
    _log.info('deleteItem (localId=$localId)');
    await _store.applyDelete(_openListId!, localId);
    _requestDrainForOpenList();
  }

  /// Reorders the item at [oldIndex] to [newIndex] within [items] (a section
  /// sorted by position), computing its new fractional position from the
  /// neighbours at the destination.
  Future<void> reorderItem(
    List<LocalShoppingListItem> items,
    int oldIndex,
    int newIndex,
  ) async {
    if (oldIndex == newIndex) return;
    final item = items[oldIndex];
    _log.info('reorderItem (localId=${item.localId}, newIndex=$newIndex)');
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

  Future<void> deleteAllChecked() async {
    final listId = _openListId;
    if (listId == null) return;
    final count = _items.value.valueOrNull?.where((i) => i.checked).length ?? 0;
    _log.info('deleteAllChecked (count=$count)');
    await _store.deleteAllChecked(listId);
    _requestDrainForOpenList();
  }

  Future<void> uncheckAll() async {
    final listId = _openListId;
    if (listId == null) return;
    final count = _items.value.valueOrNull?.where((i) => i.checked).length ?? 0;
    _log.info('uncheckAll (count=$count)');
    await _store.uncheckAll(listId);
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

  Future<void> loadSharedUsers(String id) async {
    if (_isLoadSharedUsersRunning) return;
    _isLoadSharedUsersRunning = true;

    _sharedUsers.value = const AsyncValue.loading();

    _sharedUsers.value = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      final permissions = await _shoppingListRepository.fetchSharedUsers(
        id,
        token,
      );
      final currentUserEmail = _authService.email;
      for (final permission in permissions) {
        if (permission.email == currentUserEmail) {
          _currentUserRole.value = permission.role;
          break;
        }
      }
      return permissions.map((permission) {
        return SharedUser(
          email: permission.email,
          role: permission.role.displayName,
          isCurrentUser: permission.email == currentUserEmail,
        );
      }).toList();
    });

    _isLoadSharedUsersRunning = false;
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
      await loadSharedUsers(shoppingListId);
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
      await loadSharedUsers(shoppingListId);
      await _shoppingListListService.loadShoppingLists();
    }

    _isUnshareShoppingListRunning = false;

    if (result is AsyncError) {
      throw result.error;
    }
  }

  void dispose() {
    if (_openListId != null) {
      _syncService.stopPolling(_openListId!);
    }
    if (_itemsListener != null && _watchedListenable != null) {
      _watchedListenable!.removeListener(_itemsListener!);
    }
    _currentUserRole.dispose();
    _sharedUsers.dispose();
    _items.dispose();
  }
}
