import '../../core/get_it.dart';
import '../auth/auth_service.dart';
import '../planning/meal_plan_list_service.dart';
import '../recipe/collection/recipes_collection_list_service.dart';
import '../recipe/recipe_list_service.dart';
import '../shopping_list/shopping_list_list_service.dart';
import 'invites_repository.dart';
import 'invites_service.dart';

void setupInvites({InvitesRepository? invitesRepository}) {
  final repository = invitesRepository ?? InvitesRepository();
  getIt.registerSingleton<InvitesRepository>(repository);
  getIt.registerLazySingleton(
    () => InvitesService(
      invitesRepository: getIt<InvitesRepository>(),
      authService: getIt<AuthService>(),
      recipeListService: getIt<RecipeListService>(),
      recipesCollectionListService: getIt<RecipesCollectionListService>(),
      shoppingListListService: getIt<ShoppingListListService>(),
      mealPlanListService: getIt<MealPlanListService>(),
    ),
    dispose: (service) => service.dispose(),
  );
}
