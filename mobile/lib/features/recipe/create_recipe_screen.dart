import 'package:flutter/material.dart';

import '../../core/api_service.dart';
import 'recipe_detail.dart';
import 'recipe_form_widget.dart';

class CreateRecipeScreen extends StatefulWidget {
  final RecipeDetail? prefilledRecipe;

  const CreateRecipeScreen({super.key, this.prefilledRecipe});

  @override
  State<CreateRecipeScreen> createState() => _CreateRecipeScreenState();
}

class _CreateRecipeScreenState extends State<CreateRecipeScreen> {
  late ApiService _apiService;

  @override
  void didChangeDependencies() {
    _apiService = InheritedApiService.of(context);
    super.didChangeDependencies();
  }

  Future<RecipeDetail> _createRecipe(RecipeDetail recipe) async {
    return _apiService.createRecipe(recipe);
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
        initialRecipe: widget.prefilledRecipe,
        onSave: _createRecipe,
      ),
    );
  }
}
