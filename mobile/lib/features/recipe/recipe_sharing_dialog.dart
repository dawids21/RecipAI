import 'package:flutter/material.dart';

import '../sharing/share_refused_exception.dart';
import '../sharing/sharing_dialog.dart';
import 'recipe_detail_service.dart';

class RecipeSharingDialog extends StatefulWidget {
  final RecipeDetailService recipeDetailService;
  final String currentUserEmail;

  const RecipeSharingDialog({
    super.key,
    required this.recipeDetailService,
    required this.currentUserEmail,
  });

  @override
  State<RecipeSharingDialog> createState() => _RecipeSharingDialogState();
}

class _RecipeSharingDialogState extends State<RecipeSharingDialog> {
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
      title: 'Share Recipe',
      permissions: widget.recipeDetailService.permissions,
      currentUserEmail: widget.currentUserEmail,
      onShare: (email) async {
        try {
          await widget.recipeDetailService.shareRecipe(email);
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
          _showSnackBar('Failed to share recipe: ${e.toString()}');
          rethrow;
        }
      },
      onUnshare: (email) async {
        try {
          await widget.recipeDetailService.unshareRecipe(email);
          _showSnackBar('Recipe unshared successfully!');
        } catch (e) {
          _showSnackBar('Failed to unshare recipe: ${e.toString()}');
          rethrow;
        }
      },
    );
  }
}
