import 'package:flutter/material.dart';

import '../../core/theme.dart';
import '../../shared/user_role.dart';
import 'meal_plan.dart';

class PlanListTile extends StatelessWidget {
  final MealPlan plan;
  final bool isVisible;
  final VoidCallback onToggleVisibility;
  final VoidCallback onEdit;
  final VoidCallback onShare;
  final VoidCallback? onDelete;

  const PlanListTile({
    super.key,
    required this.plan,
    required this.isVisible,
    required this.onToggleVisibility,
    required this.onEdit,
    required this.onShare,
    this.onDelete,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Card(
      margin: AppSpacing.cardMargin,
      child: ListTile(
        contentPadding: AppSpacing.listTilePadding,
        leading: CircleAvatar(backgroundColor: plan.color, radius: 12),
        title: Text(plan.name, style: theme.textTheme.titleMedium),
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Checkbox(value: isVisible, onChanged: (_) => onToggleVisibility()),
            PopupMenuButton<String>(
              onSelected: (value) {
                if (value == 'edit') {
                  onEdit();
                } else if (value == 'share') {
                  onShare();
                } else if (value == 'delete') {
                  onDelete?.call();
                }
              },
              itemBuilder: (context) {
                final items = <PopupMenuItem<String>>[
                  const PopupMenuItem<String>(
                    value: 'edit',
                    child: Row(
                      children: [
                        Icon(Icons.edit),
                        SizedBox(width: AppSpacing.small),
                        Text('Edit'),
                      ],
                    ),
                  ),
                  const PopupMenuItem<String>(
                    value: 'share',
                    child: Row(
                      children: [
                        Icon(Icons.share),
                        SizedBox(width: AppSpacing.small),
                        Text('Share'),
                      ],
                    ),
                  ),
                ];

                // Only show delete for owners
                if (plan.role == UserRole.owner) {
                  items.add(
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
                  );
                }

                return items;
              },
            ),
          ],
        ),
      ),
    );
  }
}
