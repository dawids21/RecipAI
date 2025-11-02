import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:recipai_mobile/core/get_it.dart';
import 'package:recipai_mobile/core/theme.dart';
import 'package:recipai_mobile/features/recipe/recipe_detail.dart';

import '../features/auth/auth_service.dart';
import '../features/auth/login_screen.dart';
import '../features/extraction/extraction_service.dart';
import '../features/extraction/image_extraction_screen.dart';
import '../features/extraction/url_extraction_screen.dart';
import '../features/recipe/create_recipe_screen.dart';
import '../features/recipe/edit_recipe_screen.dart';
import '../features/recipe/recipe_detail_screen.dart';
import '../features/recipe/recipe_detail_service.dart';
import '../features/recipe/recipe_list_screen.dart';
import '../features/recipe/recipe_list_service.dart';
import '../features/shopping_list/shopping_list_list_screen.dart';
import '../features/shopping_list/shopping_list_list_service.dart';
import 'bottom_navigation_scaffold.dart';

/// Route definitions with enum for type-safe navigation
enum AppRoute {
  login('/login'),
  recipes('/'),
  shoppingLists('/shopping-lists'),
  urlExtraction('recipes/url-extraction'), // '/recipes/url-extraction'
  imageExtraction('recipes/image-extraction'), // '/recipes/image-extraction'
  recipeCreate('recipes/create'), // '/recipes/create'
  recipeDetail('recipes/:id'), // '/recipes/:id'
  recipeEdit('edit'); // '/recipes/:id/edit');

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
              onPressed: () => context.goNamed(AppRoute.recipes.name),
              child: const Text('Go to home'),
            ),
          ],
        ),
      ),
    );
  }
}

/// Navigator keys for each shell branch
final GlobalKey<NavigatorState> _recipesNavigatorKey =
    GlobalKey<NavigatorState>(debugLabel: 'recipes');
final GlobalKey<NavigatorState> _shoppingNavigatorKey =
    GlobalKey<NavigatorState>(debugLabel: 'shopping');

/// Main router configuration for the application
GoRouter createAppRouter() {
  final authService = getIt<AuthService>();

  return GoRouter(
    initialLocation: AppRoute.recipes.path,
    refreshListenable: authService.isAuthenticated,
    redirect: (context, state) {
      final isAuthenticated = authService.isAuthenticated.value;
      final isLoginRoute = state.matchedLocation == AppRoute.login.path;

      // If not authenticated and not on login route, redirect to login
      if (!isAuthenticated && !isLoginRoute) {
        return AppRoute.login.path;
      }

      // If authenticated and on login route, redirect to recipes
      if (isAuthenticated && isLoginRoute) {
        return AppRoute.recipes.path;
      }

      // No redirect needed
      return null;
    },
    errorBuilder: (context, state) => ErrorPage(error: state.error),
    routes: [
      GoRoute(
        path: AppRoute.login.path,
        name: AppRoute.login.name,
        builder: (context, state) =>
            LoginScreen(authService: getIt<AuthService>()),
      ),
      StatefulShellRoute.indexedStack(
        builder: (context, state, navigationShell) {
          return BottomNavigationScaffold(navigationShell: navigationShell);
        },
        branches: [
          // Branch 1: Recipes
          StatefulShellBranch(
            navigatorKey: _recipesNavigatorKey,
            routes: [
              GoRoute(
                path: AppRoute.recipes.path,
                name: AppRoute.recipes.name,
                builder: (context, state) => RecipeListScreen(
                  recipeListService: getIt<RecipeListService>(),
                  authService: getIt<AuthService>(),
                ),
                routes: [
                  GoRoute(
                    path: AppRoute.urlExtraction.path,
                    name: AppRoute.urlExtraction.name,
                    builder: (context, state) => UrlExtractionScreen(
                      extractionService: getIt<ExtractionService>(),
                    ),
                  ),
                  GoRoute(
                    path: AppRoute.imageExtraction.path,
                    name: AppRoute.imageExtraction.name,
                    builder: (context, state) => ImageExtractionScreen(
                      extractionService: getIt<ExtractionService>(),
                    ),
                  ),
                  GoRoute(
                    path: AppRoute.recipeCreate.path,
                    name: AppRoute.recipeCreate.name,
                    builder: (context, state) {
                      final recipeDetail = state.extra as RecipeDetail?;
                      return CreateRecipeScreen(
                        prefilledRecipe: recipeDetail,
                        recipeListService: getIt<RecipeListService>(),
                      );
                    },
                  ),
                  GoRoute(
                    path: AppRoute.recipeDetail.path,
                    name: AppRoute.recipeDetail.name,
                    builder: (context, state) {
                      final id = state.pathParameters['id']!;
                      return RecipeDetailScreen(
                        recipeId: id,
                        recipeDetailService: getIt<RecipeDetailService>(),
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
                          );
                        },
                      ),
                    ],
                  ),
                ],
              ),
            ],
          ),
          // Branch 2: Shopping Lists
          StatefulShellBranch(
            navigatorKey: _shoppingNavigatorKey,
            routes: [
              GoRoute(
                path: AppRoute.shoppingLists.path,
                name: AppRoute.shoppingLists.name,
                builder: (context, state) => ShoppingListListScreen(
                  shoppingListListService: getIt<ShoppingListListService>(),
                  authService: getIt<AuthService>(),
                ),
              ),
            ],
          ),
        ],
      ),
    ],
  );
}
