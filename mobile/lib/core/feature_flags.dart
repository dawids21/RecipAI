class FeatureFlags {
  static const bool recipeCollectionsEnabled = bool.fromEnvironment(
    'RECIPE_COLLECTIONS',
    defaultValue: false,
  );
}
