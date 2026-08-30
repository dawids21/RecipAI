import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:recipai_mobile/core/get_it.dart';
import 'package:recipai_mobile/core/theme.dart';

import '../features/auth/auth_service.dart';
import '../features/auth/dev_auth_service.dart';
import '../features/auth/login_screen.dart';
import '../features/extraction/extraction_service.dart';
import '../features/extraction/image_extraction_screen.dart';
import '../features/extraction/share_route_extras.dart';
import '../features/extraction/url_extraction_screen.dart';
import '../features/invites/invites_screen.dart';
import '../features/invites/invites_service.dart';
import '../features/limits/limits_service.dart';
import '../features/planning/meal_plan_calendar_service.dart';
import '../features/planning/meal_plan_list_service.dart';
import '../features/planning/meal_plan_visibility_service.dart';
import '../features/planning/shopping_list_generation_calendar_service.dart';
import '../features/planning/shopping_list_generation_screen.dart';
import '../features/planning/shopping_list_generation_service.dart';
import '../features/recipe/collection/recipes_collection_list_screen.dart';
import '../features/recipe/collection/recipes_collection_list_service.dart';
import '../features/recipe/create_recipe_screen.dart';
import '../features/recipe/edit_recipe_screen.dart';
import '../features/recipe/initial_recipe_form_data.dart';
import '../features/recipe/recipe_detail_screen.dart';
import '../features/recipe/recipe_detail_service.dart';
import '../features/recipe/recipe_list_service.dart';
import '../features/recipe/recipe_picker_screen.dart';
import '../features/recipe/recipe_to_shopping_list_screen.dart';
import '../features/shopping_list/shopping_list_detail_screen.dart';
import '../features/shopping_list/shopping_list_detail_service.dart';
import '../features/shopping_list/shopping_list_item_import_service.dart';
import '../features/shopping_list/shopping_list_list_service.dart';
import 'main_screen.dart';

/// Route definitions with enum for type-safe navigation
enum AppRoute {
  login('/login'),
  main('/'),
  urlExtraction('recipes/url-extraction'), // '/recipes/url-extraction'
  imageExtraction('recipes/image-extraction'), // '/recipes/image-extraction'
  recipeCreate('recipes/create'), // '/recipes/create'
  recipePicker('recipes/picker'), // '/recipes/picker'
  recipeDetail('recipes/:id'), // '/recipes/:id'
  recipeEdit('edit'), // '/recipes/:id/edit'
  recipesCollections('recipes-collections'), // '/recipes-collections'
  invites('invites'), // '/invites'
  recipeToShoppingList('to-shopping-list'), // '/recipes/:id/to-shopping-list'
  shoppingListGeneration(
    'shopping-list-generation',
  ), // '/shopping-list-generation'
  shoppingListDetail('shopping-lists/:id'); // '/shopping-lists/:id'

  const AppRoute(this.path);

  final String path;
}

/// Error page widget for handling invalid routes
class ErrorPage extends StatelessWidget {
  final Exception? error;

  const ErrorPage({super.key, this.error});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(
        title: const Text('Error'),
        backgroundColor: theme.colorScheme.inversePrimary,
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.error_outline, size: 64, color: theme.colorScheme.error),
            const SizedBox(height: AppSpacing.medium),
            Text('Page not found', style: theme.textTheme.headlineSmall),
            const SizedBox(height: AppSpacing.small),
            Text(
              error?.toString() ?? 'The requested page could not be found.',
              style: theme.textTheme.bodyMedium,
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: AppSpacing.large),
            ElevatedButton(
              onPressed: () => context.goNamed(AppRoute.main.name),
              child: const Text('Go to home'),
            ),
          ],
        ),
      ),
    );
  }
}

/// Main router configuration for the application
GoRouter createAppRouter() {
  final authService = getIt<AuthService>();

  return GoRouter(
    initialLocation: AppRoute.main.path,
    refreshListenable: authService.isAuthenticated,
    redirect: (context, state) {
      final isAuthenticated = authService.isAuthenticated.value;
      final isLoginRoute = state.matchedLocation == AppRoute.login.path;

      // If not authenticated and not on login route, redirect to login
      if (!isAuthenticated && !isLoginRoute) {
        return AppRoute.login.path;
      }

      // If authenticated and on login route, redirect to main
      if (isAuthenticated && isLoginRoute) {
        return AppRoute.main.path;
      }

      // No redirect needed
      return null;
    },
    errorBuilder: (context, state) => ErrorPage(error: state.error),
    routes: [
      GoRoute(
        path: AppRoute.login.path,
        name: AppRoute.login.name,
        builder: (context, state) => LoginScreen(
          authService: getIt<AuthService>(),
          devAuthService: getIt.isRegistered<DevAuthService>()
              ? getIt<DevAuthService>()
              : null,
        ),
      ),
      GoRoute(
        path: AppRoute.main.path,
        name: AppRoute.main.name,
        builder: (context, state) => MainScreen(
          recipeListService: getIt<RecipeListService>(),
          recipesCollectionListService: getIt<RecipesCollectionListService>(),
          shoppingListListService: getIt<ShoppingListListService>(),
          authService: getIt<AuthService>(),
          mealPlanCalendarService: getIt<MealPlanCalendarService>(),
          mealPlanListService: getIt<MealPlanListService>(),
          mealPlanVisibilityService: getIt<MealPlanVisibilityService>(),
          limitsService: getIt<LimitsService>(),
          invitesService: getIt<InvitesService>(),
        ),
        routes: [
          GoRoute(
            path: AppRoute.urlExtraction.path,
            name: AppRoute.urlExtraction.name,
            builder: (context, state) {
              final prefill = state.extra as UrlPrefill?;
              return UrlExtractionScreen(
                extractionService: getIt<ExtractionService>(),
                limitsService: getIt<LimitsService>(),
                initialUrl: prefill?.url,
              );
            },
          ),
          GoRoute(
            path: AppRoute.imageExtraction.path,
            name: AppRoute.imageExtraction.name,
            builder: (context, state) {
              final prefill = state.extra as ImagePrefill?;
              return ImageExtractionScreen(
                extractionService: getIt<ExtractionService>(),
                limitsService: getIt<LimitsService>(),
                initialImageFile: prefill?.file,
              );
            },
          ),
          GoRoute(
            path: AppRoute.recipeCreate.path,
            name: AppRoute.recipeCreate.name,
            builder: (context, state) {
              final formData = state.extra as InitialRecipeFormData?;
              return CreateRecipeScreen(
                initialFormData: formData,
                recipeListService: getIt<RecipeListService>(),
                recipesCollectionListService:
                    getIt<RecipesCollectionListService>(),
                limitsService: getIt<LimitsService>(),
              );
            },
          ),
          GoRoute(
            path: AppRoute.recipePicker.path,
            name: AppRoute.recipePicker.name,
            builder: (context, state) => RecipePickerScreen(
              recipeListService: getIt<RecipeListService>(),
              recipesCollectionListService:
                  getIt<RecipesCollectionListService>(),
            ),
          ),
          GoRoute(
            path: AppRoute.recipeDetail.path,
            name: AppRoute.recipeDetail.name,
            builder: (context, state) {
              final id = state.pathParameters['id']!;
              return RecipeDetailScreen(
                recipeId: id,
                recipeDetailService: getIt<RecipeDetailService>(),
                authService: getIt<AuthService>(),
                mealPlanCalendarService: getIt<MealPlanCalendarService>(),
                mealPlanListService: getIt<MealPlanListService>(),
                recipesCollectionListService:
                    getIt<RecipesCollectionListService>(),
              );
            },
            routes: [
              GoRoute(
                path: AppRoute.recipeEdit.path,
                name: AppRoute.recipeEdit.name,
                builder: (context, state) {
                  final id = state.pathParameters['id']!;
                  return EditRecipeScreen(
                    recipeId: id,
                    recipeDetailService: getIt<RecipeDetailService>(),
                    recipesCollectionListService:
                        getIt<RecipesCollectionListService>(),
                  );
                },
              ),
              GoRoute(
                path: AppRoute.recipeToShoppingList.path,
                name: AppRoute.recipeToShoppingList.name,
                builder: (context, state) {
                  final id = state.pathParameters['id']!;
                  return RecipeToShoppingListScreen(
                    recipeId: id,
                    recipeDetailService: getIt<RecipeDetailService>(),
                    shoppingListListService: getIt<ShoppingListListService>(),
                    importService: getIt<ShoppingListItemImportService>(),
                  );
                },
              ),
            ],
          ),
          GoRoute(
            path: AppRoute.recipesCollections.path,
            name: AppRoute.recipesCollections.name,
            builder: (context, state) {
              return RecipesCollectionListScreen(
                recipesCollectionListService:
                    getIt<RecipesCollectionListService>(),
                limitsService: getIt<LimitsService>(),
                authService: getIt<AuthService>(),
              );
            },
          ),
          GoRoute(
            path: AppRoute.invites.path,
            name: AppRoute.invites.name,
            builder: (context, state) {
              return InvitesScreen(invitesService: getIt<InvitesService>());
            },
          ),
          GoRoute(
            path: AppRoute.shoppingListGeneration.path,
            name: AppRoute.shoppingListGeneration.name,
            builder: (context, state) => ShoppingListGenerationScreen(
              mealPlanListService: getIt<MealPlanListService>(),
              shoppingListListService: getIt<ShoppingListListService>(),
              generationService: getIt<ShoppingListGenerationService>(),
              calendarService: getIt<ShoppingListGenerationCalendarService>(),
              importService: getIt<ShoppingListItemImportService>(),
            ),
          ),
          GoRoute(
            path: AppRoute.shoppingListDetail.path,
            name: AppRoute.shoppingListDetail.name,
            builder: (context, state) {
              final id = state.pathParameters['id']!;
              final name = state.extra as String?;
              return ShoppingListDetailScreen(
                shoppingListId: id,
                shoppingListName: name,
                shoppingListDetailService: getIt<ShoppingListDetailService>(),
                authService: getIt<AuthService>(),
              );
            },
          ),
        ],
      ),
    ],
  );
}
