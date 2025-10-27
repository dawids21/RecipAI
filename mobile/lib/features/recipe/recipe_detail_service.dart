import 'package:flutter/foundation.dart';

import '../../core/async_value.dart';
import 'recipe_detail.dart';
import 'recipe_list_service.dart';
import 'recipe_repository.dart';

class RecipeDetailService {
  final RecipeRepository _recipeRepository;
  final RecipeListService _recipeListService;

  RecipeDetailService({
    required RecipeRepository recipeRepository,
    required RecipeListService recipeListService,
  }) : _recipeRepository = recipeRepository,
       _recipeListService = recipeListService;

  final ValueNotifier<AsyncValue<RecipeDetail>> _recipeDetail = ValueNotifier(
    const AsyncValue.loading(),
  );

  ValueListenable<AsyncValue<RecipeDetail>> get recipeDetail => _recipeDetail;

  bool _isLoadRecipeDetailRunning = false;
  bool _isDeleteRecipeRunning = false;

  Future<void> loadRecipeDetail(String id) async {
    if (_isLoadRecipeDetailRunning) return;
    _isLoadRecipeDetailRunning = true;
    _recipeDetail.value = const AsyncValue.loading();
    _recipeDetail.value = await AsyncValue.guardAsync(() async {
      return _recipeRepository.fetchRecipeDetail(id);
    });
    _isLoadRecipeDetailRunning = false;
  }

  Future<void> deleteRecipe(String id) async {
    if (_isDeleteRecipeRunning) return;
    _isDeleteRecipeRunning = true;

    final result = await AsyncValue.guardAsync(() async {
      return _recipeRepository.deleteRecipe(id);
    });

    if (result is AsyncData) {
      await _recipeListService.loadRecipes();
    }

    _isDeleteRecipeRunning = false;

    if (result is AsyncError) {
      throw result.error;
    }
  }
}
