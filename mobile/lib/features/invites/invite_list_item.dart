import 'package:flutter/material.dart';

import '../../core/theme.dart';
import 'invite.dart';
import 'invite_resource_type.dart';

class InviteListItem extends StatelessWidget {
  static const double _busyOpacity = 0.5;

  final Invite invite;
  final bool busy;
  final VoidCallback onAccept;
  final VoidCallback onDecline;

  const InviteListItem({
    super.key,
    required this.invite,
    required this.busy,
    required this.onAccept,
    required this.onDecline,
  });

  IconData get _icon {
    switch (invite.resourceType) {
      case InviteResourceType.recipe:
        return Icons.restaurant_menu;
      case InviteResourceType.recipesCollection:
        return Icons.folder;
      case InviteResourceType.shoppingList:
        return Icons.shopping_cart;
      case InviteResourceType.mealPlan:
        return Icons.calendar_today;
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Card(
      margin: AppSpacing.cardMargin,
      child: Opacity(
        opacity: busy ? _busyOpacity : 1,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              contentPadding: AppSpacing.listTilePadding,
              leading: Icon(_icon),
              title: Text(invite.label, style: theme.textTheme.titleMedium),
              subtitle: Text(
                '${invite.resourceType.displayName} · Shared by ${invite.invitedBy}',
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(
                horizontal: AppSpacing.medium,
              ).copyWith(bottom: AppSpacing.small),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  TextButton(
                    onPressed: busy ? null : onDecline,
                    child: const Text('Decline'),
                  ),
                  const SizedBox(width: AppSpacing.small),
                  FilledButton(
                    onPressed: busy ? null : onAccept,
                    child: const Text('Accept'),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
