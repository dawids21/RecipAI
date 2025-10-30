import 'package:flutter/foundation.dart';

import '../../core/async_value.dart';
import '../auth/auth_service.dart';
import 'recipe.dart';
import 'recipe_detail.dart';
import 'recipe_repository.dart';

class RecipeListService {
  final RecipeRepository _recipeRepository;
  final AuthService _authService;

  RecipeListService({
    required RecipeRepository recipeRepository,
    required AuthService authService,
  }) : _recipeRepository = recipeRepository,
       _authService = authService;

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
      final token = await _authService.idToken;
      return _recipeRepository.fetchRecipes(token);
    });
    _isLoadRecipesRunning = false;
  }

  Future<void> createRecipe(RecipeDetail recipe) async {
    if (_isCreateRecipeRunning) return;
    _isCreateRecipeRunning = true;

    final result = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      return _recipeRepository.createRecipe(recipe, token);
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
