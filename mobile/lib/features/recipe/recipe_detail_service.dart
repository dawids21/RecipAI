import 'package:flutter/foundation.dart';
import 'package:recipai_mobile/features/recipe/recipe_image_input.dart';

import '../../core/async_value.dart';
import '../auth/auth_service.dart';
import '../sharing/resource_permission.dart';
import 'recipe_detail.dart';
import 'recipe_list_service.dart';
import 'recipe_repository.dart';

class RecipeDetailService {
  final RecipeRepository _recipeRepository;
  final RecipeListService _recipeListService;
  final AuthService _authService;

  RecipeDetailService({
    required RecipeRepository recipeRepository,
    required RecipeListService recipeListService,
    required AuthService authService,
  }) : _recipeRepository = recipeRepository,
       _recipeListService = recipeListService,
       _authService = authService;

  final ValueNotifier<AsyncValue<RecipeDetail>> _recipeDetail = ValueNotifier(
    const AsyncValue.loading(),
  );

  ValueListenable<AsyncValue<RecipeDetail>> get recipeDetail => _recipeDetail;

  final ValueNotifier<AsyncValue<List<ResourcePermission>>> _permissions =
      ValueNotifier(const AsyncValue.loading());

  ValueListenable<AsyncValue<List<ResourcePermission>>> get permissions =>
      _permissions;

  bool _isLoadRecipeDetailRunning = false;
  bool _isDeleteRecipeRunning = false;
  bool _isUpdateRecipeRunning = false;
  bool _isLoadPermissionsRunning = false;
  bool _isShareRecipeRunning = false;
  bool _isUnshareRecipeRunning = false;

  Future<void> loadRecipeDetail(String id) async {
    if (_isLoadRecipeDetailRunning) return;
    _isLoadRecipeDetailRunning = true;
    _recipeDetail.value = const AsyncValue.loading();
    _recipeDetail.value = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      return _recipeRepository.fetchRecipeDetail(id, token);
    });
    _isLoadRecipeDetailRunning = false;
  }

  Future<void> updateRecipe(
    String id,
    RecipeRequest recipeRequest,
    List<RecipeImageInput> images,
  ) async {
    if (_isUpdateRecipeRunning) return;
    _isUpdateRecipeRunning = true;

    final result = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;

      if (images.isNotEmpty) {
        return _recipeRepository.updateRecipeMultipart(
          id,
          recipeRequest,
          images,
          token,
        );
      } else {
        return _recipeRepository.updateRecipe(id, recipeRequest, token);
      }
    });

    if (result is AsyncData) {
      _recipeDetail.value = result;
      await _recipeListService.loadRecipes();
    }

    _isUpdateRecipeRunning = false;

    if (result is AsyncError<RecipeDetail>) {
      throw result.error;
    }
  }

  Future<void> deleteRecipe(String id) async {
    if (_isDeleteRecipeRunning) return;
    _isDeleteRecipeRunning = true;

    final result = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      return _recipeRepository.deleteRecipe(id, token);
    });

    if (result is AsyncData) {
      await _recipeListService.loadRecipes();
    }

    _isDeleteRecipeRunning = false;

    if (result is AsyncError) {
      throw result.error;
    }
  }

  Future<void> loadPermissions(String id) async {
    if (_isLoadPermissionsRunning) return;
    _isLoadPermissionsRunning = true;

    _permissions.value = const AsyncValue.loading();

    _permissions.value = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      return _recipeRepository.fetchPermissions(id, token);
    });

    _isLoadPermissionsRunning = false;
  }

  Future<void> shareRecipe(String email) async {
    if (_isShareRecipeRunning) return;
    _isShareRecipeRunning = true;

    // Get recipeId from current state
    final recipeDetail = _recipeDetail.value;
    if (recipeDetail is! AsyncData<RecipeDetail>) {
      _isShareRecipeRunning = false;
      throw Exception('Recipe not loaded');
    }
    final recipeId = recipeDetail.value.id;

    final result = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      return _recipeRepository.shareRecipe(recipeId, email, token);
    });

    if (result is AsyncData) {
      await loadPermissions(recipeId); // Refresh list on success
    }

    _isShareRecipeRunning = false;

    if (result is AsyncError) {
      throw result.error;
    }
  }

  Future<void> unshareRecipe(String email) async {
    if (_isUnshareRecipeRunning) return;
    _isUnshareRecipeRunning = true;

    // Get recipeId from current state
    final recipeDetail = _recipeDetail.value;
    if (recipeDetail is! AsyncData<RecipeDetail>) {
      _isUnshareRecipeRunning = false;
      throw Exception('Recipe not loaded');
    }
    final recipeId = recipeDetail.value.id;

    final result = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      return _recipeRepository.unshareRecipe(recipeId, email, token);
    });

    if (result is AsyncData) {
      await loadPermissions(recipeId); // Refresh list on success
    }

    _isUnshareRecipeRunning = false;

    if (result is AsyncError) {
      throw result.error;
    }
  }
}
