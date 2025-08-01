import 'package:flutter/material.dart';

import '../../core/api_service.dart';
import '../../core/theme.dart';
import '../../shared/error_icon.dart';
import '../../shared/loading_widget.dart';
import 'recipe.dart';
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
            return Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const ErrorIcon(),
                  const SizedBox(height: AppSpacing.medium),
                  Text(
                    'Error: ${snapshot.error}',
                    style: theme.textTheme.bodyLarge,
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: AppSpacing.medium),
                  ElevatedButton(
                    onPressed: () {
                      setState(() {
                        futureRecipes = ApiService.fetchRecipes();
                      });
                    },
                    child: const Text('Retry'),
                  ),
                ],
              ),
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
    );
  }
}
