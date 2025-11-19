import '../shopping_list/shopping_list_item_parser.dart';
import '../shopping_list/shopping_list_operation.dart';
import '../shopping_list/shopping_list_sync_service.dart';
import 'recipe_detail.dart';

class RecipeToShoppingListService {
  final ShoppingListSyncService _syncService;

  RecipeToShoppingListService({required ShoppingListSyncService syncService})
    : _syncService = syncService;

  void addIngredientsToList(String listId, List<Ingredient> ingredients) {
    for (final ingredient in ingredients) {
      final parsed = _parseIngredient(ingredient);

      final operation = AddItemOperation(
        itemName: parsed.name,
        itemQuantity: parsed.quantity,
        itemUnit: parsed.unit,
      );

      _syncService.queueOperation(listId, operation);
    }
  }

  ParsedShoppingListItem _parseIngredient(Ingredient ingredient) {
    // Build full text string: "<quantity> <unit> <name>"
    final parts = <String>[];
    if (ingredient.quantity.isNotEmpty) {
      parts.add(ingredient.quantity);
    }
    if (ingredient.unit != null && ingredient.unit!.isNotEmpty) {
      parts.add(ingredient.unit!);
    }
    parts.add(ingredient.name);
    final fullText = parts.join(' ');

    return ShoppingListItemParser.parse(fullText);
  }
}
