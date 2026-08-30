import 'package:flutter/material.dart';

import '../sharing/share_refused_exception.dart';
import '../sharing/sharing_dialog.dart';
import 'meal_plan_sharing_service.dart';

class MealPlanSharingDialog extends StatefulWidget {
  final MealPlanSharingService mealPlanSharingService;
  final String currentUserEmail;

  const MealPlanSharingDialog({
    super.key,
    required this.mealPlanSharingService,
    required this.currentUserEmail,
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
      permissions: widget.mealPlanSharingService.permissions,
      currentUserEmail: widget.currentUserEmail,
      onShare: (email) async {
        try {
          await widget.mealPlanSharingService.shareMealPlan(email);
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
