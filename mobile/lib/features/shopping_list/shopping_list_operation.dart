import 'package:uuid/uuid.dart';

sealed class ShoppingListOperation {
  final String id;
  final String itemId;

  ShoppingListOperation({String? id, String? itemId})
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
  final int itemVersion;

  DeleteItemOperation({
    super.id,
    required String itemId,
    required this.itemVersion,
  }) : super(itemId: itemId);
}
