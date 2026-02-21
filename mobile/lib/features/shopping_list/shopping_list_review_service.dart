import 'shopping_list_operation.dart';
import 'shopping_list_review_item.dart';
import 'shopping_list_sync_service.dart';

class ShoppingListReviewService {
  final ShoppingListSyncService _syncService;

  ShoppingListReviewService({required ShoppingListSyncService syncService})
    : _syncService = syncService;

  void addItemsToShoppingList(
    String listId,
    List<ShoppingListGeneratedItem> items,
  ) {
    for (final item in items) {
      _syncService.queueOperation(
        listId,
        AddItemOperation(
          itemName: item.name,
          itemQuantity: item.quantity,
          itemUnit: item.unit,
        ),
      );
    }
  }
}
