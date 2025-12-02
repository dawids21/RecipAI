import 'package:flutter/material.dart';

import '../../shared/api_error_widget.dart';
import '../../shared/loading_widget.dart';
import 'collection/recipes_collection_list_service.dart';
import 'recipe_detail.dart';
import 'recipe_detail_service.dart';
import 'recipe_form_widget.dart';

class EditRecipeScreen extends StatefulWidget {
  final String recipeId;
  final RecipeDetailService recipeDetailService;
  final RecipesCollectionListService recipesCollectionListService;

  const EditRecipeScreen({
    super.key,
    required this.recipeId,
    required this.recipeDetailService,
    required this.recipesCollectionListService,
  });

  @override
  State<EditRecipeScreen> createState() => _EditRecipeScreenState();
}

class _EditRecipeScreenState extends State<EditRecipeScreen> {
  Future<void> _updateRecipe(RecipeDetail recipe) async {
    await widget.recipeDetailService.updateRecipe(widget.recipeId, recipe);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return ValueListenableBuilder(
      valueListenable: widget.recipeDetailService.recipeDetail,
      builder: (context, asyncValueRecipeDetail, child) {
        return asyncValueRecipeDetail.when(
          loading: () => Scaffold(
            appBar: AppBar(
              title: const Text('Edit Recipe'),
              backgroundColor: theme.colorScheme.inversePrimary,
            ),
            body: const SafeArea(top: false, child: LoadingWidget()),
          ),
          error: (error) => Scaffold(
            appBar: AppBar(
              title: const Text('Edit Recipe'),
              backgroundColor: theme.colorScheme.inversePrimary,
            ),
            body: SafeArea(
              top: false,
              child: ApiErrorWidget(
                errorMessage: 'Error: $error',
                onRetry: () {
                  widget.recipeDetailService.loadRecipeDetail(widget.recipeId);
                },
              ),
            ),
          ),
          data: (recipeDetail) => Scaffold(
            appBar: AppBar(
              title: const Text('Edit Recipe'),
              backgroundColor: theme.colorScheme.inversePrimary,
            ),
            body: SafeArea(
              top: false,
              child: RecipeFormWidget(
                initialRecipe: recipeDetail,
                onSave: _updateRecipe,
                recipesCollectionListService:
                    widget.recipesCollectionListService,
              ),
            ),
          ),
        );
      },
    );
  }
}
