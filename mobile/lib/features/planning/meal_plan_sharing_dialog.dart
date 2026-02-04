import 'package:flutter/material.dart';

import '../../core/widgets/sharing_dialog.dart';
import 'meal_plan_sharing_service.dart';

class MealPlanSharingDialog extends StatefulWidget {
  final MealPlanSharingService mealPlanSharingService;

  const MealPlanSharingDialog({
    super.key,
    required this.mealPlanSharingService,
  });

  @override
  State<MealPlanSharingDialog> createState() => _MealPlanSharingDialogState();
}

class _MealPlanSharingDialogState extends State<MealPlanSharingDialog> {
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
      title: 'Share Meal Plan',
      sharedUsers: widget.mealPlanSharingService.sharedUsers,
      onShare: (email) async {
        try {
          await widget.mealPlanSharingService.shareMealPlan(email);
          _showSnackBar('Meal plan shared successfully!');
        } catch (e) {
          _showSnackBar('Failed to share meal plan: ${e.toString()}');
          rethrow;
        }
      },
      onUnshare: (email) async {
        try {
          await widget.mealPlanSharingService.unshareMealPlan(email);
          _showSnackBar('Meal plan unshared successfully!');
        } catch (e) {
          _showSnackBar('Failed to unshare meal plan: ${e.toString()}');
          rethrow;
        }
      },
    );
  }
}
