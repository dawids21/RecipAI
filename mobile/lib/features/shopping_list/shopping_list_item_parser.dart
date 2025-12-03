/// Result of parsing an ingredient string into its components.
///
/// An ingredient can be represented as "quantity unit name" (e.g., "2 cups flour")
/// or just "name" if no quantity/unit is present.
typedef ParsedShoppingListItem = ({
  String name,
  double? quantity,
  String? unit,
});

class ShoppingListItemParser {
  static final _pattern = RegExp(
    r'^\s*(\d+[.,]?\d*)\s*([\p{L}]+)?\s+(.+)$',
    unicode: true,
  );

  /// Parses a text string into ingredient components.
  ///
  /// Attempts to extract quantity, unit, and name from the input text.
  /// The expected format is "[quantity] [unit] name" where quantity and unit are optional.
  ///
  /// If parsing succeeds, returns the extracted components separately.
  /// If parsing fails, returns the entire text as name with null quantity and unit.
  ///
  /// Examples:
  ///   "2 cups flour" -> (name: "flour", quantity: 2.0, unit: "cups")
  ///   "1.5 tbsp salt" -> (name: "salt", quantity: 1.5, unit: "tbsp")
  ///   "3 eggs" -> (name: "eggs", quantity: 3.0, unit: null)
  ///   "salt" -> (name: "salt", quantity: null, unit: null)
  static ParsedShoppingListItem parse(String text) {
    final trimmed = text.trim();
    if (trimmed.isEmpty) {
      return (name: '', quantity: null, unit: null);
    }

    final match = _pattern.firstMatch(trimmed);
    if (match != null) {
      final quantityStr = match.group(1)!.replaceAll(',', '.');
      final quantity = double.tryParse(quantityStr);
      final unit = match.group(2)?.trim();
      final name = match.group(3)!.trim();

      if (quantity != null) {
        return (name: name, quantity: quantity, unit: unit);
      }
    }

    // Parsing failed - use full text as name so user can edit later
    return (name: trimmed, quantity: null, unit: null);
  }
}
