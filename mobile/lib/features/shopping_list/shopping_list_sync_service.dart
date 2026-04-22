import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:recipai_mobile/features/shopping_list/shopping_list_item.dart';

import '../auth/auth_service.dart';
import 'shopping_list_exceptions.dart';
import 'shopping_list_operation.dart';
import 'shopping_list_repository.dart';

sealed class SyncEvent {}

final class ItemSynced extends SyncEvent {
  final String submittedItemId;
  final ShoppingListItem serverItem;
  ItemSynced(this.submittedItemId, this.serverItem);
}

final class SyncConflict extends SyncEvent {}

final class SyncFailed extends SyncEvent {
  final String message;
  SyncFailed(this.message);
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
  final Map<String, StreamController<SyncEvent>> _eventControllers = {};

  ValueListenable<bool> syncStatus(String listId) =>
      _syncStatusNotifiers.putIfAbsent(listId, () => ValueNotifier(false));

  Stream<SyncEvent> events(String listId) => _eventControllers
      .putIfAbsent(listId, () => StreamController<SyncEvent>.broadcast())
      .stream;

  List<ShoppingListOperation> pendingOperations(String listId) =>
      List.unmodifiable(_operationQueues[listId] ?? const []);

  void queueOperation(String listId, ShoppingListOperation operation) {
    _operationQueues.putIfAbsent(listId, () => []).add(operation);
    _processQueue(listId);
  }

  Future<void> _processQueue(String listId) async {
    if (_isSyncing[listId] == true) return;

    _isSyncing[listId] = true;
    _updateSyncStatus(listId);

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
              add.index,
            );
            _replaceValuesInQueue(listId, add.itemId, response);
            _operationQueues[listId]!.removeAt(0);
            _emit(listId, ItemSynced(add.itemId, response));
          case DeleteItemOperation delete:
            await _repository.deleteItem(
              listId,
              delete.itemId,
              delete.itemVersion!,
              await _authService.idToken,
            );
            _operationQueues[listId]!.removeAt(0);
          case MoveItemOperation move:
            final response = await _repository.moveItem(
              listId,
              move.itemId,
              move.itemVersion!,
              move.targetIndex,
              await _authService.idToken,
            );
            _replaceValuesInQueue(listId, move.itemId, response);
            _operationQueues[listId]!.removeAt(0);
            _emit(listId, ItemSynced(move.itemId, response));
          case CheckItemOperation check:
            final response = await _repository.checkItem(
              listId,
              check.itemId,
              check.itemVersion!,
              await _authService.idToken,
            );
            _replaceValuesInQueue(listId, check.itemId, response);
            _operationQueues[listId]!.removeAt(0);
            _emit(listId, ItemSynced(check.itemId, response));
          case UncheckItemOperation uncheck:
            final response = await _repository.uncheckItem(
              listId,
              uncheck.itemId,
              uncheck.itemVersion!,
              await _authService.idToken,
            );
            _replaceValuesInQueue(listId, uncheck.itemId, response);
            _operationQueues[listId]!.removeAt(0);
            _emit(listId, ItemSynced(uncheck.itemId, response));
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
            _operationQueues[listId]!.removeAt(0);
            _emit(listId, ItemSynced(update.itemId, response));
        }
      } on ShoppingListItemApiConflictException {
        final failedItemId = _operationQueues[listId]!.first.itemId;
        _operationQueues[listId]!.removeAt(0);
        _operationQueues[listId]!.removeWhere(
          (op) => op.itemId == failedItemId,
        );
        _emit(listId, SyncConflict());
      } on ShoppingListItemApiException catch (e) {
        _operationQueues[listId]!.removeAt(0);
        _emit(listId, SyncFailed('Failed to process operation: ${e.message}'));
      } catch (e) {
        // retry if operation failed due to connection error
        await Future.delayed(const Duration(seconds: 3));
      }
    }

    _isSyncing[listId] = false;
    _updateSyncStatus(listId);
  }

  void _emit(String listId, SyncEvent event) {
    final controller = _eventControllers.putIfAbsent(
      listId,
      () => StreamController<SyncEvent>.broadcast(),
    );
    controller.add(event);
  }

  void _updateSyncStatus(String listId) {
    final isProcessing = _operationQueues[listId]?.isNotEmpty ?? false;
    _syncStatusNotifiers.putIfAbsent(listId, () => ValueNotifier(false)).value =
        isProcessing;
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
            index: operation.index,
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

  void dispose() {
    for (final controller in _eventControllers.values) {
      controller.close();
    }
    _eventControllers.clear();
    for (final notifier in _syncStatusNotifiers.values) {
      notifier.dispose();
    }
    _syncStatusNotifiers.clear();
  }
}
