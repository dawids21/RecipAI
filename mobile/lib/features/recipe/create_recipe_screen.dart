import 'package:collection/collection.dart';
import 'package:flutter/material.dart';
import 'package:recipai_mobile/features/recipe/collection/recipes_collection.dart';

import 'collection/recipes_collection_list_service.dart';
import 'recipe_detail.dart';
import 'recipe_form_widget.dart';
import 'recipe_list_service.dart';

class CreateRecipeScreen extends StatefulWidget {
  final RecipeDetail? prefilledRecipe;
  final RecipeListService recipeListService;
  final RecipesCollectionListService recipesCollectionListService;

  const CreateRecipeScreen({
    super.key,
    this.prefilledRecipe,
    required this.recipeListService,
    required this.recipesCollectionListService,
  });

  @override
  State<CreateRecipeScreen> createState() => _CreateRecipeScreenState();
}

class _CreateRecipeScreenState extends State<CreateRecipeScreen> {
  RecipesCollection? _getSelectedCollection() {
    final selectedCollectionId =
        widget.recipeListService.selectedCollectionId.value;
    RecipesCollection? selectedCollection;
    if (selectedCollectionId != null &&
        selectedCollectionId != RecipeListService.unassignedFilterId) {
      selectedCollection = widget
          .recipesCollectionListService
          .recipesCollections
          .value
          .valueOrNull
          ?.firstWhereOrNull((element) => element.id == selectedCollectionId);
    }
    return selectedCollection;
  }

  Future<void> _createRecipe(recipeRequest, images) async {
    return widget.recipeListService.createRecipe(recipeRequest, images);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    RecipesCollection? selectedCollection = _getSelectedCollection();

    return Scaffold(
      appBar: AppBar(
        title: const Text('Create Recipe'),
        backgroundColor: theme.colorScheme.inversePrimary,
      ),
      body: SafeArea(
        top: false,
        child: RecipeFormWidget(
          initialRecipe: widget.prefilledRecipe,
          initialCollection: selectedCollection,
          onSave: _createRecipe,
          recipesCollectionListService: widget.recipesCollectionListService,
        ),
      ),
    );
  }
}
