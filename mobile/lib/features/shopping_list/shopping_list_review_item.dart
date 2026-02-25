import '../recipe/recipe_detail.dart';

class ShoppingListGeneratedItem {
  final String name;
  final double? quantity;
  final String? unit;
  final String? source;

  const ShoppingListGeneratedItem({
    required this.name,
    this.quantity,
    this.unit,
    this.source,
  });

  factory ShoppingListGeneratedItem.fromJson(Map<String, dynamic> json) {
    return ShoppingListGeneratedItem(
      name: json['name'] as String,
      quantity: (json['quantity'] as num?)?.toDouble(),
      unit: json['unit'] as String?,
      source: json['source'] as String?,
    );
  }

  factory ShoppingListGeneratedItem.fromIngredient(
    Ingredient ingredient, {
    String? source,
  }) {
    return ShoppingListGeneratedItem(
      name: ingredient.name,
      quantity: double.tryParse(ingredient.quantity),
      unit: ingredient.unit,
      source: source,
    );
  }

  String get displayTitle {
    final quantityStr = quantity != null
        ? (quantity! % 1 == 0
              ? quantity!.toInt().toString()
              : quantity.toString())
        : '';
    final parts = <String>[
      if (quantityStr.isNotEmpty) quantityStr,
      if (unit != null && unit!.isNotEmpty) unit!,
    ];
    if (parts.isEmpty) return name;
    return '$name - ${parts.join(' ')}';
  }

  String get displaySubtitle => source ?? '';
}
