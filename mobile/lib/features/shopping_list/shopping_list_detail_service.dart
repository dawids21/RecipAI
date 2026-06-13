import 'dart:async';

import 'package:collection/collection.dart';
import 'package:flutter/foundation.dart';
import 'package:logging/logging.dart';

import '../../core/async_value.dart';
import '../../core/widgets/sharing_dialog.dart';
import '../auth/auth_service.dart';
import 'shopping_list_detail.dart';
import 'shopping_list_item.dart';
import 'shopping_list_list_service.dart';
import 'shopping_list_operation.dart';
import 'shopping_list_repository.dart';
import 'shopping_list_sync_service.dart';

class ShoppingListDetailService {
  static final _log = Logger('recipai.shopping_list.detail');

  final ShoppingListRepository _shoppingListRepository;
  final AuthService _authService;
  final ShoppingListListService _shoppingListListService;
  final ShoppingListSyncService _syncService;

  ShoppingListDetailService({
    required ShoppingListRepository shoppingListRepository,
    required AuthService authService,
    required ShoppingListListService shoppingListListService,
    required ShoppingListSyncService syncService,
  }) : _shoppingListRepository = shoppingListRepository,
       _authService = authService,
       _shoppingListListService = shoppingListListService,
       _syncService = syncService;

  final ValueNotifier<AsyncValue<ShoppingListDetail>> _shoppingListDetail =
      ValueNotifier(const AsyncValue.loading());

  ValueListenable<AsyncValue<ShoppingListDetail>> get shoppingListDetail =>
      _shoppingListDetail;

  final ValueNotifier<AsyncValue<List<SharedUser>>> _sharedUsers =
      ValueNotifier(const AsyncValue.loading());

  ValueListenable<AsyncValue<List<SharedUser>>> get sharedUsers => _sharedUsers;

  bool _isLoadShoppingListDetailRunning = false;
  bool _isRenameRunning = false;
  bool _isDeleteRunning = false;
  bool _isLoadSharedUsersRunning = false;
  bool _isShareShoppingListRunning = false;
  bool _isUnshareShoppingListRunning = false;

  String? _currentSyncingListId;
  Timer? _periodicFetchTimer;
  StreamSubscription<SyncEvent>? _syncEventsSubscription;
  VoidCallback? _onConflictCallback;
  ValueChanged<String>? _onErrorCallback;

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

  void startSyncing({
    required String listId,
    VoidCallback? onConflict,
    ValueChanged<String>? onError,
  }) {
    _log.fine('startSyncing (listId=$listId)');
    _currentSyncingListId = listId;
    _onConflictCallback = onConflict;
    _onErrorCallback = onError;
    _syncEventsSubscription = _syncService
        .events(listId)
        .listen(_handleSyncEvent);
    _periodicFetchTimer = Timer.periodic(
      const Duration(seconds: 10),
      (_) => _onPeriodicFetch(),
    );
  }

  void stopSyncing() {
    _log.fine('stopSyncing (listId=$_currentSyncingListId)');
    _periodicFetchTimer?.cancel();
    _periodicFetchTimer = null;
    _syncEventsSubscription?.cancel();
    _syncEventsSubscription = null;
    _currentSyncingListId = null;
    _onConflictCallback = null;
    _onErrorCallback = null;
  }

  void pauseSyncing() {
    if (_currentSyncingListId != null) {
      _log.fine('pauseSyncing (listId=$_currentSyncingListId)');
      _periodicFetchTimer?.cancel();
      _periodicFetchTimer = null;
    }
  }

  void resumeSyncing() {
    if (_currentSyncingListId != null) {
      _log.fine('resumeSyncing (listId=$_currentSyncingListId)');
      _periodicFetchTimer = Timer.periodic(
        const Duration(seconds: 10),
        (_) => _onPeriodicFetch(),
      );
      _onPeriodicFetch();
    }
  }

  ValueListenable<bool> syncStatus(String listId) {
    return _syncService.syncStatus(listId);
  }

  Future<void> _onPeriodicFetch() async {
    if (_currentSyncingListId == null) return;
    final id = _currentSyncingListId!;

    if (_syncService.syncStatus(id).value == true ||
        _syncService.pendingOperations(id).isNotEmpty) {
      _log.fine('_onPeriodicFetch skipped (listId=$id, syncing/pending)');
      return;
    }

    _log.fine('_onPeriodicFetch running (listId=$id)');
    try {
      final token = await _authService.idToken;
      final detail = await _shoppingListRepository.fetchShoppingListDetail(
        id,
        token,
      );
      _shoppingListDetail.value = AsyncData(detail);
    } catch (e) {
      // Behaviour preserved (silent to the user); previously-swallowed error is
      // now logged so silent background failures are visible in the trace.
      _log.warning('_onPeriodicFetch failed (listId=$id)', e);
    }
  }

  void _handleSyncEvent(SyncEvent event) {
    switch (event) {
      case ItemSynced(:final submittedItemId, :final serverItem):
        _log.fine(
          '_handleSyncEvent ItemSynced ($submittedItemId -> ${serverItem.id})',
        );
        _handleItemSynced(event);
      case SyncConflict():
        _log.info('_handleSyncEvent SyncConflict -> refetch');
        _handleConflict();
      case SyncFailed(:final message):
        _log.warning('_handleSyncEvent SyncFailed: $message');
        _onErrorCallback?.call(message);
    }
  }

  void _handleItemSynced(ItemSynced e) {
    final currentState = _shoppingListDetail.value;
    if (currentState is! AsyncData<ShoppingListDetail>) return;

    final detail = currentState.value;
    final itemIndex = detail.items.indexWhere(
      (item) => item.id == e.submittedItemId,
    );

    // If no item matches, a later optimistic op (e.g. delete) has already
    // removed it — that absence is authoritative; do nothing.
    if (itemIndex == -1) return;

    final updatedItems = detail.items.toList();
    updatedItems[itemIndex] = e.serverItem;

    var updatedDetail = ShoppingListDetail(
      id: detail.id,
      name: detail.name,
      items: updatedItems,
      role: detail.role,
    );

    // Re-apply pending ops for this item so subsequent optimistic changes stay
    // reflected. _replaceValuesInQueue in sync service rewrites ops to the
    // server id *before* emitting ItemSynced, so pendingOperations already
    // carries the server id and the filter below matches correctly. Do not
    // move the emit before _replaceValuesInQueue in sync service — this
    // ordering is load-bearing.
    final pending = _syncService.pendingOperations(_currentSyncingListId!);
    for (final op in pending) {
      if (op.itemId == e.serverItem.id) {
        updatedDetail = applyOperation(updatedDetail, op);
      }
    }

    _shoppingListDetail.value = AsyncData(updatedDetail);
  }

  Future<void> _handleConflict() async {
    final listId = _currentSyncingListId;
    if (listId == null) return;

    _shoppingListDetail.value = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      var detail = await _shoppingListRepository.fetchShoppingListDetail(
        listId,
        token,
      );
      // Re-apply still-pending ops so optimistic state for non-conflicted
      // items survives the refetch. Without this, those changes would only
      // reappear once each op round-trips and ItemSynced lands.
      for (final op in _syncService.pendingOperations(listId)) {
        detail = applyOperation(detail, op);
      }
      return detail;
    });
    _onConflictCallback?.call();
  }

  void processOperation(ShoppingListOperation operation) async {
    final currentState = _shoppingListDetail.value;
    if (currentState is! AsyncData<ShoppingListDetail>) return;

    final detail = currentState.value;

    // Item name is low sensitivity and useful for repro, but only logged for
    // add/update where it carries meaning.
    final name = switch (operation) {
      AddItemOperation(:final itemName) => ' name="$itemName"',
      UpdateItemOperation(:final itemName) => ' name="$itemName"',
      _ => '',
    };
    _log.info(
      'processOperation ${operation.runtimeType} '
      '(itemId=${operation.itemId})$name',
    );

    final updatedDetail = applyOperation(detail, operation);

    _shoppingListDetail.value = AsyncData(updatedDetail);

    _syncService.queueOperation(detail.id, operation);
  }

  List<ShoppingListItem> _getCheckedItems() {
    final currentState = _shoppingListDetail.value;
    if (currentState is! AsyncData<ShoppingListDetail>) return [];

    final detail = currentState.value;
    return detail.items.where((item) => item.checked).toList();
  }

  void deleteAllCheckedItems() {
    final checkedItems = _getCheckedItems();
    _log.info('deleteAllCheckedItems (count=${checkedItems.length})');
    for (final item in checkedItems) {
      final operation = DeleteItemOperation(
        itemId: item.id,
        itemVersion: item.version,
      );
      processOperation(operation);
    }
  }

  void uncheckAllItems() {
    final checkedItems = _getCheckedItems();
    _log.info('uncheckAllItems (count=${checkedItems.length})');
    for (final item in checkedItems) {
      final operation = UncheckItemOperation(
        itemId: item.id,
        itemVersion: item.version,
      );
      processOperation(operation);
    }
  }

  static ShoppingListDetail applyOperation(
    ShoppingListDetail detail,
    ShoppingListOperation operation,
  ) {
    return switch (operation) {
      AddItemOperation(
        :final itemName,
        :final itemQuantity,
        :final itemUnit,
        :final index,
      ) =>
        () {
          final newItem = ShoppingListItem(
            id: operation.itemId,
            name: itemName,
            quantity: itemQuantity,
            unit: itemUnit,
            checked: false,
            position: 0.0,
            version: 0,
          );

          final updatedItems = [...detail.items];

          if (index != null && index >= 0 && index <= updatedItems.length) {
            updatedItems.insert(index, newItem);
          } else {
            updatedItems.add(newItem);
          }

          return ShoppingListDetail(
            id: detail.id,
            name: detail.name,
            items: updatedItems,
            role: detail.role,
          );
        }(),
      DeleteItemOperation() => () {
        final updatedItems = detail.items
            .where((i) => i.id != operation.itemId)
            .toList();
        return ShoppingListDetail(
          id: detail.id,
          name: detail.name,
          items: updatedItems,
          role: detail.role,
        );
      }(),
      MoveItemOperation(:final targetIndex) => () {
        final itemsCopy = detail.items.toList();
        final movedItem = itemsCopy.firstWhereOrNull(
          (i) => i.id == operation.itemId,
        );

        if (movedItem == null) {
          return detail;
        }

        final oldIndex = itemsCopy.indexOf(movedItem);

        itemsCopy.removeAt(oldIndex);
        itemsCopy.insert(targetIndex, movedItem);

        return ShoppingListDetail(
          id: detail.id,
          name: detail.name,
          items: itemsCopy,
          role: detail.role,
        );
      }(),
      CheckItemOperation() => () {
        final updatedItems = detail.items.map((item) {
          if (item.id == operation.itemId) {
            return ShoppingListItem(
              id: item.id,
              name: item.name,
              quantity: item.quantity,
              unit: item.unit,
              checked: true,
              position: item.position,
              version: item.version,
            );
          }
          return item;
        }).toList();

        return ShoppingListDetail(
          id: detail.id,
          name: detail.name,
          items: updatedItems,
          role: detail.role,
        );
      }(),
      UncheckItemOperation() => () {
        final updatedItems = detail.items.map((item) {
          if (item.id == operation.itemId) {
            return ShoppingListItem(
              id: item.id,
              name: item.name,
              quantity: item.quantity,
              unit: item.unit,
              checked: false,
              position: item.position,
              version: item.version,
            );
          }
          return item;
        }).toList();

        return ShoppingListDetail(
          id: detail.id,
          name: detail.name,
          items: updatedItems,
          role: detail.role,
        );
      }(),
      UpdateItemOperation(
        :final itemName,
        :final itemQuantity,
        :final itemUnit,
      ) =>
        () {
          final updatedItems = detail.items.map((item) {
            if (item.id == operation.itemId) {
              return ShoppingListItem(
                id: item.id,
                name: itemName,
                quantity: itemQuantity,
                unit: itemUnit,
                checked: item.checked,
                position: item.position,
                version: item.version,
              );
            }
            return item;
          }).toList();

          return ShoppingListDetail(
            id: detail.id,
            name: detail.name,
            items: updatedItems,
            role: detail.role,
          );
        }(),
    };
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

    // Get shoppingListId from current state
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
      await loadSharedUsers(shoppingListId); // Refresh list on success
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

    // Get shoppingListId from current state
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
      await loadSharedUsers(shoppingListId); // Refresh list on success
      await _shoppingListListService.loadShoppingLists();
    }

    _isUnshareShoppingListRunning = false;

    if (result is AsyncError) {
      throw result.error;
    }
  }

  void dispose() {
    _shoppingListDetail.dispose();
    _sharedUsers.dispose();
    stopSyncing();
  }
}
