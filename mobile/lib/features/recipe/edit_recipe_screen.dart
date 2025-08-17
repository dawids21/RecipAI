import 'package:flutter/material.dart';

import '../../core/api_service.dart';
import '../../shared/api_error_widget.dart';
import '../../shared/loading_widget.dart';
import 'recipe_detail.dart';
import 'recipe_form_widget.dart';

class EditRecipeScreen extends StatefulWidget {
  final String recipeId;

  const EditRecipeScreen({super.key, required this.recipeId});

  @override
  State<EditRecipeScreen> createState() => _EditRecipeScreenState();
}

class _EditRecipeScreenState extends State<EditRecipeScreen> {
  late Future<RecipeDetail> futureRecipeDetail;

  @override
  void initState() {
    super.initState();
    futureRecipeDetail = ApiService.fetchRecipeDetail(widget.recipeId);
  }

  Future<RecipeDetail> _updateRecipe(RecipeDetail recipe) async {
    return await ApiService.updateRecipe(widget.recipeId, recipe);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Edit Recipe'),
        backgroundColor: theme.colorScheme.inversePrimary,
      ),
      body: FutureBuilder<RecipeDetail>(
        future: futureRecipeDetail,
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const LoadingWidget();
          } else if (snapshot.hasError) {
            return ApiErrorWidget(
              errorMessage: 'Error: ${snapshot.error}',
              onRetry: () {
                setState(() {
                  futureRecipeDetail = ApiService.fetchRecipeDetail(
                    widget.recipeId,
                  );
                });
              },
            );
          } else if (snapshot.hasData) {
            final recipeDetail = snapshot.data!;
            return RecipeFormWidget(
              initialRecipe: recipeDetail,
              onSave: _updateRecipe,
            );
          } else {
            return const Center(child: Text('No data available'));
          }
        },
      ),
    );
  }
}
