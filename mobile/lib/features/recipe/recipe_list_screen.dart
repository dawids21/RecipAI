import 'package:flutter/material.dart';

import '../../core/api_service.dart';
import '../../shared/api_error_widget.dart';
import '../../shared/loading_widget.dart';
import '../import/import_screen.dart';
import 'recipe.dart';
import 'recipe_detail.dart';
import 'recipe_detail_screen.dart';
import 'recipe_list_item.dart';

class RecipeListScreen extends StatefulWidget {
  const RecipeListScreen({super.key});

  @override
  State<RecipeListScreen> createState() => _RecipeListScreenState();
}

class _RecipeListScreenState extends State<RecipeListScreen> {
  late Future<List<Recipe>> futureRecipes;

  @override
  void initState() {
    super.initState();
    futureRecipes = ApiService.fetchRecipes();
  }

  void _onRecipeTap(Recipe recipe) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => RecipeDetailScreen(recipeId: recipe.id),
      ),
    );
  }

  void _onImportTap() async {
    final result = await Navigator.push<RecipeDetail>(
      context,
      MaterialPageRoute(builder: (context) => const ImportScreen()),
    );

    // If a recipe was imported, refresh the list
    if (result != null) {
      _refreshRecipeList();
    }
  }

  void _refreshRecipeList() {
    setState(() {
      futureRecipes = ApiService.fetchRecipes();
    });
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(
        title: const Text('RecipAI'),
        backgroundColor: theme.colorScheme.inversePrimary,
      ),
      body: FutureBuilder<List<Recipe>>(
        future: futureRecipes,
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const LoadingWidget();
          } else if (snapshot.hasError) {
            return ApiErrorWidget(
              errorMessage: 'Error: ${snapshot.error}',
              onRetry: () {
                setState(() {
                  futureRecipes = ApiService.fetchRecipes();
                });
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
                  onTap: () => _onRecipeTap(recipes[index]),
                );
              },
            );
          } else {
            return const Center(child: Text('No data available'));
          }
        },
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _onImportTap,
        tooltip: 'Import Recipe',
        child: const Icon(Icons.add),
      ),
    );
  }
}
