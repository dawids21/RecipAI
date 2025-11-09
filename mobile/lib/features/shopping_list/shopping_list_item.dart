class ShoppingListItem {
  final String id;
  final String name;
  final double? quantity;
  final String? unit;
  final bool checked;
  final int position;

  const ShoppingListItem({
    required this.id,
    required this.name,
    required this.quantity,
    required this.unit,
    required this.checked,
    required this.position,
  });

  factory ShoppingListItem.fromJson(Map<String, dynamic> json) {
    return ShoppingListItem(
      id: json['id'] as String,
      name: json['name'] as String,
      quantity: json['quantity'] as double?,
      unit: json['unit'] as String?,
      checked: json['checked'] as bool,
      position: json['position'] as int,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'name': name,
      'quantity': quantity,
      'unit': unit,
      'checked': checked,
      'position': position,
    };
  }
}
