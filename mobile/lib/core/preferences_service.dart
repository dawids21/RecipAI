import 'package:shared_preferences/shared_preferences.dart';

class PreferencesService {
  final SharedPreferences _prefs;

  static const String _recipeFilterKey = 'recipe_filter_collection_id';

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
}
