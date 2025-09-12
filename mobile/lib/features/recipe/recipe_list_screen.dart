import 'package:flutter/material.dart';
import 'package:flutter_speed_dial/flutter_speed_dial.dart';
import 'package:go_router/go_router.dart';
import 'package:recipai_mobile/core/theme.dart';

import '../../core/routes.dart';
import '../../shared/api_error_widget.dart';
import '../../shared/loading_widget.dart';
import '../auth/auth_service.dart';
import 'recipe.dart';
import 'recipe_list_item.dart';
import 'recipe_list_model.dart';

class RecipeListScreen extends StatefulWidget {
  const RecipeListScreen({super.key});

  @override
  State<RecipeListScreen> createState() => _RecipeListScreenState();
}

class _RecipeListScreenState extends State<RecipeListScreen> {
  late AuthService _authService;

  @override
  void didChangeDependencies() {
    _authService = InheritedAuthService.of(context);
    super.didChangeDependencies();
  }

  void _onRecipeTap(BuildContext context, Recipe recipe) {
    context.goNamed(
      AppRoute.recipeDetail.name,
      pathParameters: {'id': recipe.id},
    );
  }

  void _onExtractionTap(BuildContext context) {
    context.goNamed(AppRoute.extraction.name);
  }

  void _onCreateTap(BuildContext context) {
    context.goNamed(
      AppRoute.recipeCreate.name,
    );
  }

  Future<void> _onLogoutTap(BuildContext context) async {
    final shouldLogout = await showDialog<bool>(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: const Text('Logout'),
          content: const Text('Are you sure you want to logout?'),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(false),
              child: const Text('Cancel'),
            ),
            TextButton(
              onPressed: () => Navigator.of(context).pop(true),
              child: const Text('Logout'),
            ),
          ],
        );
      },
    );

    if (shouldLogout == true) {
      try {
        await _authService.signOut();
      } catch (e) {
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Failed to logout: $e')),
          );
        }
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final recipeListModel = InheritedRecipeListModel.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('RecipAI'),
        backgroundColor: theme.colorScheme.inversePrimary,
        actions: [
          IconButton(
            icon: const Icon(Icons.logout),
            onPressed: () => _onLogoutTap(context),
            tooltip: 'Logout',
          ),
        ],
      ),
      body: FutureBuilder<List<Recipe>>(
        future: recipeListModel.recipes,
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const LoadingWidget();
          } else if (snapshot.hasError) {
            return ApiErrorWidget(
              errorMessage: 'Error: ${snapshot.error}',
              onRetry: () {
                recipeListModel.refresh();
              },
            );
          } else if (snapshot.hasData) {
            final recipes = snapshot.data!;
            if (recipes.isEmpty) {
              return Center(
                child: Text(
                  'No recipes found',
                  style: theme.textTheme.labelMedium,
                ),
              );
            }
            return ListView.builder(
              itemCount: recipes.length,
              itemBuilder: (context, index) {
                return RecipeListItem(
                  recipe: recipes[index],
                  onTap: () => _onRecipeTap(context, recipes[index]),
                );
              },
            );
          } else {
            return const Center(child: Text('No data available'));
          }
        },
      ),
      floatingActionButton: SpeedDial(
        spaceBetweenChildren: AppSpacing.medium,
        icon: Icons.add,
        activeIcon: Icons.menu,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.all(Radius.circular(16.0)),
        ),
        children: [
          SpeedDialChild(
            child: const Icon(Icons.download),
            label: 'Extract Recipe',
            onTap: () => _onExtractionTap(context),
          ),
          SpeedDialChild(
            child: const Icon(Icons.edit),
            label: 'Create Recipe',
            onTap: () => _onCreateTap(context),
          ),
        ],
      ),
    );
  }
}
