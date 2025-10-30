import 'package:flutter/foundation.dart';

import '../../core/async_value.dart';
import '../auth/auth_service.dart';
import 'recipe_detail.dart';
import 'recipe_list_service.dart';
import 'recipe_repository.dart';
import 'recipe_shared_user.dart';

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

  final ValueNotifier<AsyncValue<List<RecipeSharedUser>>> _sharedUsers =
      ValueNotifier(const AsyncValue.loading());

  ValueListenable<AsyncValue<List<RecipeSharedUser>>> get sharedUsers =>
      _sharedUsers;

  bool _isLoadRecipeDetailRunning = false;
  bool _isDeleteRecipeRunning = false;
  bool _isUpdateRecipeRunning = false;
  bool _isLoadSharedUsersRunning = false;
  bool _isShareRecipeRunning = false;
  bool _isUnshareRecipeRunning = false;

  Future<void> loadRecipeDetail(String id) async {
    if (_isLoadRecipeDetailRunning) return;
    _isLoadRecipeDetailRunning = true;
    _recipeDetail.value = const AsyncValue.loading();
    _recipeDetail.value = await AsyncValue.guardAsync(() async {
      return _recipeRepository.fetchRecipeDetail(id);
    });
    _isLoadRecipeDetailRunning = false;
  }

  Future<void> updateRecipe(String id, RecipeDetail recipe) async {
    if (_isUpdateRecipeRunning) return;
    _isUpdateRecipeRunning = true;

    final result = await AsyncValue.guardAsync(() async {
      return _recipeRepository.updateRecipe(id, recipe);
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

  Future<void> loadSharedUsers() async {
    if (_isLoadSharedUsersRunning) return;
    _isLoadSharedUsersRunning = true;

    _sharedUsers.value = const AsyncValue.loading();

    // Get recipeId from current state
    final recipeDetail = _recipeDetail.value;
    if (recipeDetail is! AsyncData<RecipeDetail>) {
      _isLoadSharedUsersRunning = false;
      return;
    }
    final recipeId = recipeDetail.value.id;

    _sharedUsers.value = await AsyncValue.guardAsync(() async {
      final sharedUsers = await _recipeRepository.fetchSharedUsers(recipeId);
      final currentUserEmail = _authService.email;
      return sharedUsers.map((sharedUser) {
        return RecipeSharedUser(
          sharedUser: sharedUser,
          isCurrentUser: sharedUser.email == currentUserEmail,
        );
      }).toList();
    });

    _isLoadSharedUsersRunning = false;
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
      return _recipeRepository.shareRecipe(recipeId, email);
    });

    if (result is AsyncData) {
      await loadSharedUsers(); // Refresh list on success
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
      return _recipeRepository.unshareRecipe(recipeId, email);
    });

    if (result is AsyncData) {
      await loadSharedUsers(); // Refresh list on success
    }

    _isUnshareRecipeRunning = false;

    if (result is AsyncError) {
      throw result.error;
    }
  }
}
