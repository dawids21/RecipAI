import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:recipai_mobile/core/get_it.dart';
import 'package:recipai_mobile/core/routes.dart';

import '../../core/theme.dart';
import '../../shared/api_error_widget.dart';
import '../../shared/loading_widget.dart';
import '../../shared/user_role.dart';
import '../auth/auth_service.dart';
import 'ingredient_bullet.dart';
import 'recipe_detail.dart';
import 'recipe_detail_service.dart';
import 'recipe_sharing_dialog.dart';
import 'step_number_badge.dart';

class RecipeDetailScreen extends StatefulWidget {
  final String recipeId;
  final RecipeDetailService recipeDetailService;

  const RecipeDetailScreen({
    super.key,
    required this.recipeId,
    required this.recipeDetailService,
  });

  @override
  State<RecipeDetailScreen> createState() => _RecipeDetailScreenState();
}

class _RecipeDetailScreenState extends State<RecipeDetailScreen> {
  @override
  void initState() {
    super.initState();
    widget.recipeDetailService.loadRecipeDetail(widget.recipeId);
  }

  @override
  void dispose() {
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
        context.goNamed(AppRoute.recipes.name);
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

  Future<void> _showSharingDialog() async {
    await showDialog<void>(
      context: context,
      builder: (context) => RecipeSharingDialog(
        recipeDetailService: widget.recipeDetailService,
        authService: getIt<AuthService>(),
      ),
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
            body: const LoadingWidget(),
          ),
          error: (error) => Scaffold(
            appBar: AppBar(
              title: const Text('Recipe Details'),
              backgroundColor: theme.colorScheme.inversePrimary,
            ),
            body: ApiErrorWidget(
              errorMessage: 'Error: $error',
              onRetry: () {
                widget.recipeDetailService.loadRecipeDetail(widget.recipeId);
              },
            ),
          ),
          data: (recipeDetail) {
            // Build menu items based on user role
            final menuItems = <PopupMenuItem<String>>[];

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
                      if (value == 'delete') {
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
                onPressed: _navigateToEdit,
                child: const Icon(Icons.edit),
              ),
              body: SingleChildScrollView(
                padding: AppSpacing.screenPadding,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Recipe Name
                    Text(
                      recipeDetail.name,
                      style: theme.textTheme.headlineMedium?.copyWith(
                        fontWeight: FontWeight.bold,
                      ),
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
                                      '${ingredient.quantity}${ingredient.unit != null ? ' ${ingredient.unit}' : ''} ${ingredient.name}',
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
            );
          },
        );
      },
    );
  }
}
