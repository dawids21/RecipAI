import '../shopping_list/shopping_list_review_item.dart';

class ShoppingListGeneratedItems {
  final List<ShoppingListGeneratedItem> items;
  final List<String> inaccessibleRecipeNames;

  const ShoppingListGeneratedItems({
    required this.items,
    required this.inaccessibleRecipeNames,
  });

  factory ShoppingListGeneratedItems.fromJson(Map<String, dynamic> json) {
    return ShoppingListGeneratedItems(
      items: (json['items'] as List)
          .map(
            (e) =>
                ShoppingListGeneratedItem.fromJson(e as Map<String, dynamic>),
          )
          .toList(),
      inaccessibleRecipeNames: (json['inaccessibleRecipeNames'] as List)
          .map((e) => e as String)
          .toList(),
    );
  }
}
