import 'package:flutter/foundation.dart';
import 'package:logging/logging.dart';

import '../../core/async_value.dart';
import '../../core/widgets/sharing_dialog.dart';
import '../auth/auth_service.dart';
import 'local_shopping_list_item.dart';
import 'shopping_list_detail.dart';
import 'shopping_list_item_repository.dart';
import 'shopping_list_item_widget.dart';
import 'shopping_list_list_service.dart';
import 'shopping_list_repository.dart';
import 'shopping_list_sync_service.dart';

class ShoppingListDetailService {
  static final _log = Logger('recipai.shopping_list.detail');

  final ShoppingListRepository _shoppingListRepository;
  final AuthService _authService;
  final ShoppingListListService _shoppingListListService;
  final ShoppingListItemRepository _itemRepository;
  final ShoppingListSyncService _syncService;

  ShoppingListDetailService({
    required ShoppingListRepository shoppingListRepository,
    required AuthService authService,
    required ShoppingListListService shoppingListListService,
    required ShoppingListItemRepository itemRepository,
    required ShoppingListSyncService syncService,
  }) : _shoppingListRepository = shoppingListRepository,
       _authService = authService,
       _shoppingListListService = shoppingListListService,
       _itemRepository = itemRepository,
       _syncService = syncService;

  final ValueNotifier<AsyncValue<ShoppingListDetail>> _shoppingListDetail =
      ValueNotifier(const AsyncValue.loading());

  ValueListenable<AsyncValue<ShoppingListDetail>> get shoppingListDetail =>
      _shoppingListDetail;

  final ValueNotifier<AsyncValue<List<SharedUser>>> _sharedUsers =
      ValueNotifier(const AsyncValue.loading());

  ValueListenable<AsyncValue<List<SharedUser>>> get sharedUsers => _sharedUsers;

  final ValueNotifier<AsyncValue<List<LocalShoppingListItem>>> _items =
      ValueNotifier(const AsyncValue.loading());

  ValueListenable<AsyncValue<List<LocalShoppingListItem>>> get items => _items;

  String? _openListId;
  ValueListenable<List<LocalShoppingListItem>>? _watchedListenable;
  VoidCallback? _itemsListener;

  bool _isLoadShoppingListDetailRunning = false;
  bool _isRenameRunning = false;
  bool _isDeleteRunning = false;
  bool _isLoadSharedUsersRunning = false;
  bool _isShareShoppingListRunning = false;
  bool _isUnshareShoppingListRunning = false;

  Future<void> loadShoppingListDetail(String id) async {
    if (_isLoadShoppingListDetailRunning) return;
    _isLoadShoppingListDetailRunning = true;

    _log.fine('loadShoppingListDetail start (listId=$id)');
    _shoppingListDetail.value = const AsyncValue.loading();
    final result = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      return _shoppingListRepository.fetchShoppingListDetail(id, token);
    });
    _shoppingListDetail.value = result;

    switch (result) {
      case AsyncData(:final value):
        _log.info(
          'loadShoppingListDetail loaded (listId=$id, items=${value.items.length})',
        );
      case AsyncError(:final error):
        _log.warning('loadShoppingListDetail failed (listId=$id)', error);
      case AsyncLoading():
        break;
    }

    _isLoadShoppingListDetailRunning = false;
  }

  Future<void> openShoppingList(String listId) async {
    _openListId = listId;
    await _itemRepository.openList(listId);
    final listenable = _itemRepository.watch(listId);
    _watchedListenable = listenable;
    _itemsListener = () => _items.value = AsyncValue.data(listenable.value);
    listenable.addListener(_itemsListener!);
    _items.value = AsyncValue.data(listenable.value);
    _syncService.requestDrain(listId);
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
    await _itemRepository.applyCreate(
      listId,
      name: parsed.name,
      quantity: parsed.quantity,
      unit: parsed.unit,
      afterLocalId: afterLocalId,
    );
    _syncService.requestDrain(listId);
  }

  Future<void> editItem(String localId, ItemChanged parsed) async {
    await _itemRepository.applyEdit(
      localId,
      name: parsed.name,
      quantity: parsed.quantity,
      unit: parsed.unit,
    );
    _requestDrainForOpenList();
  }

  Future<void> toggleChecked(String localId, bool checked) async {
    await _itemRepository.applyChecked(localId, checked);
    _requestDrainForOpenList();
  }

  Future<void> deleteItem(String localId) async {
    await _itemRepository.applyDelete(localId);
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
    final newPosition = _reorderPosition(items, oldIndex, newIndex);
    await _itemRepository.applyReorder(item.localId, newPosition);
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
    final current = _items.value.valueOrNull;
    if (current == null) return;
    for (final item in current.where((i) => i.checked).toList()) {
      await _itemRepository.applyDelete(item.localId);
    }
    _requestDrainForOpenList();
  }

  Future<void> uncheckAll() async {
    final current = _items.value.valueOrNull;
    if (current == null) return;
    for (final item in current.where((i) => i.checked).toList()) {
      await _itemRepository.applyChecked(item.localId, false);
    }
    _requestDrainForOpenList();
  }

  void _requestDrainForOpenList() {
    final listId = _openListId;
    if (listId != null) _syncService.requestDrain(listId);
  }

  Future<void> renameShoppingList(String id, String newName) async {
    if (_isRenameRunning) return;
    _isRenameRunning = true;

    _log.info('renameShoppingList start (listId=$id)');
    try {
      final token = await _authService.idToken;
      await _shoppingListRepository.updateShoppingList(id, newName, token);
      await loadShoppingListDetail(id);
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

    final shoppingListDetail = _shoppingListDetail.value;
    if (shoppingListDetail is! AsyncData<ShoppingListDetail>) {
      _isShareShoppingListRunning = false;
      throw Exception('Shopping list not loaded');
    }
    final shoppingListId = shoppingListDetail.value.id;

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

    final shoppingListDetail = _shoppingListDetail.value;
    if (shoppingListDetail is! AsyncData<ShoppingListDetail>) {
      _isUnshareShoppingListRunning = false;
      throw Exception('Shopping list not loaded');
    }
    final shoppingListId = shoppingListDetail.value.id;

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
    if (_itemsListener != null && _watchedListenable != null) {
      _watchedListenable!.removeListener(_itemsListener!);
    }
    _shoppingListDetail.dispose();
    _sharedUsers.dispose();
    _items.dispose();
  }
}
