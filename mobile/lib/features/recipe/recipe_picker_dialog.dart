import 'package:flutter/material.dart';
import 'package:recipai_mobile/core/theme.dart';
import 'package:recipai_mobile/features/recipe/collection/recipes_collection_list_service.dart';
import 'package:recipai_mobile/features/recipe/recipe_list.dart';
import 'package:recipai_mobile/features/recipe/recipe_list_service.dart';

class RecipePickerDialog extends StatelessWidget {
  final RecipeListService recipeListService;
  final RecipesCollectionListService recipesCollectionListService;

  const RecipePickerDialog({
    super.key,
    required this.recipeListService,
    required this.recipesCollectionListService,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Dialog(
      child: Column(
        children: [
          Container(
            padding: AppSpacing.mediumVertical,
            decoration: BoxDecoration(
              border: Border(
                bottom: BorderSide(
                  color: theme.colorScheme.outlineVariant,
                  width: 1,
                ),
              ),
            ),
            child: Row(
              children: [
                IconButton(
                  icon: const Icon(Icons.close),
                  onPressed: () => Navigator.pop(context),
                ),
                Expanded(
                  child: Text(
                    'Select Recipe',
                    style: theme.textTheme.titleLarge,
                    textAlign: TextAlign.center,
                  ),
                ),
                const SizedBox(width: 48),
              ],
            ),
          ),
          Expanded(
            child: RecipeList(
              recipeListService: recipeListService,
              recipesCollectionListService: recipesCollectionListService,
              onRecipeTap: (context, recipe) {
                Navigator.pop(context, recipe);
              },
            ),
          ),
        ],
      ),
    );
  }
}
