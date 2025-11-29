class FeatureFlags {
  static const bool recipesCollectionsEnabled = bool.fromEnvironment(
    'RECIPES_COLLECTIONS',
    defaultValue: false,
  );
}
