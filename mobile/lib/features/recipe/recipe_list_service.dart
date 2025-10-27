import 'package:flutter/foundation.dart';

import '../../core/async_value.dart';
import 'recipe.dart';
import 'recipe_repository.dart';

class RecipeListService {
  final RecipeRepository _recipeRepository;

  RecipeListService({required RecipeRepository recipeRepository})
    : _recipeRepository = recipeRepository;

  final ValueNotifier<AsyncValue<List<Recipe>>> _recipes = ValueNotifier(
    const AsyncValue.loading(),
  );

  ValueListenable<AsyncValue<List<Recipe>>> get recipes => _recipes;

  bool _isLoadRecipesRunning = false;

  Future<void> loadRecipes() async {
    if (_isLoadRecipesRunning) return;
    _isLoadRecipesRunning = true;
    _recipes.value = const AsyncValue.loading();
    _recipes.value = await AsyncValue.guardAsync(() async {
      return _recipeRepository.fetchRecipes();
    });
    _isLoadRecipesRunning = false;
  }
}
