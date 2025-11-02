class FeatureFlags {
  static const bool shoppingListsEnabled = bool.fromEnvironment(
    'SHOPPING_LISTS',
    defaultValue: false,
  );
}
