import 'package:flutter/foundation.dart';

import '../../core/async_value.dart';
import 'recipe.dart';
import 'recipe_detail.dart';
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
  bool _isCreateRecipeRunning = false;

  Future<void> loadRecipes() async {
    if (_isLoadRecipesRunning) return;
    _isLoadRecipesRunning = true;
    _recipes.value = const AsyncValue.loading();
    _recipes.value = await AsyncValue.guardAsync(() async {
      return _recipeRepository.fetchRecipes();
    });
    _isLoadRecipesRunning = false;
  }

  Future<void> createRecipe(RecipeDetail recipe) async {
    if (_isCreateRecipeRunning) return;
    _isCreateRecipeRunning = true;

    final result = await AsyncValue.guardAsync(() async {
      return _recipeRepository.createRecipe(recipe);
    });

    if (result is AsyncData) {
      await loadRecipes();
    }

    _isCreateRecipeRunning = false;

    if (result is AsyncError<RecipeDetail>) {
      throw result.error;
    }
  }
}
