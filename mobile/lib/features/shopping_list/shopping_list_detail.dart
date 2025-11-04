import 'shopping_list_item.dart';

class ShoppingListDetail {
  final String id;
  final String name;
  final List<ShoppingListItem> items;

  const ShoppingListDetail({
    required this.id,
    required this.name,
    required this.items,
  });

  factory ShoppingListDetail.fromJson(Map<String, dynamic> json) {
    final List<dynamic> itemsJson = json['items'] as List<dynamic>;
    final List<ShoppingListItem> items = itemsJson
        .map(
          (itemJson) =>
              ShoppingListItem.fromJson(itemJson as Map<String, dynamic>),
        )
        .toList();

    return ShoppingListDetail(
      id: json['id'] as String,
      name: json['name'] as String,
      items: items,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'name': name,
      'items': items.map((item) => item.toJson()).toList(),
    };
  }
}
