import 'package:flutter/material.dart';

import '../../core/theme.dart';
import 'recipe.dart';

class RecipeListItem extends StatelessWidget {
  final Recipe recipe;
  final VoidCallback onTap;

  const RecipeListItem({super.key, required this.recipe, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      margin: AppSpacing.cardMargin,
      child: ListTile(
        title: Text(recipe.name, style: theme.textTheme.titleMedium),
        trailing: const Icon(Icons.arrow_forward_ios),
        onTap: onTap,
        contentPadding: AppSpacing.listTilePadding,
      ),
    );
  }
}
