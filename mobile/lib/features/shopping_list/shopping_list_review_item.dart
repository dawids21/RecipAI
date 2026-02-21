import '../recipe/recipe_detail.dart';

class ShoppingListGeneratedItem {
  final String name;
  final double? quantity;
  final String? unit;

  const ShoppingListGeneratedItem({
    required this.name,
    this.quantity,
    this.unit,
  });

  factory ShoppingListGeneratedItem.fromJson(Map<String, dynamic> json) {
    return ShoppingListGeneratedItem(
      name: json['name'] as String,
      quantity: (json['quantity'] as num?)?.toDouble(),
      unit: json['unit'] as String?,
    );
  }

  factory ShoppingListGeneratedItem.fromIngredient(Ingredient ingredient) {
    return ShoppingListGeneratedItem(
      name: ingredient.name,
      quantity: double.tryParse(ingredient.quantity),
      unit: ingredient.unit,
    );
  }

  String get displaySubtitle {
    if (quantity == null && unit == null) return '';
    final quantityStr = quantity != null
        ? (quantity! % 1 == 0
              ? quantity!.toInt().toString()
              : quantity.toString())
        : '';
    final parts = <String>[
      if (quantityStr.isNotEmpty) quantityStr,
      if (unit != null && unit!.isNotEmpty) unit!,
    ];
    return parts.join(' ');
  }
}
