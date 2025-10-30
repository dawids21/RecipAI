import 'package:recipai_mobile/core/get_it.dart';

import '../auth/auth_service.dart';
import 'recipe_detail_service.dart';
import 'recipe_list_service.dart';
import 'recipe_repository.dart';

void setupRecipe() {
  getIt.registerSingleton(RecipeRepository());
  getIt.registerLazySingleton(
    () => RecipeListService(
      recipeRepository: getIt<RecipeRepository>(),
      authService: getIt<AuthService>(),
    ),
  );
  getIt.registerLazySingleton(
    () => RecipeDetailService(
      recipeRepository: getIt<RecipeRepository>(),
      recipeListService: getIt<RecipeListService>(),
      authService: getIt<AuthService>(),
    ),
  );
}
