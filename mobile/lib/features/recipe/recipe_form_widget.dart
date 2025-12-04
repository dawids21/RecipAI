import 'package:collection/collection.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:recipai_mobile/core/feature_flags.dart';

import '../../core/theme.dart';
import '../../shared/error_message_widget.dart';
import '../../shared/loading_widget.dart';
import '../../shared/user_role.dart';
import 'collection/recipes_collection.dart';
import 'collection/recipes_collection_list_service.dart';
import 'ingredient_input_widget.dart';
import 'recipe_detail.dart';

class RecipeFormWidget extends StatefulWidget {
  final RecipeDetail? initialRecipe;
  final RecipesCollection? initialCollection;
  final Future<void> Function(RecipeDetail recipe) onSave;
  final RecipesCollectionListService recipesCollectionListService;

  const RecipeFormWidget({
    super.key,
    this.initialRecipe,
    this.initialCollection,
    required this.onSave,
    required this.recipesCollectionListService,
  });

  @override
  State<RecipeFormWidget> createState() => _RecipeFormWidgetState();
}

class _RecipeFormWidgetState extends State<RecipeFormWidget> {
  final _formKey = GlobalKey<FormState>();
  final _nameController = TextEditingController();
  final _instructionsController = TextEditingController();
  final List<GlobalKey<State>> _ingredientKeys = [];
  final List<Ingredient?> _ingredients = [];
  bool _isLoading = false;
  String? _errorMessage;
  RecipesCollection? _selectedCollection;

  @override
  void initState() {
    super.initState();
    _initializeForm();
  }

  void _initializeForm() {
    if (widget.initialRecipe != null) {
      var initialRecipe = widget.initialRecipe!;
      // Pre-populate form with existing recipe data
      _nameController.text = initialRecipe.name;
      _instructionsController.text = initialRecipe.data.instructions
          .map((instruction) => instruction.step)
          .join('\n');

      // Build selected collection from recipe details
      if (initialRecipe.collectionId != null &&
          initialRecipe.collectionName != null) {
        _selectedCollection = RecipesCollection(
          id: initialRecipe.collectionId!,
          name: initialRecipe.collectionName!,
        );
      } else if (widget.initialCollection != null) {
        _selectedCollection = widget.initialCollection;
      }

      // Pre-populate ingredients
      if (initialRecipe.data.ingredients.isNotEmpty) {
        for (final ingredient in initialRecipe.data.ingredients) {
          _ingredientKeys.add(GlobalKey<State>());
          // Convert back to the format expected by IngredientInputWidget
          final quantityText = ingredient.unit != null
              ? '${ingredient.quantity} ${ingredient.unit!}'
              : ingredient.quantity;
          _ingredients.add(
            Ingredient(
              name: ingredient.name,
              quantity: quantityText,
              unit: null, // IngredientInputWidget will parse this again
            ),
          );
        }
      } else {
        // Start with one empty ingredient input
        _addIngredient();
      }
    } else {
      // Start with one empty ingredient input for new recipes
      if (widget.initialCollection != null) {
        _selectedCollection = widget.initialCollection;
      }
      _addIngredient();
    }
  }

  @override
  void dispose() {
    _nameController.dispose();
    _instructionsController.dispose();
    super.dispose();
  }

  void _addIngredient() {
    setState(() {
      _ingredientKeys.add(GlobalKey<State>());
      _ingredients.add(null);
    });
  }

  void _removeIngredient(int index) {
    if (_ingredients.length > 1) {
      setState(() {
        _ingredientKeys.removeAt(index);
        _ingredients.removeAt(index);
      });
    }
  }

  void _onIngredientChanged(int index, Ingredient? ingredient) {
    _ingredients[index] = ingredient;
  }

  /// Parses ingredient text to extract quantity and unit from the quantity field
  static Ingredient parseIngredientText(String name, String quantityText) {
    final regex = RegExp(r'(\d+(?:[.,]\d+)?)\s*([\p{L}]*)\s*', unicode: true);
    final match = regex.firstMatch(quantityText.trim());

    if (match != null) {
      final quantity = match.group(1) ?? '';
      final unit = match.group(2) ?? '';
      return Ingredient(
        name: name.trim(),
        quantity: quantity,
        unit: unit.isEmpty ? null : unit,
      );
    } else {
      // If no pattern matches, use the full quantity text as quantity
      return Ingredient(
        name: name.trim(),
        quantity: quantityText.trim(),
        unit: null,
      );
    }
  }

  Future<void> _saveRecipe() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      // Filter out null/empty ingredients
      final validIngredients = _ingredients
          .where(
            (ingredient) => ingredient != null && ingredient.name.isNotEmpty,
          )
          .map(
            (ingredient) =>
                parseIngredientText(ingredient!.name, ingredient.quantity),
          )
          .toList();

      if (validIngredients.isEmpty) {
        setState(() {
          _errorMessage = 'Please add at least one ingredient';
          _isLoading = false;
        });
        return;
      }

      final validInstructions = _instructionsController.text
          .split('\n')
          .where((step) => step.trim().isNotEmpty)
          .map((step) => Instruction(step: step.trim()))
          .toList();

      final recipeData = RecipeData(
        ingredients: validIngredients,
        instructions: validInstructions,
      );

      final recipe = RecipeDetail(
        id: widget.initialRecipe?.id ?? '',
        // Keep existing ID for updates
        name: _nameController.text.trim(),
        data: recipeData,
        role: widget.initialRecipe?.role ?? UserRole.owner,
        collectionId: _selectedCollection?.id,
        collectionName: _selectedCollection?.name,
      );

      await widget.onSave(recipe);

      if (mounted) {
        // Show success message
        final successMessage = widget.initialRecipe != null
            ? 'Recipe updated successfully!'
            : 'Recipe created successfully!';
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(successMessage)));

        context.pop();
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _errorMessage = 'Failed to save recipe: ${e.toString()}';
        });
      }
    } finally {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    if (_isLoading) {
      return const LoadingWidget();
    }

    return Padding(
      padding: AppSpacing.screenPadding,
      child: Form(
        key: _formKey,
        child: Column(
          children: [
            // Error message display
            if (_errorMessage != null)
              ErrorMessageWidget(message: _errorMessage!),

            Expanded(
              child: SingleChildScrollView(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Recipe name input
                    TextFormField(
                      controller: _nameController,
                      decoration: const InputDecoration(
                        labelText: 'Recipe name',
                        hintText: 'Enter recipe name',
                      ),
                      validator: (value) {
                        if (value == null || value.trim().isEmpty) {
                          return 'Recipe name is required';
                        }
                        return null;
                      },
                    ),

                    // Collection dropdown
                    if (FeatureFlags.recipesCollectionsEnabled) ...[
                      const SizedBox(height: AppSpacing.medium),
                      ValueListenableBuilder(
                        valueListenable: widget
                            .recipesCollectionListService
                            .recipesCollections,
                        builder: (context, collectionsAsync, _) {
                          return collectionsAsync.when(
                            loading: () =>
                                DropdownButtonFormField<RecipesCollection?>(
                                  decoration: const InputDecoration(
                                    labelText: 'Collection',
                                  ),
                                  items: const [],
                                  onChanged: null, // Disabled while loading
                                ),
                            error: (error) =>
                                DropdownButtonFormField<RecipesCollection?>(
                                  decoration: InputDecoration(
                                    labelText: 'Collection',
                                    errorText: 'Failed to load collections',
                                    errorStyle: TextStyle(
                                      color: theme.colorScheme.error,
                                    ),
                                  ),
                                  items: const [],
                                  onChanged: null, // Disabled on error
                                ),
                            data: (collections) {
                              // Find matching collection from loaded list by ID
                              final currentValue = _selectedCollection != null
                                  ? collections.firstWhereOrNull(
                                      (c) => c.id == _selectedCollection!.id,
                                    )
                                  : null;

                              return DropdownButtonFormField<
                                RecipesCollection?
                              >(
                                initialValue: currentValue,
                                decoration: const InputDecoration(
                                  labelText: 'Collection',
                                ),
                                style: theme.textTheme.bodyLarge,
                                items: [
                                  // "None" option
                                  const DropdownMenuItem<RecipesCollection?>(
                                    value: null,
                                    child: Text('None'),
                                  ),
                                  // Collection options
                                  ...collections.map(
                                    (collection) =>
                                        DropdownMenuItem<RecipesCollection?>(
                                          value: collection,
                                          child: Text(collection.name),
                                        ),
                                  ),
                                ],
                                onChanged: (RecipesCollection? newValue) {
                                  setState(() {
                                    _selectedCollection = newValue;
                                  });
                                },
                              );
                            },
                          );
                        },
                      ),
                    ],

                    const SizedBox(height: AppSpacing.large),

                    // Ingredients section
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text('Ingredients', style: theme.textTheme.titleMedium),
                        TextButton.icon(
                          onPressed: _addIngredient,
                          icon: const Icon(Icons.add),
                          label: const Text('Add'),
                        ),
                      ],
                    ),

                    const SizedBox(height: AppSpacing.small),

                    // Ingredients list
                    ...List.generate(_ingredients.length, (index) {
                      return Padding(
                        padding: AppSpacing.smallVertical,
                        child: Row(
                          children: [
                            Expanded(
                              child: IngredientInputWidget(
                                key: _ingredientKeys[index],
                                initialIngredient: _ingredients[index],
                                onIngredientChanged: (ingredient) =>
                                    _onIngredientChanged(index, ingredient),
                              ),
                            ),
                            if (_ingredients.length > 1)
                              IconButton(
                                onPressed: () => _removeIngredient(index),
                                icon: const Icon(Icons.remove_circle_outline),
                                color: theme.colorScheme.error,
                              ),
                          ],
                        ),
                      );
                    }),

                    const SizedBox(height: AppSpacing.large),

                    // Instructions input
                    Text('Instructions', style: theme.textTheme.titleMedium),
                    const SizedBox(height: AppSpacing.small),
                    TextFormField(
                      controller: _instructionsController,
                      decoration: const InputDecoration(
                        labelText: 'Cooking instructions',
                        hintText: 'Enter each step on a new line...',
                        alignLabelWithHint: true,
                      ),
                      maxLines: 8,
                      validator: (value) {
                        if (value == null || value.trim().isEmpty) {
                          return 'Instructions are required';
                        }
                        return null;
                      },
                    ),

                    const SizedBox(height: AppSpacing.extraLarge),
                  ],
                ),
              ),
            ),

            // Save button
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: _isLoading ? null : _saveRecipe,
                child: Padding(
                  padding: AppSpacing.mediumVertical,
                  child: Text(
                    _isLoading
                        ? 'Saving...'
                        : (widget.initialRecipe != null
                              ? 'Update Recipe'
                              : 'Create Recipe'),
                    style: theme.textTheme.titleMedium,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
