class MealEntryFormResult {
  final String planId;
  final DateTime date;
  final String? recipeId;
  final String? recipeName;
  final int? servingSize;
  final String? placeholderText;

  const MealEntryFormResult({
    required this.planId,
    required this.date,
    this.recipeId,
    this.recipeName,
    this.servingSize,
    this.placeholderText,
  });
}
