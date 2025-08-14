import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_service.dart';
import '../../core/theme.dart';
import '../../shared/error_message_widget.dart';
import '../../shared/loading_widget.dart';
import 'ingredient_input_widget.dart';
import 'recipe_detail.dart';

class CreateRecipeScreen extends StatefulWidget {
  const CreateRecipeScreen({super.key});

  @override
  State<CreateRecipeScreen> createState() => _CreateRecipeScreenState();
}

class _CreateRecipeScreenState extends State<CreateRecipeScreen> {
  final _formKey = GlobalKey<FormState>();
  final _nameController = TextEditingController();
  final _instructionsController = TextEditingController();
  final List<GlobalKey<State>> _ingredientKeys = [];
  final List<Ingredient?> _ingredients = [];
  bool _isLoading = false;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    // Start with one empty ingredient input
    _addIngredient();
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
    final regex = RegExp(r'(\d+(?:[.,]\d+)?)\s*([a-zA-Z]*)\s*');
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

    // Filter out null/empty ingredients
    final validIngredients = _ingredients
        .where((ingredient) => ingredient != null && ingredient.name.isNotEmpty)
        .map(
          (ingredient) =>
              parseIngredientText(ingredient!.name, ingredient.quantity),
        )
        .toList();

    if (validIngredients.isEmpty) {
      setState(() {
        _errorMessage = 'Please add at least one ingredient';
      });
      return;
    }

    final validInstructions = _instructionsController.text
        .split('\n')
        .where((step) => step.trim().isNotEmpty)
        .map((step) => Instruction(step: step.trim()))
        .toList();

    try {
      final recipeData = RecipeData(
        ingredients: validIngredients,
        instructions: validInstructions,
      );

      final newRecipe = RecipeDetail(
        id: '', // Backend will assign ID
        name: _nameController.text.trim(),
        data: recipeData,
      );

      final createdRecipe = await ApiService.createRecipe(newRecipe);

      if (mounted) {
        // Show success message
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Recipe created successfully!')),
        );

        // Navigate back with the created recipe
        context.pop(createdRecipe);
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _errorMessage = 'Failed to create recipe: ${e.toString()}';
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

    return Scaffold(
      appBar: AppBar(
        title: const Text('Create Recipe'),
        backgroundColor: theme.colorScheme.inversePrimary,
      ),
      body: _isLoading
          ? const LoadingWidget()
          : Padding(
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

                            const SizedBox(height: AppSpacing.large),

                            // Ingredients section
                            Row(
                              mainAxisAlignment: MainAxisAlignment.spaceBetween,
                              children: [
                                Text(
                                  'Ingredients',
                                  style: theme.textTheme.titleMedium,
                                ),
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
                                        onIngredientChanged: (ingredient) =>
                                            _onIngredientChanged(
                                              index,
                                              ingredient,
                                            ),
                                      ),
                                    ),
                                    if (_ingredients.length > 1)
                                      IconButton(
                                        onPressed: () =>
                                            _removeIngredient(index),
                                        icon: const Icon(
                                          Icons.remove_circle_outline,
                                        ),
                                        color: theme.colorScheme.error,
                                      ),
                                  ],
                                ),
                              );
                            }),

                            const SizedBox(height: AppSpacing.large),

                            // Instructions input
                            Text(
                              'Instructions',
                              style: theme.textTheme.titleMedium,
                            ),
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
                            _isLoading ? 'Creating...' : 'Create Recipe',
                            style: theme.textTheme.titleMedium,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
    );
  }
}
