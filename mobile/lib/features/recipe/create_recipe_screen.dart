import 'package:flutter/material.dart';

import '../../core/api_service.dart';
import 'recipe_detail.dart';
import 'recipe_form_widget.dart';

class CreateRecipeScreen extends StatefulWidget {
  const CreateRecipeScreen({super.key});

  @override
  State<CreateRecipeScreen> createState() => _CreateRecipeScreenState();
}

class _CreateRecipeScreenState extends State<CreateRecipeScreen> {
  Future<RecipeDetail> _createRecipe(RecipeDetail recipe) async {
    return await ApiService.createRecipe(recipe);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Create Recipe'),
        backgroundColor: theme.colorScheme.inversePrimary,
      ),
      body: RecipeFormWidget(
        onSave: _createRecipe,
      ),
    );
  }
}
