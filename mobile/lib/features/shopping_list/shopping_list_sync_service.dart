import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:recipai_mobile/features/shopping_list/shopping_list_item.dart';

import '../auth/auth_service.dart';
import 'shopping_list_detail.dart';
import 'shopping_list_operation.dart';
import 'shopping_list_repository.dart';

class _SyncCallbacks {
  final Function(String, ShoppingListItem) onItemAdded;
  final Function(ShoppingListDetail) onSync;
  final Function() onConflict;
  final Function(String) onError;

  _SyncCallbacks({
    required this.onItemAdded,
    required this.onSync,
    required this.onConflict,
    required this.onError,
  });
}

class ShoppingListSyncService {
  final ShoppingListRepository _repository;
  final AuthService _authService;

  ShoppingListSyncService({
    required ShoppingListRepository repository,
    required AuthService authService,
  }) : _repository = repository,
       _authService = authService;

  final Map<String, List<ShoppingListOperation>> _operationQueues = {};
  final Map<String, bool> _isSyncing = {};
  final Map<String, ValueNotifier<bool>> _syncStatusNotifiers = {};
  final Map<String, Timer> _syncTimers = {};
  final Map<String, _SyncCallbacks> _syncCallbacks = {};

  ValueNotifier<bool> getSyncStatusNotifier(String listId) {
    return _syncStatusNotifiers.putIfAbsent(listId, () => ValueNotifier(false));
  }

  void startSyncing({
    required String listId,
    required Function(String, ShoppingListItem) onItemAdded,
    required Function(ShoppingListDetail) onSync,
    required VoidCallback onConflict,
    required ValueChanged<String> onError,
  }) {
    // Cancel existing timer if any
    _syncTimers[listId]?.cancel();

    // Store callbacks
    _syncCallbacks[listId] = _SyncCallbacks(
      onItemAdded: onItemAdded,
      onSync: onSync,
      onConflict: onConflict,
      onError: onError,
    );

    // Create new timer
    _syncTimers[listId] = Timer.periodic(
      const Duration(seconds: 10),
      (_) => _syncList(listId),
    );
  }

  void stopSyncing(String listId) {
    _syncTimers[listId]?.cancel();
    _syncTimers.remove(listId);
    _syncCallbacks.remove(listId);
  }

  void queueOperation(String listId, ShoppingListOperation operation) {
    _operationQueues.putIfAbsent(listId, () => []).add(operation);
    _processQueue(listId);
  }

  Future<void> _processQueue(String listId) async {
    if (_isSyncing[listId] == true) return;

    _isSyncing[listId] = true;
    _updateSyncStatus(listId);
    final callbacks = _syncCallbacks[listId];

    while (_operationQueues[listId]?.isNotEmpty ?? false) {
      final operation = _operationQueues[listId]!.first;

      try {
        switch (operation) {
          case AddItemOperation add:
            final response = await _repository.createItem(
              listId,
              add.itemName,
              add.itemQuantity,
              add.itemUnit,
              await _authService.idToken,
            );
            callbacks?.onItemAdded.call(add.itemId, response);
            _replaceItemIdInQueue(listId, add.itemId, response.id);
          case DeleteItemOperation delete:
            await _repository.deleteItem(
              listId,
              delete.itemId,
              delete.itemVersion,
              await _authService.idToken,
            );
        }

        _operationQueues[listId]!.removeAt(0);
      } catch (e) {
        if (e.toString().contains('412')) {
          _operationQueues[listId]!.removeAt(0);
          await callbacks?.onConflict.call();
        } else {
          _operationQueues[listId]!.removeAt(0);
          callbacks?.onError.call('Failed to sync: $e');
        }
      }
    }

    _isSyncing[listId] = false;
    _updateSyncStatus(listId);
  }

  void _updateSyncStatus(String listId) {
    final isProcessing = _operationQueues[listId]?.isNotEmpty ?? false;
    getSyncStatusNotifier(listId).value = isProcessing;
  }

  void _replaceItemIdInQueue(String listId, String oldId, String newId) {
    for (var i = 0; i < (_operationQueues[listId]?.length ?? 0); i++) {
      final operation = _operationQueues[listId]![i];
      if (operation.itemId == oldId) {
        if (operation is AddItemOperation) {
          _operationQueues[listId]![i] = AddItemOperation(
            id: operation.id,
            itemId: newId,
            itemName: operation.itemName,
            itemQuantity: operation.itemQuantity,
            itemUnit: operation.itemUnit,
          );
        } else if (operation is DeleteItemOperation) {
          _operationQueues[listId]![i] = DeleteItemOperation(
            id: operation.id,
            itemId: newId,
            itemVersion: operation.itemVersion,
          );
        }
      }
    }
  }

  Future<void> _syncList(String listId) async {
    // Skip if currently syncing
    if (_isSyncing[listId] == true) return;

    final callbacks = _syncCallbacks[listId];
    if (callbacks == null) return;

    try {
      final token = await _authService.idToken;
      final detail = await _repository.fetchShoppingListDetail(listId, token);
      callbacks.onSync(detail);
    } catch (e) {
      callbacks.onError('Failed to sync list: $e');
    }
  }
}
