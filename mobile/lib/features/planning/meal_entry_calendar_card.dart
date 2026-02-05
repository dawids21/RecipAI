import 'package:flutter/material.dart';

import '../../core/theme.dart';
import 'meal_plan_calendar_entry.dart';

class MealEntryCalendarCard extends StatelessWidget {
  final MealPlanCalendarEntry entry;
  final VoidCallback onTap;
  final VoidCallback onEdit;
  final VoidCallback onDelete;

  const MealEntryCalendarCard({
    super.key,
    required this.entry,
    required this.onTap,
    required this.onEdit,
    required this.onDelete,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    final textColor = entry.planColor.computeLuminance() > 0.5
        ? Colors.black
        : Colors.white;

    return Card(
      margin: AppSpacing.cardMargin,
      color: entry.planColor,
      child: ListTile(
        contentPadding: AppSpacing.listTilePadding,

        title: Text(
          entry.displayText,
          style: theme.textTheme.bodyLarge?.copyWith(color: textColor),
        ),

        subtitle: entry.servingSize != null && entry.isRecipeEntry
            ? Text(
                '${entry.servingSize} servings',
                style: theme.textTheme.bodySmall?.copyWith(
                  color: textColor.withValues(alpha: 0.7),
                ),
              )
            : null,

        trailing: PopupMenuButton<String>(
          icon: Icon(Icons.more_vert, color: textColor),
          itemBuilder: (context) => [
            const PopupMenuItem(
              value: 'edit',
              child: Row(
                children: [
                  Icon(Icons.edit),
                  SizedBox(width: AppSpacing.small),
                  Text('Edit'),
                ],
              ),
            ),
            const PopupMenuItem(
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
          onSelected: (value) {
            if (value == 'edit') {
              onEdit();
            } else if (value == 'delete') {
              onDelete();
            }
          },
        ),

        onTap: onTap,
      ),
    );
  }
}
