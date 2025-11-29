import 'package:flutter/material.dart';

import '../../../core/theme.dart';
import 'recipes_collection.dart';
import 'recipes_collection_rename_dialog.dart';

class RecipesCollectionListItem extends StatelessWidget {
  final RecipesCollection recipesCollection;
  final Function(String newName) onRename;
  final VoidCallback onDelete;

  const RecipesCollectionListItem({
    super.key,
    required this.recipesCollection,
    required this.onRename,
    required this.onDelete,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Card(
      margin: AppSpacing.cardMargin,
      child: ListTile(
        title: Text(recipesCollection.name, style: theme.textTheme.titleMedium),
        trailing: PopupMenuButton<String>(
          onSelected: (value) async {
            if (value == 'rename') {
              final newName = await showDialog<String>(
                context: context,
                builder: (context) => RecipesCollectionRenameDialog(
                  currentName: recipesCollection.name,
                ),
              );
              if (newName != null) {
                onRename(newName);
              }
            } else if (value == 'delete') {
              onDelete();
            }
          },
          itemBuilder: (context) => [
            const PopupMenuItem<String>(
              value: 'rename',
              child: Row(
                children: [
                  Icon(Icons.edit),
                  SizedBox(width: AppSpacing.small),
                  Text('Rename'),
                ],
              ),
            ),
            const PopupMenuItem<String>(
              value: 'delete',
              child: Row(
                children: [
                  Icon(Icons.delete),
                  SizedBox(width: AppSpacing.small),
                  Text('Delete'),
                ],
              ),
            ),
          ],
        ),
        contentPadding: AppSpacing.listTilePadding,
      ),
    );
  }
}
