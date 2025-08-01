import 'package:flutter/material.dart';

import '../../core/api_service.dart';
import '../../core/theme.dart';
import '../../shared/error_icon.dart';
import '../../shared/loading_widget.dart';
import 'ingredient_bullet.dart';
import 'recipe_detail.dart';
import 'step_number_badge.dart';

class RecipeDetailScreen extends StatefulWidget {
  final String recipeId;

  const RecipeDetailScreen({super.key, required this.recipeId});

  @override
  State<RecipeDetailScreen> createState() => _RecipeDetailScreenState();
}

class _RecipeDetailScreenState extends State<RecipeDetailScreen> {
  late Future<RecipeDetail> futureRecipeDetail;

  @override
  void initState() {
    super.initState();
    futureRecipeDetail = ApiService.fetchRecipeDetail(widget.recipeId);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(
        title: const Text('Recipe Details'),
        backgroundColor: theme.colorScheme.inversePrimary,
      ),
      body: FutureBuilder<RecipeDetail>(
        future: futureRecipeDetail,
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const LoadingWidget();
          } else if (snapshot.hasError) {
            return Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const ErrorIcon(),
                  const SizedBox(height: AppSpacing.medium),
                  Text(
                    'Error: ${snapshot.error}',
                    style: theme.textTheme.bodyLarge,
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: AppSpacing.medium),
                  ElevatedButton(
                    onPressed: () {
                      setState(() {
                        futureRecipeDetail = ApiService.fetchRecipeDetail(
                          widget.recipeId,
                        );
                      });
                    },
                    child: const Text('Retry'),
                  ),
                ],
              ),
            );
          } else if (snapshot.hasData) {
            final recipeDetail = snapshot.data!;
            return SingleChildScrollView(
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
                                    '${ingredient.quantity} ${ingredient.name}',
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
                                  crossAxisAlignment: CrossAxisAlignment.start,
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
            );
          } else {
            return const Center(child: Text('No data available'));
          }
        },
      ),
    );
  }
}
