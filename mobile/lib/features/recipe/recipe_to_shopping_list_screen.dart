import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:recipai_mobile/features/shopping_list/shopping_list_list_service.dart';

import '../../core/theme.dart';
import '../../shared/api_error_widget.dart';
import '../../shared/loading_widget.dart';
import 'recipe_detail.dart';
import 'recipe_detail_service.dart';
import 'recipe_to_shopping_list_service.dart';

class RecipeToShoppingListScreen extends StatefulWidget {
  final String recipeId;
  final RecipeDetailService recipeDetailService;
  final ShoppingListListService shoppingListListService;
  final RecipeToShoppingListService recipeToShoppingListService;

  const RecipeToShoppingListScreen({
    required this.recipeId,
    required this.recipeDetailService,
    required this.shoppingListListService,
    required this.recipeToShoppingListService,
    super.key,
  });

  @override
  State<RecipeToShoppingListScreen> createState() =>
      _RecipeToShoppingListScreenState();
}

class _RecipeToShoppingListScreenState
    extends State<RecipeToShoppingListScreen> {
  // Track which ingredient indices are selected
  Set<int> _selectedIndices = {};

  // Track if we've initialized selection (to handle rebuilds)
  bool _initialized = false;

  void _toggleSelectAll(int ingredientCount) {
    setState(() {
      if (_selectedIndices.length == ingredientCount) {
        _selectedIndices.clear();
      } else {
        _selectedIndices = Set.from(List.generate(ingredientCount, (i) => i));
      }
    });
  }

  Future<String?> _showListSelectionDialog() async {
    final theme = Theme.of(context);

    return showDialog<String>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Select Shopping List'),
        content: ValueListenableBuilder(
          valueListenable: widget.shoppingListListService.shoppingLists,
          builder: (context, listsAsync, _) {
            return listsAsync.when(
              loading: () => const SizedBox(
                height: 100,
                child: Center(child: CircularProgressIndicator()),
              ),
              error: (error) => Text(
                'Failed to load shopping lists',
                style: TextStyle(color: theme.colorScheme.error),
              ),
              data: (lists) {
                if (lists.isEmpty) {
                  return const Text(
                    'No shopping lists available. Create one first.',
                  );
                }

                return SingleChildScrollView(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: lists
                        .map(
                          (list) => Card(
                            child: ListTile(
                              title: Text(
                                list.name,
                                style: theme.textTheme.titleMedium,
                              ),
                              trailing: const Icon(Icons.arrow_forward_ios),
                              onTap: () =>
                                  Navigator.of(dialogContext).pop(list.id),
                              contentPadding: AppSpacing.listTilePadding,
                            ),
                          ),
                        )
                        .toList(),
                  ),
                );
              },
            );
          },
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(),
            child: const Text('Cancel'),
          ),
        ],
      ),
    );
  }

  Future<void> _onAddButtonPressed(List<Ingredient> ingredients) async {
    // Show dialog to select shopping list
    final selectedListId = await _showListSelectionDialog();

    if (selectedListId == null || !mounted) return;

    // Get selected ingredients
    final selectedIngredients = _selectedIndices
        .map((index) => ingredients[index])
        .toList();

    // Add to shopping list via service
    widget.recipeToShoppingListService.addIngredientsToList(
      selectedListId,
      selectedIngredients,
    );

    if (!mounted) return;

    // Show success message
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          'Added ${selectedIngredients.length} ingredient(s) to shopping list',
        ),
      ),
    );

    // Navigate back to recipe detail
    context.pop();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Add to Shopping List'),
        backgroundColor: theme.colorScheme.inversePrimary,
      ),
      body: ValueListenableBuilder(
        valueListenable: widget.recipeDetailService.recipeDetail,
        builder: (context, recipeAsync, _) {
          return recipeAsync.when(
            loading: () => const LoadingWidget(),
            error: (error) => ApiErrorWidget(
              errorMessage: 'Error: $error',
              onRetry: () {
                widget.recipeDetailService.loadRecipeDetail(widget.recipeId);
              },
            ),
            data: (recipeDetail) {
              final ingredients = recipeDetail.data.ingredients;

              // Initialize all as selected on first build
              if (!_initialized && ingredients.isNotEmpty) {
                _selectedIndices = Set.from(
                  List.generate(ingredients.length, (i) => i),
                );
                _initialized = true;
              }

              // Handle edge case: no ingredients
              if (ingredients.isEmpty) {
                return Center(
                  child: Padding(
                    padding: const EdgeInsets.all(AppSpacing.large),
                    child: Text(
                      'This recipe has no ingredients to add.',
                      style: theme.textTheme.bodyLarge,
                      textAlign: TextAlign.center,
                    ),
                  ),
                );
              }

              return Column(
                children: [
                  // Header section
                  Padding(
                    padding: const EdgeInsets.all(AppSpacing.medium),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          recipeDetail.name,
                          style: theme.textTheme.headlineMedium?.copyWith(
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        const SizedBox(height: AppSpacing.small),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              'Select ingredients',
                              style: theme.textTheme.titleMedium,
                            ),
                            TextButton(
                              onPressed: () =>
                                  _toggleSelectAll(ingredients.length),
                              child: Text(
                                _selectedIndices.length == ingredients.length
                                    ? 'Deselect All'
                                    : 'Select All',
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),

                  // Ingredients list
                  Expanded(
                    child: ListView.builder(
                      itemCount: ingredients.length,
                      itemBuilder: (context, index) {
                        final ingredient = ingredients[index];
                        final isSelected = _selectedIndices.contains(index);
                        final subtitle = ingredient.quantity.isNotEmpty
                            ? '${ingredient.quantity}${ingredient.unit != null ? ' ${ingredient.unit}' : ''}'
                            : (ingredient.unit ?? '');

                        return CheckboxListTile(
                          title: Text(ingredient.name),
                          subtitle: subtitle.isNotEmpty ? Text(subtitle) : null,
                          value: isSelected,
                          onChanged: (value) {
                            setState(() {
                              if (value == true) {
                                _selectedIndices.add(index);
                              } else {
                                _selectedIndices.remove(index);
                              }
                            });
                          },
                        );
                      },
                    ),
                  ),

                  // Add button
                  Padding(
                    padding: const EdgeInsets.all(AppSpacing.medium),
                    child: SizedBox(
                      width: double.infinity,
                      child: ElevatedButton(
                        onPressed: _selectedIndices.isNotEmpty
                            ? () => _onAddButtonPressed(ingredients)
                            : null,
                        child: const Text('Add to Shopping List'),
                      ),
                    ),
                  ),
                ],
              );
            },
          );
        },
      ),
    );
  }
}
