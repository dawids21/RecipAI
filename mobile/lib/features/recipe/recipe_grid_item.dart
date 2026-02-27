import 'package:flutter/material.dart';

import 'recipe.dart';

class RecipeGridItem extends StatelessWidget {
  final Recipe recipe;
  final VoidCallback onTap;

  const RecipeGridItem({super.key, required this.recipe, required this.onTap});

  Widget _buildImage() {
    const double borderRadius = 8.0;

    if (recipe.thumbnailUrl == null || recipe.thumbnailUrl!.isEmpty) {
      return Container(
        color: Colors.grey.shade200,
        child: Icon(
          Icons.restaurant_menu,
          color: Colors.grey.shade500,
          size: 48,
        ),
      );
    }

    return Image.network(
      recipe.thumbnailUrl!,
      fit: BoxFit.cover,
      loadingBuilder: (context, child, loadingProgress) {
        if (loadingProgress == null) return child;
        return Container(
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
          decoration: BoxDecoration(
            color: Colors.grey.shade200,
            borderRadius: BorderRadius.circular(borderRadius),
          ),
          child: Icon(
            Icons.broken_image,
            color: Colors.grey.shade500,
            size: 48,
          ),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Expanded(child: _buildImage()),
            Padding(
              padding: const EdgeInsets.all(8.0),
              child: Text(
                recipe.name,
                style: theme.textTheme.titleSmall,
                maxLines: 3,
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
