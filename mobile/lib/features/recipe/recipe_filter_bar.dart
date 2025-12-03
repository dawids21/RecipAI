import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../../core/async_value.dart';
import '../../core/theme.dart';
import 'collection/recipes_collection.dart';
import 'recipe_list_service.dart';

class RecipeFilterBar extends StatelessWidget {
  final ValueListenable<AsyncValue<List<RecipesCollection>>> collections;
  final ValueListenable<String?> selectedCollectionId;
  final void Function(String?) onFilterChanged;

  const RecipeFilterBar({
    super.key,
    required this.collections,
    required this.selectedCollectionId,
    required this.onFilterChanged,
  });

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<AsyncValue<List<RecipesCollection>>>(
      valueListenable: collections,
      builder: (context, collectionsState, _) {
        return collectionsState.when(
          loading: () => const SizedBox(
            height: 56.0,
            child: Center(child: CircularProgressIndicator()),
          ),
          error: (error) => const SizedBox.shrink(),
          data: (collectionsList) {
            return ValueListenableBuilder<String?>(
              valueListenable: selectedCollectionId,
              builder: (context, filterValue, _) {
                return Container(
                  height: 56.0,
                  padding: const EdgeInsets.symmetric(
                    vertical: AppSpacing.extraSmall,
                  ),
                  child: SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    padding: const EdgeInsets.only(
                      left: AppSpacing.medium,
                      right: AppSpacing.small,
                    ),
                    child: Row(
                      children: [
                        Padding(
                          padding: const EdgeInsets.only(
                            right: AppSpacing.small,
                          ),
                          child: ChoiceChip(
                            label: const Text('All Recipes'),
                            selected: filterValue == null,
                            onSelected: (_) => onFilterChanged(null),
                          ),
                        ),
                        ...collectionsList.map(
                          (collection) => Padding(
                            padding: const EdgeInsets.only(
                              right: AppSpacing.small,
                            ),
                            child: ChoiceChip(
                              label: Text(collection.name),
                              selected: filterValue == collection.id,
                              onSelected: (_) => onFilterChanged(collection.id),
                            ),
                          ),
                        ),
                        Padding(
                          padding: const EdgeInsets.only(
                            right: AppSpacing.small,
                          ),
                          child: ChoiceChip(
                            label: const Text('Unassigned'),
                            selected:
                                filterValue ==
                                RecipeListService.unassignedFilterId,
                            onSelected: (_) => onFilterChanged(
                              RecipeListService.unassignedFilterId,
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
      },
    );
  }
}
