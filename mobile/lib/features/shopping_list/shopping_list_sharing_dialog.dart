import 'package:flutter/material.dart';

import '../../core/widgets/sharing_dialog.dart';
import 'shopping_list_detail_service.dart';

class ShoppingListSharingDialog extends StatefulWidget {
  final ShoppingListDetailService shoppingListDetailService;

  const ShoppingListSharingDialog({
    super.key,
    required this.shoppingListDetailService,
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
      sharedUsers: widget.shoppingListDetailService.sharedUsers,
      onShare: (email) async {
        try {
          await widget.shoppingListDetailService.shareShoppingList(email);
          _showSnackBar('Shopping list shared successfully!');
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
