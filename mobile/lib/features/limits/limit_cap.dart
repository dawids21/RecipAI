enum LimitKind { stock, flow }

class LimitResources {
  static const String recipe = 'RECIPE';
  static const String recipesCollection = 'RECIPES_COLLECTION';
  static const String shoppingList = 'SHOPPING_LIST';
  static const String shoppingListItem = 'SHOPPING_LIST_ITEM';
  static const String mealPlan = 'MEAL_PLAN';
  static const String extraction = 'EXTRACTION';

  /// The resources whose cap is the caller's own, and so holds anywhere in the
  /// app. `LimitsService` gives each one a notifier up front; anything absent
  /// here it never surfaces, even if `GET /limits` reports a cap for it.
  ///
  /// [shoppingListItem] is deliberately absent: an item cap is configured
  /// against the *list's owner*, so the caller's own value is the wrong number
  /// for a shared list. That cap is read per list, as
  /// `ShoppingListDetailService.itemCap`.
  static const List<String> perUser = [
    recipe,
    recipesCollection,
    shoppingList,
    mealPlan,
    extraction,
  ];
}

class LimitCap {
  final String resource;
  final LimitKind kind;
  final int limit;

  const LimitCap({
    required this.resource,
    required this.kind,
    required this.limit,
  });

  factory LimitCap.fromJson(Map<String, dynamic> json) {
    return LimitCap(
      resource: json['resource'] as String,
      kind: (json['kind'] as String) == 'FLOW'
          ? LimitKind.flow
          : LimitKind.stock,
      limit: json['limit'] as int,
    );
  }
}
