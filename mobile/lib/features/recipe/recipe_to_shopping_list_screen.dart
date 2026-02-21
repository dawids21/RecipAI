import 'package:flutter/material.dart';

import '../../shared/api_error_widget.dart';
import '../../shared/loading_widget.dart';
import '../shopping_list/shopping_list_list_service.dart';
import '../shopping_list/shopping_list_review_item.dart';
import '../shopping_list/shopping_list_review_widget.dart';
import '../shopping_list/shopping_list_sync_service.dart';
import 'recipe_detail_service.dart';

class RecipeToShoppingListScreen extends StatelessWidget {
  final String recipeId;
  final RecipeDetailService recipeDetailService;
  final ShoppingListListService shoppingListListService;
  final ShoppingListSyncService shoppingListSyncService;

  const RecipeToShoppingListScreen({
    required this.recipeId,
    required this.recipeDetailService,
    required this.shoppingListListService,
    required this.shoppingListSyncService,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Add to Shopping List'),
        backgroundColor: theme.colorScheme.inversePrimary,
      ),
      body: SafeArea(
        top: false,
        child: ValueListenableBuilder(
          valueListenable: recipeDetailService.recipeDetail,
          builder: (context, recipeAsync, _) {
            return recipeAsync.when(
              loading: () => const LoadingWidget(),
              error: (error) => ApiErrorWidget(
                errorMessage: 'Error: $error',
                onRetry: () {
                  recipeDetailService.loadRecipeDetail(recipeId);
                },
              ),
              data: (recipeDetail) {
                final ingredients = recipeDetail.data.ingredients;

                if (ingredients.isEmpty) {
                  return Center(
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: Text(
                        'This recipe has no ingredients to add.',
                        style: theme.textTheme.bodyLarge,
                        textAlign: TextAlign.center,
                      ),
                    ),
                  );
                }

                final reviewItems = ingredients
                    .map(ShoppingListGeneratedItem.fromIngredient)
                    .toList();

                return Column(
                  children: [
                    Expanded(
                      child: ShoppingListReviewWidget(
                        items: reviewItems,
                        shoppingListListService: shoppingListListService,
                        shoppingListSyncService: shoppingListSyncService,
                      ),
                    ),
                  ],
                );
              },
            );
          },
        ),
      ),
    );
  }
}
