import 'package:recipai_mobile/core/get_it.dart';

import '../auth/auth_service.dart';
import 'recipe_list_service.dart';
import 'recipe_repository.dart';

void setupRecipe() {
  getIt.registerSingleton(RecipeRepository(getIt<AuthService>()));
  getIt.registerLazySingleton(
    () => RecipeListService(recipeRepository: getIt<RecipeRepository>()),
  );
}
