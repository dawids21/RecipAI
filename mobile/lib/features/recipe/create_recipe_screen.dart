import 'package:collection/collection.dart';
import 'package:flutter/material.dart';
import 'package:recipai_mobile/features/recipe/collection/recipes_collection.dart';

import '../limits/limit_cap.dart';
import '../limits/limit_counter.dart';
import '../limits/limit_gate.dart';
import '../limits/limits_service.dart';
import 'collection/recipes_collection_list_service.dart';
import 'initial_recipe_form_data.dart';
import 'recipe_detail.dart';
import 'recipe_form_widget.dart';
import 'recipe_image_input.dart';
import 'recipe_list_service.dart';

class CreateRecipeScreen extends StatefulWidget {
  final InitialRecipeFormData? initialFormData;
  final RecipeListService recipeListService;
  final RecipesCollectionListService recipesCollectionListService;
  final LimitsService limitsService;

  const CreateRecipeScreen({
    super.key,
    this.initialFormData,
    required this.recipeListService,
    required this.recipesCollectionListService,
    required this.limitsService,
  });

  @override
  State<CreateRecipeScreen> createState() => _CreateRecipeScreenState();
}

class _CreateRecipeScreenState extends State<CreateRecipeScreen> {
  @override
  void initState() {
    super.initState();
    widget.recipeListService.loadRecipeUsage();
  }

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

  Future<void> _createRecipe(
    RecipeRequest recipeRequest,
    List<RecipeImageInput> images,
  ) async {
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
        child: LimitGate(
          usage: widget.recipeListService.recipeUsage,
          cap: widget.limitsService.capFor(LimitResources.recipe),
          builder: (context, usage, cap) {
            final limitCounter = (usage != null && cap != null)
                ? LimitCounter(
                    used: usage.used,
                    limit: cap.limit,
                    resetsInSeconds: usage.resetsInSeconds,
                    noun: 'recipes',
                  )
                : null;
            final saveBlocked =
                usage != null && cap != null && usage.used >= cap.limit;

            return RecipeFormWidget(
              initialFormData: widget.initialFormData,
              initialCollection: selectedCollection,
              onSave: _createRecipe,
              recipesCollectionListService: widget.recipesCollectionListService,
              limitCounter: limitCounter,
              saveBlocked: saveBlocked,
            );
          },
        ),
      ),
    );
  }
}
