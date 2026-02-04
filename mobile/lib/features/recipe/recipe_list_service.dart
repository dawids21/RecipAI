import 'package:flutter/foundation.dart';
import 'package:fuzzy/fuzzy.dart';
import 'package:recipai_mobile/features/recipe/recipe_image_input.dart';

import '../../core/async_value.dart';
import '../../core/preferences_service.dart';
import '../auth/auth_service.dart';
import 'recipe.dart';
import 'recipe_detail.dart';
import 'recipe_repository.dart';

class RecipeListService {
  final RecipeRepository _recipeRepository;
  final AuthService _authService;
  final PreferencesService _preferencesService;

  RecipeListService({
    required RecipeRepository recipeRepository,
    required AuthService authService,
    required PreferencesService preferencesService,
  }) : _recipeRepository = recipeRepository,
       _authService = authService,
       _preferencesService = preferencesService {
    _loadSavedFilter();
  }

  static const String unassignedFilterId = '__UNASSIGNED__';

  final ValueNotifier<AsyncValue<List<Recipe>>> _recipes = ValueNotifier(
    const AsyncValue.loading(),
  );

  final ValueNotifier<String?> _selectedCollectionId = ValueNotifier(null);

  ValueListenable<String?> get selectedCollectionId => _selectedCollectionId;

  ValueListenable<AsyncValue<List<Recipe>>> get recipes => _recipes;

  bool _isLoadRecipesRunning = false;
  bool _isCreateRecipeRunning = false;

  void _loadSavedFilter() {
    final savedFilter = _preferencesService.getRecipeFilterCollectionId();
    if (savedFilter != null) {
      _selectedCollectionId.value = savedFilter;
    }
  }

  Future<void> setFilter(String? collectionId) async {
    _selectedCollectionId.value = collectionId;
    _preferencesService.setRecipeFilterCollectionId(collectionId);
    await loadRecipes();
  }

  Future<void> loadRecipes() async {
    if (_isLoadRecipesRunning) return;
    _isLoadRecipesRunning = true;
    _recipes.value = const AsyncValue.loading();
    _recipes.value = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      final filterValue = _selectedCollectionId.value;

      if (filterValue == null) {
        return _recipeRepository.fetchRecipes(token);
      } else if (filterValue == unassignedFilterId) {
        return _recipeRepository.fetchUnassignedRecipes(token);
      } else {
        return _recipeRepository.fetchRecipesByCollectionId(filterValue, token);
      }
    });
    _isLoadRecipesRunning = false;
  }

  AsyncValue<List<Recipe>> getFilteredRecipes(String searchQuery) {
    final currentRecipes = _recipes.value;
    final query = searchQuery.trim();

    if (query.isEmpty) {
      return currentRecipes;
    }

    return currentRecipes.when(
      loading: () => const AsyncValue.loading(),
      error: (error) => AsyncValue.error(error),
      data: (recipes) {
        if (recipes.isEmpty) {
          return const AsyncValue.data([]);
        }

        final fuse = Fuzzy(
          recipes,
          options: FuzzyOptions(
            keys: [
              WeightedKey(
                name: 'name',
                getter: (Recipe recipe) => recipe.name,
                weight: 1.0,
              ),
            ],
            threshold: 0.6,
            shouldSort: true,
          ),
        );

        final results = fuse.search(query);
        return AsyncValue.data(results.map((result) => result.item).toList());
      },
    );
  }

  Future<void> createRecipe(
    RecipeRequest recipeRequest,
    List<RecipeImageInput> images,
  ) async {
    if (_isCreateRecipeRunning) return;
    _isCreateRecipeRunning = true;

    final result = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;

      // Use multipart if images present, otherwise JSON
      if (images.isNotEmpty) {
        return _recipeRepository.createRecipeMultipart(
          recipeRequest,
          images,
          token,
        );
      } else {
        return _recipeRepository.createRecipe(recipeRequest, token);
      }
    });

    if (result is AsyncData) {
      await loadRecipes();
    }

    _isCreateRecipeRunning = false;

    if (result is AsyncError<RecipeDetail>) {
      throw result.error;
    }
  }

  void dispose() {
    _recipes.dispose();
    _selectedCollectionId.dispose();
  }
}
