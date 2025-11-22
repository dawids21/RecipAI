import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:recipai_mobile/features/shopping_list/shopping_list_item.dart';

import '../auth/auth_service.dart';
import 'shopping_list_detail.dart';
import 'shopping_list_operation.dart';
import 'shopping_list_repository.dart';

class _SyncCallbacks {
  final Function(String, ShoppingListItem, List<ShoppingListOperation>)
  onItemAdded;
  final Function(String, ShoppingListItem, List<ShoppingListOperation>)
  onItemUpdated;
  final Function(ShoppingListDetail) onSync;
  final Function() onConflict;
  final Function(String) onError;

  _SyncCallbacks({
    required this.onItemAdded,
    required this.onItemUpdated,
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
    required Function(String, ShoppingListItem, List<ShoppingListOperation>)
    onItemAdded,
    required Function(String, ShoppingListItem, List<ShoppingListOperation>)
    onItemUpdated,
    required Function(ShoppingListDetail) onSync,
    required VoidCallback onConflict,
    required ValueChanged<String> onError,
  }) {
    // Cancel existing timer if any
    _syncTimers[listId]?.cancel();

    // Store callbacks
    _syncCallbacks[listId] = _SyncCallbacks(
      onItemAdded: onItemAdded,
      onItemUpdated: onItemUpdated,
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

  void pauseSyncing(String listId) {
    _syncTimers[listId]?.cancel();
  }

  void resumeSyncing(String listId) {
    // Only resume if callbacks exist and timer isn't already active
    if (_syncCallbacks[listId] != null &&
        (_syncTimers[listId]?.isActive != true)) {
      // Recreate timer
      _syncTimers[listId] = Timer.periodic(
        const Duration(seconds: 10),
        (_) => _syncList(listId),
      );
      // Immediately sync to catch up on changes
      _syncList(listId);
    }
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
      _operationQueues[listId]!.removeAt(0);

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
            _replaceValuesInQueue(listId, add.itemId, response);
            callbacks?.onItemAdded.call(
              add.itemId,
              response,
              _operationQueues[listId]!
                  .where((operation) => operation.itemId == response.id)
                  .toList(),
            );
          case DeleteItemOperation delete:
            await _repository.deleteItem(
              listId,
              delete.itemId,
              delete.itemVersion!,
              await _authService.idToken,
            );
          case MoveItemOperation move:
            final response = await _repository.moveItem(
              listId,
              move.itemId,
              move.itemVersion!,
              move.targetIndex,
              await _authService.idToken,
            );
            _replaceValuesInQueue(listId, move.itemId, response);
            callbacks?.onItemUpdated.call(
              move.itemId,
              response,
              _operationQueues[listId]!
                  .where((operation) => operation.itemId == response.id)
                  .toList(),
            );
          case CheckItemOperation check:
            final response = await _repository.checkItem(
              listId,
              check.itemId,
              check.itemVersion!,
              await _authService.idToken,
            );
            _replaceValuesInQueue(listId, check.itemId, response);
            callbacks?.onItemUpdated.call(
              check.itemId,
              response,
              _operationQueues[listId]!
                  .where((operation) => operation.itemId == response.id)
                  .toList(),
            );
          case UncheckItemOperation uncheck:
            final response = await _repository.uncheckItem(
              listId,
              uncheck.itemId,
              uncheck.itemVersion!,
              await _authService.idToken,
            );
            _replaceValuesInQueue(listId, uncheck.itemId, response);
            callbacks?.onItemUpdated.call(
              uncheck.itemId,
              response,
              _operationQueues[listId]!
                  .where((operation) => operation.itemId == response.id)
                  .toList(),
            );
          case UpdateItemOperation update:
            final response = await _repository.updateItem(
              listId,
              update.itemId,
              update.itemVersion!,
              update.itemName,
              update.itemQuantity,
              update.itemUnit,
              await _authService.idToken,
            );
            _replaceValuesInQueue(listId, update.itemId, response);
            callbacks?.onItemUpdated.call(
              update.itemId,
              response,
              _operationQueues[listId]!
                  .where((operation) => operation.itemId == response.id)
                  .toList(),
            );
        }
      } catch (e) {
        if (e.toString().contains('412')) {
          await callbacks?.onConflict.call();
        } else {
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

  void _replaceValuesInQueue(
    String listId,
    String itemId,
    ShoppingListItem item,
  ) {
    for (var i = 0; i < (_operationQueues[listId]?.length ?? 0); i++) {
      final operation = _operationQueues[listId]![i];
      if (operation.itemId == itemId) {
        if (operation is AddItemOperation) {
          _operationQueues[listId]![i] = AddItemOperation(
            id: operation.id,
            itemId: item.id,
            itemName: operation.itemName,
            itemQuantity: operation.itemQuantity,
            itemUnit: operation.itemUnit,
          );
        } else if (operation is DeleteItemOperation) {
          _operationQueues[listId]![i] = DeleteItemOperation(
            id: operation.id,
            itemId: item.id,
            itemVersion: item.version,
          );
        } else if (operation is MoveItemOperation) {
          _operationQueues[listId]![i] = MoveItemOperation(
            id: operation.id,
            itemId: item.id,
            itemVersion: item.version,
            targetIndex: operation.targetIndex,
          );
        } else if (operation is CheckItemOperation) {
          _operationQueues[listId]![i] = CheckItemOperation(
            id: operation.id,
            itemId: item.id,
            itemVersion: item.version,
          );
        } else if (operation is UncheckItemOperation) {
          _operationQueues[listId]![i] = UncheckItemOperation(
            id: operation.id,
            itemId: item.id,
            itemVersion: item.version,
          );
        } else if (operation is UpdateItemOperation) {
          _operationQueues[listId]![i] = UpdateItemOperation(
            id: operation.id,
            itemId: item.id,
            itemVersion: item.version,
            itemName: operation.itemName,
            itemQuantity: operation.itemQuantity,
            itemUnit: operation.itemUnit,
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
