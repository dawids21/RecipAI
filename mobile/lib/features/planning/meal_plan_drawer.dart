import 'package:flutter/material.dart';
import 'package:recipai_mobile/shared/user_role.dart';

import '../../core/theme.dart';
import '../../shared/api_error_widget.dart';
import '../../shared/loading_widget.dart';
import 'meal_plan.dart';
import 'meal_plan_list_service.dart';
import 'meal_plan_visibility_service.dart';
import 'plan_form_dialog.dart';
import 'plan_list_tile.dart';

class MealPlanDrawer extends StatelessWidget {
  final MealPlanListService mealPlanListService;
  final MealPlanVisibilityService visibilityService;

  const MealPlanDrawer({
    super.key,
    required this.mealPlanListService,
    required this.visibilityService,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Drawer(
      child: Column(
        children: [
          DrawerHeader(
            decoration: BoxDecoration(color: theme.colorScheme.inversePrimary),
            child: const Align(
              alignment: Alignment.centerLeft,
              child: Text('My Meal Plans', style: TextStyle(fontSize: 24)),
            ),
          ),
          Expanded(
            child: RefreshIndicator(
              onRefresh: () => mealPlanListService.loadMealPlans(),
              child: ValueListenableBuilder(
                valueListenable: mealPlanListService.mealPlans,
                builder: (context, asyncValuePlans, child) {
                  return asyncValuePlans.when(
                    loading: () => const LoadingWidget(),
                    data: (plans) {
                      if (plans.isEmpty) {
                        return const Center(child: Text('No plans yet'));
                      }

                      return ValueListenableBuilder(
                        valueListenable: visibilityService.visibility,
                        builder: (context, visibilityMap, child) {
                          return ListView.builder(
                            itemCount: plans.length,
                            itemBuilder: (context, index) {
                              final plan = plans[index];
                              final isVisible = visibilityService.isVisible(
                                plan.id,
                              );

                              return PlanListTile(
                                plan: plan,
                                isVisible: isVisible,
                                onToggleVisibility: () {
                                  visibilityService.toggleVisibility(plan.id);
                                },
                                onEdit: () => _handleEditPlan(context, plan),
                                onShare: () =>
                                    _handlePlaceholder(context, 'Share'),
                                onDelete: plan.role == UserRole.owner
                                    ? () => _handleDeletePlan(context, plan)
                                    : null,
                              );
                            },
                          );
                        },
                      );
                    },
                    error: (error) => ApiErrorWidget(
                      errorMessage: 'Error: $error',
                      onRetry: mealPlanListService.loadMealPlans,
                    ),
                  );
                },
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(AppSpacing.medium),
            child: ElevatedButton.icon(
              onPressed: () => _handleCreatePlan(context),
              icon: const Icon(Icons.add),
              label: const Text('Create New Plan'),
              style: ElevatedButton.styleFrom(
                minimumSize: const Size(double.infinity, 48),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _handleCreatePlan(BuildContext context) async {
    final result = await showDialog<PlanFormResult>(
      context: context,
      builder: (context) => const PlanFormDialog(),
    );

    if (result == null || !context.mounted) return;

    try {
      await mealPlanListService.createMealPlan(
        name: result.name,
        color: result.color,
      );

      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Plan created successfully')),
      );
    } catch (e) {
      if (!context.mounted) return;
      final message = e.toString().contains('Plan limit exceeded')
          ? 'Cannot create plan: You have reached the maximum number of plans'
          : 'Failed to create plan: ${e.toString().replaceFirst('Exception: ', '')}';

      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(message)));
    }
  }

  Future<void> _handleEditPlan(BuildContext context, MealPlan plan) async {
    final result = await showDialog<PlanFormResult>(
      context: context,
      builder: (context) => PlanFormDialog(existingPlan: plan),
    );

    if (result == null || !context.mounted) return;

    try {
      await mealPlanListService.updateMealPlan(
        id: plan.id,
        name: result.name,
        color: result.color,
      );

      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Plan updated successfully')),
      );
    } catch (e) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Failed to update plan: ${e.toString().replaceFirst('Exception: ', '')}',
          ),
        ),
      );
    }
  }

  Future<void> _handleDeletePlan(BuildContext context, MealPlan plan) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Delete Plan'),
        content: Text(
          'Are you sure you want to delete "${plan.name}"? This will remove all scheduled meals in this plan.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );

    if (confirmed != true || !context.mounted) return;

    try {
      await mealPlanListService.deleteMealPlan(id: plan.id);

      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Plan deleted successfully')),
      );
    } catch (e) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Failed to delete plan: ${e.toString().replaceFirst('Exception: ', '')}',
          ),
        ),
      );
    }
  }

  void _handlePlaceholder(BuildContext context, String action) {
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text('$action feature coming soon')));
  }
}
