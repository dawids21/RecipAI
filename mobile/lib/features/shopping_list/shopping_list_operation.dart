import 'package:uuid/uuid.dart';

sealed class ShoppingListOperation {
  final String id;
  final String itemId;
  final int? itemVersion;

  ShoppingListOperation({String? id, String? itemId, this.itemVersion})
    : id = id ?? const Uuid().v4(),
      itemId = itemId ?? const Uuid().v4();
}

class AddItemOperation extends ShoppingListOperation {
  final String itemName;
  final double? itemQuantity;
  final String? itemUnit;

  AddItemOperation({
    super.id,
    super.itemId,
    required this.itemName,
    required this.itemQuantity,
    required this.itemUnit,
  });
}

class DeleteItemOperation extends ShoppingListOperation {
  DeleteItemOperation({
    super.id,
    required String itemId,
    required int itemVersion,
  }) : super(itemId: itemId, itemVersion: itemVersion);
}

class MoveItemOperation extends ShoppingListOperation {
  final int targetIndex;

  MoveItemOperation({
    super.id,
    required String itemId,
    required int itemVersion,
    required this.targetIndex,
  }) : super(itemId: itemId, itemVersion: itemVersion);
}

class CheckItemOperation extends ShoppingListOperation {
  CheckItemOperation({
    super.id,
    required String itemId,
    required int itemVersion,
  }) : super(itemId: itemId, itemVersion: itemVersion);
}

class UncheckItemOperation extends ShoppingListOperation {
  UncheckItemOperation({
    super.id,
    required String itemId,
    required int itemVersion,
  }) : super(itemId: itemId, itemVersion: itemVersion);
}
