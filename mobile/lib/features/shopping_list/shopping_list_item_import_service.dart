import 'package:logging/logging.dart';

import 'shopping_list_item_store_service.dart';
import 'shopping_list_review_item.dart';
import 'shopping_list_sync_service.dart';

/// Writes reviewed generated items into a chosen shopping list, whether or
/// not that list is currently open, then kicks a sync drain.
class ShoppingListItemImportService {
  static final _log = Logger('recipai.shopping_list.import');

  final ShoppingListItemStoreService store;
  final ShoppingListSyncService syncService;

  ShoppingListItemImportService({required this.store, required this.syncService});

  Future<void> importItems(
    String listId,
    List<ShoppingListGeneratedItem> items,
  ) async {
    _log.info('importItems listId=$listId count=${items.length}');
    for (final item in items) {
      await store.applyCreate(
        listId,
        name: item.name,
        quantity: item.quantity,
        unit: item.unit,
      );
    }
    syncService.requestDrain(listId);
  }
}
