import 'package:flutter/material.dart';

import '../sharing/share_refused_exception.dart';
import '../sharing/sharing_dialog.dart';
import 'shopping_list_detail_service.dart';

class ShoppingListSharingDialog extends StatefulWidget {
  final ShoppingListDetailService shoppingListDetailService;
  final String currentUserEmail;

  const ShoppingListSharingDialog({
    super.key,
    required this.shoppingListDetailService,
    required this.currentUserEmail,
  });

  @override
  State<ShoppingListSharingDialog> createState() =>
      _ShoppingListSharingDialogState();
}

class _ShoppingListSharingDialogState extends State<ShoppingListSharingDialog> {
  void _showSnackBar(String message) {
    if (mounted) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(message)));
    }
  }

  @override
  Widget build(BuildContext context) {
    return SharingDialog(
      title: 'Share Shopping List',
      permissions: widget.shoppingListDetailService.permissions,
      currentUserEmail: widget.currentUserEmail,
      onShare: (email) async {
        try {
          await widget.shoppingListDetailService.shareShoppingList(email);
          _showSnackBar('Invitation sent to $email');
        } on ShareRefusedException catch (e) {
          _showSnackBar(switch (e.reason) {
            ShareRefusedReason.alreadyInvited =>
              '${e.email} already has a pending invitation',
            ShareRefusedReason.alreadyHasAccess =>
              '${e.email} already has access',
          });
          rethrow;
        } catch (e) {
          _showSnackBar('Failed to share shopping list: ${e.toString()}');
          rethrow;
        }
      },
      onUnshare: (email) async {
        try {
          await widget.shoppingListDetailService.unshareShoppingList(email);
          _showSnackBar('Shopping list unshared successfully!');
        } catch (e) {
          _showSnackBar('Failed to unshare shopping list: ${e.toString()}');
          rethrow;
        }
      },
    );
  }
}
