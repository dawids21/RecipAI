import 'package:flutter/material.dart';

import '../../core/theme.dart';
import 'recipe.dart';

class RecipeListItem extends StatelessWidget {
  final Recipe recipe;
  final VoidCallback onTap;

  const RecipeListItem({super.key, required this.recipe, required this.onTap});

  Widget _buildThumbnail() {
    const double size = 56.0;
    const double borderRadius = 8.0;

    if (recipe.thumbnailUrl == null || recipe.thumbnailUrl!.isEmpty) {
      return Container(
        width: size,
        height: size,
        decoration: BoxDecoration(
          color: Colors.grey.shade200,
          borderRadius: BorderRadius.circular(borderRadius),
        ),
        child: Icon(
          Icons.restaurant_menu,
          color: Colors.grey.shade500,
          size: 32,
        ),
      );
    }

    return ClipRRect(
      borderRadius: BorderRadius.circular(borderRadius),
      child: Image.network(
        recipe.thumbnailUrl!,
        width: size,
        height: size,
        fit: BoxFit.cover,
        loadingBuilder: (context, child, loadingProgress) {
          if (loadingProgress == null) return child;
          return Container(
            width: size,
            height: size,
            color: Colors.grey.shade200,
            child: Center(
              child: CircularProgressIndicator(
                strokeWidth: 2,
                value: loadingProgress.expectedTotalBytes != null
                    ? loadingProgress.cumulativeBytesLoaded /
                          loadingProgress.expectedTotalBytes!
                    : null,
              ),
            ),
          );
        },
        errorBuilder: (context, error, stackTrace) {
          return Container(
            width: size,
            height: size,
            decoration: BoxDecoration(
              color: Colors.grey.shade200,
              borderRadius: BorderRadius.circular(borderRadius),
            ),
            child: Icon(
              Icons.broken_image,
              color: Colors.grey.shade500,
              size: 32,
            ),
          );
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      margin: AppSpacing.cardMargin,
      child: ListTile(
        leading: _buildThumbnail(),
        title: Text(recipe.name, style: theme.textTheme.titleMedium),
        trailing: const Icon(Icons.arrow_forward_ios),
        onTap: onTap,
        contentPadding: AppSpacing.listTilePadding,
      ),
    );
  }
}
