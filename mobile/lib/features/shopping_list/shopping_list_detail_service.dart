import 'dart:async';

import 'package:flutter/foundation.dart';

import '../../core/async_value.dart';
import '../auth/auth_service.dart';
import 'shopping_list_detail.dart';
import 'shopping_list_item.dart';
import 'shopping_list_list_service.dart';
import 'shopping_list_operation.dart';
import 'shopping_list_repository.dart';
import 'shopping_list_sync_service.dart';

class ShoppingListDetailService {
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

  bool _isLoadShoppingListDetailRunning = false;
  bool _isRenameRunning = false;
  bool _isDeleteRunning = false;

  Future<void> loadShoppingListDetail(String id) async {
    if (_isLoadShoppingListDetailRunning) return;
    _isLoadShoppingListDetailRunning = true;

    _shoppingListDetail.value = const AsyncValue.loading();
    _shoppingListDetail.value = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      return _shoppingListRepository.fetchShoppingListDetail(id, token);
    });

    _isLoadShoppingListDetailRunning = false;
  }

  Future<void> renameShoppingList(String id, String newName) async {
    if (_isRenameRunning) return;
    _isRenameRunning = true;

    try {
      final token = await _authService.idToken;
      await _shoppingListRepository.updateShoppingList(id, newName, token);
      await loadShoppingListDetail(id);
      await _shoppingListListService.loadShoppingLists();
    } finally {
      _isRenameRunning = false;
    }
  }

  Future<void> deleteShoppingList(String id) async {
    if (_isDeleteRunning) return;
    _isDeleteRunning = true;

    try {
      final token = await _authService.idToken;
      await _shoppingListRepository.deleteShoppingList(id, token);
      await _shoppingListListService.loadShoppingLists();
    } finally {
      _isDeleteRunning = false;
    }
  }

  String? _currentSyncingListId;

  void startSyncing({
    required String listId,
    VoidCallback? onConflict,
    ValueChanged<String>? onError,
  }) {
    _currentSyncingListId = listId;
    _syncService.startSyncing(
      listId: listId,
      onItemAdded: _onItemAdded,
      onItemUpdated: _onItemUpdated,
      onSync: (detail) {
        _shoppingListDetail.value = AsyncData(detail);
      },
      onConflict: () async {
        await loadShoppingListDetail(listId);
        onConflict?.call();
      },
      onError: (message) {
        onError?.call(message);
      },
    );
  }

  void stopSyncing() {
    if (_currentSyncingListId != null) {
      _syncService.stopSyncing(_currentSyncingListId!);
      _currentSyncingListId = null;
    }
  }

  ValueNotifier<bool> getSyncStatusNotifier(String listId) {
    return _syncService.getSyncStatusNotifier(listId);
  }

  void processOperation(ShoppingListOperation operation) async {
    final currentState = _shoppingListDetail.value;
    if (currentState is! AsyncData<ShoppingListDetail>) return;

    final detail = currentState.value;
    final updatedDetail = applyOperation(detail, operation);

    _shoppingListDetail.value = AsyncData(updatedDetail);

    _syncService.queueOperation(detail.id, operation);
  }

  static ShoppingListDetail applyOperation(
    ShoppingListDetail detail,
    ShoppingListOperation operation,
  ) {
    return switch (operation) {
      AddItemOperation(:final itemName, :final itemQuantity, :final itemUnit) =>
        () {
          final maxPosition = detail.items.isEmpty
              ? 0.0
              : detail.items
                    .map((i) => i.position)
                    .reduce((a, b) => a > b ? a : b);

          final newItem = ShoppingListItem(
            id: operation.itemId,
            name: itemName,
            quantity: itemQuantity,
            unit: itemUnit,
            checked: false,
            position: maxPosition + 1.0,
            version: 0,
          );

          final updatedItems = [...detail.items, newItem];
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
        final movedItem = itemsCopy.firstWhere((i) => i.id == operation.itemId);
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
    };
  }

  void _onItemAdded(
    String tempId,
    ShoppingListItem addedItem,
    List<ShoppingListOperation> pendingOperations,
  ) {
    final currentState = _shoppingListDetail.value;
    if (currentState is! AsyncData<ShoppingListDetail>) return;

    final detail = currentState.value;
    final updatedItems = detail.items.map((item) {
      if (item.id == tempId) {
        return addedItem;
      }
      return item;
    }).toList();

    var updatedDetail = ShoppingListDetail(
      id: detail.id,
      name: detail.name,
      items: updatedItems,
      role: detail.role,
    );

    for (final operation in pendingOperations) {
      updatedDetail = applyOperation(updatedDetail, operation);
    }

    _shoppingListDetail.value = AsyncData(updatedDetail);
  }

  void _onItemUpdated(
    String itemId,
    ShoppingListItem updatedItem,
    List<ShoppingListOperation> pendingOperations,
  ) {
    final currentState = _shoppingListDetail.value;
    if (currentState is! AsyncData<ShoppingListDetail>) return;

    final detail = currentState.value;
    final itemsCopy = detail.items.toList();
    final index = itemsCopy.indexWhere((i) => i.id == itemId);

    if (index != -1) {
      itemsCopy[index] = updatedItem;
    }

    itemsCopy.sort((a, b) => a.position.compareTo(b.position));

    var updatedDetail = ShoppingListDetail(
      id: detail.id,
      name: detail.name,
      items: itemsCopy,
      role: detail.role,
    );

    for (final operation in pendingOperations) {
      updatedDetail = applyOperation(updatedDetail, operation);
    }

    _shoppingListDetail.value = AsyncData(updatedDetail);
  }

  void dispose() {
    stopSyncing();
  }
}
