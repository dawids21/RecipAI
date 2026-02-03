import 'package:flutter/material.dart';
import 'package:recipai_mobile/shared/user_role.dart';

import '../../core/theme.dart';
import '../../shared/api_error_widget.dart';
import '../../shared/loading_widget.dart';
import 'meal_plan_list_service.dart';
import 'meal_plan_visibility_service.dart';
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
                                onEdit: () =>
                                    _handlePlaceholder(context, 'Edit'),
                                onShare: () =>
                                    _handlePlaceholder(context, 'Share'),
                                onDelete: plan.role == UserRole.owner
                                    ? () =>
                                          _handlePlaceholder(context, 'Delete')
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
              onPressed: () => _handlePlaceholder(context, 'Create plan'),
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

  void _handlePlaceholder(BuildContext context, String action) {
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text('$action feature coming soon')));
  }
}
