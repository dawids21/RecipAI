import 'package:flutter/material.dart';

import '../../shared/api_error_widget.dart';
import '../../shared/loading_widget.dart';
import 'collection/recipes_collection_list_service.dart';
import 'recipe.dart';
import 'recipe_filter_bar.dart';
import 'recipe_list_item.dart';
import 'recipe_list_service.dart';
import 'recipe_search_bar.dart';

class RecipeList extends StatefulWidget {
  final RecipeListService recipeListService;
  final RecipesCollectionListService recipesCollectionListService;
  final void Function(BuildContext, Recipe) onRecipeTap;

  const RecipeList({
    super.key,
    required this.recipeListService,
    required this.recipesCollectionListService,
    required this.onRecipeTap,
  });

  @override
  State<RecipeList> createState() => _RecipeListState();
}

class _RecipeListState extends State<RecipeList> {
  String _searchQuery = '';

  void _onRecipeTap(BuildContext context, Recipe recipe) {
    widget.onRecipeTap(context, recipe);
  }

  void _handleSearchChanged(String query) {
    setState(() {
      _searchQuery = query;
    });
  }

  Future<void> _handleRefresh() async {
    await widget.recipeListService.loadRecipes();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Column(
      children: [
        RecipeFilterBar(
          collections: widget.recipesCollectionListService.recipesCollections,
          selectedCollectionId: widget.recipeListService.selectedCollectionId,
          onFilterChanged: (id) => widget.recipeListService.setFilter(id),
        ),
        RecipeSearchBar(
          searchQuery: _searchQuery,
          onSearchChanged: _handleSearchChanged,
        ),
        Expanded(
          child: RefreshIndicator(
            onRefresh: _handleRefresh,
            child: ValueListenableBuilder(
              valueListenable: widget.recipeListService.recipes,
              builder: (context, asyncValueRecipes, child) {
                final filteredRecipes = widget.recipeListService
                    .getFilteredRecipes(_searchQuery);

                return filteredRecipes.when(
                  loading: () => const LoadingWidget(),
                  data: (recipes) {
                    if (recipes.isEmpty) {
                      return Center(
                        child: Text(
                          'No recipes found',
                          style: theme.textTheme.labelMedium,
                        ),
                      );
                    }
                    return ListView.builder(
                      itemCount: recipes.length,
                      itemBuilder: (context, index) {
                        return RecipeListItem(
                          recipe: recipes[index],
                          onTap: () => _onRecipeTap(context, recipes[index]),
                        );
                      },
                    );
                  },
                  error: (error) => ApiErrorWidget(
                    errorMessage: 'Error: $error',
                    onRetry: () {
                      widget.recipeListService.loadRecipes();
                    },
                  ),
                );
              },
            ),
          ),
        ),
      ],
    );
  }
}
