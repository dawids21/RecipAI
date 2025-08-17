import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:recipai_mobile/core/theme.dart';

import '../features/import/import_screen.dart';
import '../features/recipe/create_recipe_screen.dart';
import '../features/recipe/edit_recipe_screen.dart';
import '../features/recipe/recipe_detail_screen.dart';
import '../features/recipe/recipe_list_screen.dart';

/// Route definitions with enum for type-safe navigation
enum AppRoute {
  home('/'),
  recipes('/recipes'),
  recipeDetail(':id'), // nested under /recipes
  recipeImport('import'), // nested under /recipes
  recipeCreate('create'), // nested under /recipes
  recipeEdit('edit'); // nested under /recipes/:id

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
              onPressed: () => context.goNamed(AppRoute.home.name),
              child: const Text('Go to home'),
            ),
          ],
        ),
      ),
    );
  }
}

/// Main router configuration for the application
final GoRouter appRouter = GoRouter(
  initialLocation: AppRoute.home.path,
  errorBuilder: (context, state) => ErrorPage(error: state.error),
  routes: [
    GoRoute(
      path: AppRoute.home.path,
      name: AppRoute.home.name,
      redirect: (context, state) => AppRoute.recipes.path,
    ),
    GoRoute(
      path: AppRoute.recipes.path,
      name: AppRoute.recipes.name,
      builder: (context, state) => const RecipeListScreen(),
      routes: [
        GoRoute(
          path: AppRoute.recipeImport.path,
          name: AppRoute.recipeImport.name,
          builder: (context, state) => const ImportScreen(),
        ),
        GoRoute(
          path: AppRoute.recipeCreate.path,
          name: AppRoute.recipeCreate.name,
          builder: (context, state) => const CreateRecipeScreen(),
        ),
        GoRoute(
          path: AppRoute.recipeDetail.path,
          name: AppRoute.recipeDetail.name,
          builder: (context, state) {
            final id = state.pathParameters['id']!;
            return RecipeDetailScreen(recipeId: id);
          },
          routes: [
            GoRoute(
              path: AppRoute.recipeEdit.path,
              name: AppRoute.recipeEdit.name,
              builder: (context, state) {
                final id = state.pathParameters['id']!;
                return EditRecipeScreen(recipeId: id);
              },
            ),
          ],
        ),
      ],
    ),
  ],
);
