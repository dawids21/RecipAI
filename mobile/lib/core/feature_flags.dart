class FeatureFlags {
  /// Controls visibility of meal planning feature in the app
  /// Environment variable: MEAL_PLANNING
  /// Default: false
  static const bool mealPlanningEnabled = bool.fromEnvironment(
    'MEAL_PLANNING',
    defaultValue: false,
  );
}
