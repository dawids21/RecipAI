class FeatureFlags {
  static const bool loggingEnabled = bool.fromEnvironment(
    'LOGGING_ENABLED',
    defaultValue: true,
  );
}
