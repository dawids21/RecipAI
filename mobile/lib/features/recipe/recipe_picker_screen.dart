import 'package:flutter/material.dart';
import 'package:recipai_mobile/features/recipe/collection/recipes_collection_list_service.dart';
import 'package:recipai_mobile/features/recipe/recipe_grid.dart';
import 'package:recipai_mobile/features/recipe/recipe_list_service.dart';

class RecipePickerScreen extends StatelessWidget {
  final RecipeListService recipeListService;
  final RecipesCollectionListService recipesCollectionListService;

  const RecipePickerScreen({
    super.key,
    required this.recipeListService,
    required this.recipesCollectionListService,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(
        title: const Text('Select Recipe'),
        backgroundColor: theme.colorScheme.inversePrimary,
        leading: IconButton(
          icon: const Icon(Icons.close),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: SafeArea(
        top: false,
        child: RecipeGrid(
          recipeListService: recipeListService,
          recipesCollectionListService: recipesCollectionListService,
          onRecipeTap: (context, recipe) {
            Navigator.pop(context, recipe);
          },
        ),
      ),
    );
  }
}
