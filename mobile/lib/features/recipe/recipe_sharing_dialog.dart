import 'package:flutter/material.dart';

import '../../core/widgets/sharing_dialog.dart';
import 'recipe_detail_service.dart';

class RecipeSharingDialog extends StatefulWidget {
  final RecipeDetailService recipeDetailService;

  const RecipeSharingDialog({super.key, required this.recipeDetailService});

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
      sharedUsers: widget.recipeDetailService.sharedUsers,
      onShare: (email) async {
        try {
          await widget.recipeDetailService.shareRecipe(email);
          _showSnackBar('Recipe shared successfully!');
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
