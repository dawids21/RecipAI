import 'package:flutter/material.dart';

import 'recipe_detail.dart';
import 'recipe_form_widget.dart';
import 'recipe_list_service.dart';

class CreateRecipeScreen extends StatefulWidget {
  final RecipeDetail? prefilledRecipe;
  final RecipeListService recipeListService;

  const CreateRecipeScreen({
    super.key,
    this.prefilledRecipe,
    required this.recipeListService,
  });

  @override
  State<CreateRecipeScreen> createState() => _CreateRecipeScreenState();
}

class _CreateRecipeScreenState extends State<CreateRecipeScreen> {
  Future<void> _createRecipe(RecipeDetail recipe) async {
    return widget.recipeListService.createRecipe(recipe);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Create Recipe'),
        backgroundColor: theme.colorScheme.inversePrimary,
      ),
      body: SafeArea(
        top: false,
        child: RecipeFormWidget(
          initialRecipe: widget.prefilledRecipe,
          onSave: _createRecipe,
        ),
      ),
    );
  }
}
