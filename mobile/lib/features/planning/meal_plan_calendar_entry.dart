import 'dart:ui';

class MealPlanCalendarEntry {
  final int id;
  final String planId;
  final Color planColor; // Hex color string (e.g., "#FF5733")
  final String date; // ISO 8601 date format
  final String? recipeId;
  final String? recipeName;
  final String? placeholderText;
  final int? servingSize;
  final bool hasRecipeAccess;

  const MealPlanCalendarEntry({
    required this.id,
    required this.planId,
    required this.planColor,
    required this.date,
    this.recipeId,
    this.recipeName,
    this.placeholderText,
    this.servingSize,
    required this.hasRecipeAccess,
  });

  factory MealPlanCalendarEntry.fromJson(Map<String, dynamic> json) {
    final color = Color(
      int.parse((json['planColor'] as String).replaceFirst('#', '0xFF')),
    );
    return MealPlanCalendarEntry(
      id: json['id'] as int,
      planId: json['planId'] as String,
      planColor: color,
      date: json['date'] as String,
      recipeId: json['recipeId'] as String?,
      recipeName: json['recipeName'] as String?,
      placeholderText: json['placeholderText'] as String?,
      servingSize: json['servingSize'] as int?,
      hasRecipeAccess: json['hasRecipeAccess'] as bool,
    );
  }

  String get displayText => recipeName ?? placeholderText ?? '';

  bool get isRecipeEntry => recipeId != null;

  bool get isPlaceholder => recipeId == null;
}
