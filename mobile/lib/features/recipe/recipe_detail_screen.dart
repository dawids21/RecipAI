import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:recipai_mobile/core/get_it.dart';
import 'package:recipai_mobile/core/routes.dart';
import 'package:wakelock_plus/wakelock_plus.dart';

import '../../core/theme.dart';
import '../../shared/api_error_widget.dart';
import '../../shared/loading_widget.dart';
import '../../shared/user_role.dart';
import '../planning/meal_entry_form_dialog.dart';
import '../planning/meal_entry_form_result.dart';
import '../planning/meal_plan_calendar_service.dart';
import '../planning/meal_plan_list_service.dart';
import 'collection/recipes_collection_list_service.dart';
import 'ingredient_bullet.dart';
import 'recipe.dart';
import 'recipe_detail.dart';
import 'recipe_detail_service.dart';
import 'recipe_image_carousel.dart';
import 'recipe_sharing_dialog.dart';
import 'source_link_widget.dart';
import 'step_number_badge.dart';

class RecipeDetailScreen extends StatefulWidget {
  final String recipeId;
  final RecipeDetailService recipeDetailService;
  final MealPlanCalendarService? mealPlanCalendarService;
  final MealPlanListService? mealPlanListService;
  final RecipesCollectionListService? recipesCollectionListService;

  const RecipeDetailScreen({
    super.key,
    required this.recipeId,
    required this.recipeDetailService,
    this.mealPlanCalendarService,
    this.mealPlanListService,
    this.recipesCollectionListService,
  });

  @override
  State<RecipeDetailScreen> createState() => _RecipeDetailScreenState();
}

class _RecipeDetailScreenState extends State<RecipeDetailScreen> {
  @override
  void initState() {
    super.initState();
    widget.recipeDetailService.loadRecipeDetail(widget.recipeId);
    widget.recipeDetailService.loadSharedUsers(widget.recipeId);
    WakelockPlus.enable();
  }

  @override
  void dispose() {
    WakelockPlus.disable();
    if (getIt.isRegistered<RecipeDetailService>()) {
      getIt.resetLazySingleton<RecipeDetailService>();
    }
    super.dispose();
  }

  Future<void> _navigateToEdit() async {
    context.goNamed(
      AppRoute.recipeEdit.name,
      pathParameters: {'id': widget.recipeId},
    );
  }

  Future<void> _showDeleteConfirmation() async {
    final shouldDelete = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Delete Recipe'),
        content: const Text(
          'Are you sure you want to delete this recipe? This action cannot be undone.',
        ),
        actions: [
          TextButton(
            onPressed: () => context.pop(false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => context.pop(true),
            style: TextButton.styleFrom(
              foregroundColor: Theme.of(context).colorScheme.error,
            ),
            child: const Text('Delete'),
          ),
        ],
      ),
    );

    if (shouldDelete == true) {
      await _deleteRecipe();
    }
  }

  Future<void> _deleteRecipe() async {
    try {
      await widget.recipeDetailService.deleteRecipe(widget.recipeId);

      if (mounted) {
        _showSnackBar('Recipe deleted successfully!');
        context.goNamed(AppRoute.main.name);
      }
    } catch (e) {
      if (mounted) {
        _showSnackBar('Failed to delete recipe: ${e.toString()}');
      }
    }
  }

  void _showSnackBar(String message) {
    if (mounted) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(message)));
    }
  }

  Future<void> _showAddToMealPlanDialog(RecipeDetail recipeDetail) async {
    final result = await showDialog<MealEntryFormResult>(
      context: context,
      builder: (context) => MealEntryFormDialog(
        mealPlanListService: widget.mealPlanListService!,
        recipesCollectionListService: widget.recipesCollectionListService!,
        preselectedRecipe: Recipe(
          id: recipeDetail.id,
          name: recipeDetail.name,
          thumbnailUrl: recipeDetail.images.isNotEmpty
              ? recipeDetail.images.first.thumbnailUrl
              : null,
        ),
        preselectedServingSize: recipeDetail.data.servingSize,
      ),
    );

    if (result == null || !mounted) return;

    try {
      await widget.mealPlanCalendarService!.createMealEntry(
        planId: result.planId,
        date: result.date,
        recipeId: result.recipeId,
        servingSize: result.servingSize,
      );

      if (!mounted) return;
      _showSnackBar('Added to meal plan');
    } catch (e) {
      if (!mounted) return;
      _showSnackBar(
        'Failed to add to meal plan: ${e.toString().replaceFirst('Exception: ', '')}',
      );
    }
  }

  String _formatIngredient(Ingredient ingredient) {
    final parts = <String>[];
    if (ingredient.quantity != null) {
      final q = ingredient.quantity!;
      parts.add(q % 1 == 0 ? q.toInt().toString() : q.toString());
    }
    if (ingredient.unit != null) parts.add(ingredient.unit!);
    parts.add(ingredient.name);
    final base = parts.join(' ');
    if (ingredient.comment != null) return '$base (${ingredient.comment})';
    return base;
  }

  Future<void> _showSharingDialog() async {
    await showDialog<void>(
      context: context,
      builder: (context) =>
          RecipeSharingDialog(recipeDetailService: widget.recipeDetailService),
    );
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
              title: const Text('Recipe Details'),
              backgroundColor: theme.colorScheme.inversePrimary,
            ),
            body: const SafeArea(top: false, child: LoadingWidget()),
          ),
          error: (error) => Scaffold(
            appBar: AppBar(
              title: const Text('Recipe Details'),
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
          data: (recipeDetail) {
            // Build menu items based on user role
            final menuItems = <PopupMenuItem<String>>[];

            menuItems.add(
              PopupMenuItem<String>(
                value: 'edit',
                child: Row(
                  children: [
                    Icon(Icons.edit),
                    const SizedBox(width: AppSpacing.small),
                    Text('Edit Recipe'),
                  ],
                ),
              ),
            );

            // Always show add to shopping list option
            menuItems.add(
              PopupMenuItem<String>(
                value: 'addToShoppingList',
                child: Row(
                  children: [
                    Icon(Icons.shopping_cart),
                    const SizedBox(width: AppSpacing.small),
                    Text('Add to Shopping List'),
                  ],
                ),
              ),
            );

            // Always show share option
            menuItems.add(
              PopupMenuItem<String>(
                value: 'share',
                child: Row(
                  children: [
                    Icon(Icons.share),
                    const SizedBox(width: AppSpacing.small),
                    Text('Share Recipe'),
                  ],
                ),
              ),
            );

            // Only show delete for OWNER
            if (recipeDetail.role == UserRole.owner) {
              menuItems.add(
                PopupMenuItem<String>(
                  value: 'delete',
                  child: Row(
                    children: [
                      Icon(Icons.delete),
                      const SizedBox(width: AppSpacing.small),
                      Text('Delete Recipe'),
                    ],
                  ),
                ),
              );
            }

            return Scaffold(
              appBar: AppBar(
                title: const Text('Recipe Details'),
                backgroundColor: theme.colorScheme.inversePrimary,
                actions: [
                  PopupMenuButton<String>(
                    onSelected: (value) {
                      if (value == 'edit') {
                        _navigateToEdit();
                      } else if (value == 'addToShoppingList') {
                        context.goNamed(
                          AppRoute.recipeToShoppingList.name,
                          pathParameters: {'id': widget.recipeId},
                        );
                      } else if (value == 'delete') {
                        _showDeleteConfirmation();
                      } else if (value == 'share') {
                        _showSharingDialog();
                      }
                    },
                    itemBuilder: (BuildContext context) => menuItems,
                  ),
                ],
              ),
              floatingActionButton: FloatingActionButton(
                onPressed: () => _showAddToMealPlanDialog(recipeDetail),
                tooltip: 'Add to Meal Plan',
                child: const Icon(Icons.calendar_today),
              ),
              body: SafeArea(
                top: false,
                child: SingleChildScrollView(
                  padding: AppSpacing.screenPadding,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      if (recipeDetail.images.isNotEmpty) ...[
                        RecipeImageCarousel(images: recipeDetail.images),
                        const SizedBox(height: AppSpacing.large),
                      ],

                      // Recipe Name
                      Text(
                        recipeDetail.name,
                        style: theme.textTheme.headlineMedium?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                      ),

                      // Collection info (conditional rendering)
                      if (recipeDetail.collectionName != null) ...[
                        const SizedBox(height: AppSpacing.small),
                        Row(
                          spacing: AppSpacing.extraSmall,
                          children: [
                            Icon(
                              Icons.folder,
                              size: 16,
                              color: theme.colorScheme.primary,
                            ),
                            Text(
                              'Collection:',
                              style: theme.textTheme.bodyMedium,
                            ),
                            Text(
                              '${recipeDetail.collectionName}',
                              style: theme.textTheme.bodyMedium?.copyWith(
                                color: theme.colorScheme.primary,
                              ),
                            ),
                          ],
                        ),
                      ],

                      if (recipeDetail.data.sourceUrl != null &&
                          recipeDetail.data.sourceUrl!.isNotEmpty) ...[
                        const SizedBox(height: AppSpacing.small),
                        Row(
                          spacing: AppSpacing.extraSmall,
                          children: [
                            Icon(
                              Icons.link,
                              size: 16,
                              color: theme.colorScheme.primary,
                            ),
                            Text('Source:', style: theme.textTheme.bodyMedium),
                            SourceLinkWidget(
                              sourceUrl: recipeDetail.data.sourceUrl!,
                            ),
                          ],
                        ),
                      ],

                      const SizedBox(height: AppSpacing.small),
                      Row(
                        spacing: AppSpacing.extraSmall,
                        children: [
                          Icon(
                            Icons.restaurant,
                            size: 16,
                            color: theme.colorScheme.primary,
                          ),
                          Text('Servings:', style: theme.textTheme.bodyMedium),
                          Text(
                            '${recipeDetail.data.servingSize}',
                            style: theme.textTheme.bodyMedium?.copyWith(
                              color: theme.colorScheme.primary,
                            ),
                          ),
                        ],
                      ),

                      const SizedBox(height: AppSpacing.large),

                      // Ingredients Section
                      Text(
                        'Ingredients',
                        style: theme.textTheme.headlineSmall?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: AppSpacing.small),
                      Card(
                        child: Padding(
                          padding: AppSpacing.cardMargin,
                          child: Column(
                            children: recipeDetail.data.ingredients.map((
                              ingredient,
                            ) {
                              return Padding(
                                padding: AppSpacing.smallVertical,
                                child: Row(
                                  children: [
                                    const IngredientBullet(),
                                    const SizedBox(width: AppSpacing.small),
                                    Expanded(
                                      child: Text(
                                        _formatIngredient(ingredient),
                                        style: theme.textTheme.bodyLarge,
                                      ),
                                    ),
                                  ],
                                ),
                              );
                            }).toList(),
                          ),
                        ),
                      ),
                      const SizedBox(height: AppSpacing.large),

                      // Instructions Section
                      Text(
                        'Instructions',
                        style: theme.textTheme.headlineSmall?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: AppSpacing.small),
                      Card(
                        child: Padding(
                          padding: AppSpacing.screenPadding,
                          child: Column(
                            children: recipeDetail.data.instructions
                                .asMap()
                                .entries
                                .map((entry) {
                                  int index = entry.key;
                                  Instruction instruction = entry.value;
                                  return Padding(
                                    padding: AppSpacing.mediumVertical,
                                    child: Row(
                                      crossAxisAlignment:
                                          CrossAxisAlignment.start,
                                      children: [
                                        StepNumberBadge(stepNumber: index + 1),
                                        const SizedBox(
                                          width:
                                              AppSpacing.small +
                                              AppSpacing.extraSmall,
                                        ),
                                        Expanded(
                                          child: Text(
                                            instruction.step,
                                            style: theme.textTheme.bodyLarge,
                                          ),
                                        ),
                                      ],
                                    ),
                                  );
                                })
                                .toList(),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            );
          },
        );
      },
    );
  }
}
