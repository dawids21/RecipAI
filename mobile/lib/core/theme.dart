import 'package:flutter/material.dart';

/// Main theme configuration for the RecipAI app
class AppTheme {
  static ThemeData get theme => ThemeData(
    colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepOrange),
  );
}

/// Standardized spacing constants following Material Design 3 8dp grid system
class AppSpacing {
  // EdgeInsets constants
  static const EdgeInsets screenPadding = EdgeInsets.all(16.0);
  static const EdgeInsets cardMargin = EdgeInsets.symmetric(
    horizontal: 16.0,
    vertical: 4.0,
  );
  static const EdgeInsets listTilePadding = EdgeInsets.symmetric(
    horizontal: 16.0,
    vertical: 8.0,
  );
  static const EdgeInsets smallVertical = EdgeInsets.symmetric(vertical: 4.0);
  static const EdgeInsets mediumVertical = EdgeInsets.symmetric(vertical: 8.0);

  // SizedBox spacing values
  static const double small = 8.0;
  static const double medium = 16.0;
  static const double large = 24.0;
  static const double extraSmall = 4.0;
  static const double extraLarge = 32.0;
}

/// Animation constants for UI transitions
class AppAnimations {
  static const Duration sectionTransition = Duration(milliseconds: 300);
  static const Curve sectionCurve = Curves.easeInOut;
}
