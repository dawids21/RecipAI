import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

class PreferencesService {
  final SharedPreferences _prefs;

  static const String _recipeFilterKey = 'recipe_filter_collection_id';
  static const String _mealPlanVisibilityKey = 'meal_plan_visibility';

  PreferencesService(this._prefs);

  String? getRecipeFilterCollectionId() {
    return _prefs.getString(_recipeFilterKey);
  }

  Future<void> setRecipeFilterCollectionId(String? collectionId) async {
    if (collectionId == null) {
      await _prefs.remove(_recipeFilterKey);
    } else {
      await _prefs.setString(_recipeFilterKey, collectionId);
    }
  }

  Future<void> clearRecipeFilter() async {
    await _prefs.remove(_recipeFilterKey);
  }

  Map<String, bool> getMealPlanVisibility() {
    final jsonString = _prefs.getString(_mealPlanVisibilityKey);
    if (jsonString == null) return {};

    try {
      final decoded = jsonDecode(jsonString);
      return Map<String, bool>.from(decoded);
    } catch (e) {
      return {};
    }
  }

  Future<void> setMealPlanVisibility(Map<String, bool> visibility) async {
    await _prefs.setString(_mealPlanVisibilityKey, jsonEncode(visibility));
  }
}
