import '../../shared/user_role.dart';
import 'shopping_list_item.dart';

class ShoppingListDetail {
  final String id;
  final String name;
  final List<ShoppingListItem> items;
  final UserRole role;

  const ShoppingListDetail({
    required this.id,
    required this.name,
    required this.items,
    required this.role,
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
      role: UserRole.fromApiString(json['role'] as String),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'name': name,
      'items': items.map((item) => item.toJson()).toList(),
      'role': role.toApiString(),
    };
  }
}
