class FeatureFlags {
  static const bool devAuthEnabled = bool.fromEnvironment(
    'DEV_AUTH_ENABLED',
    defaultValue: false,
  );
}
