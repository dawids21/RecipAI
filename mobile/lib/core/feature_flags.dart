class FeatureFlags {
  static const bool newFeatureEnabled = bool.fromEnvironment(
    'NEW_FEATURE',
    defaultValue: false,
  );
}
