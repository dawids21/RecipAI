import 'package:flutter/material.dart';

import '../../core/theme.dart';
import '../../shared/loading_widget.dart';
import 'meal_plan_list_service.dart';

class ShoppingListGenerationSelectPlanStep extends StatelessWidget {
  final MealPlanListService mealPlanListService;
  final Set<String> selectedPlanIds;
  final void Function(String id, bool selected) onPlanToggled;
  final VoidCallback onNext;

  const ShoppingListGenerationSelectPlanStep({
    super.key,
    required this.mealPlanListService,
    required this.selectedPlanIds,
    required this.onPlanToggled,
    required this.onNext,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(
            AppSpacing.medium,
            AppSpacing.medium,
            AppSpacing.medium,
            AppSpacing.small,
          ),
          child: Text(
            'Select the meal plans to include',
            style: theme.textTheme.titleMedium,
          ),
        ),
        Expanded(
          child: ValueListenableBuilder(
            valueListenable: mealPlanListService.mealPlans,
            builder: (context, plansAsync, _) {
              return plansAsync.when(
                loading: () => const LoadingWidget(),
                error: (error) => Center(
                  child: Padding(
                    padding: AppSpacing.screenPadding,
                    child: Text(
                      'Failed to load plans: $error',
                      style: TextStyle(color: theme.colorScheme.error),
                    ),
                  ),
                ),
                data: (plans) {
                  if (plans.isEmpty) {
                    return Center(
                      child: Padding(
                        padding: AppSpacing.screenPadding,
                        child: Text(
                          'No meal plans available.',
                          style: theme.textTheme.bodyLarge,
                        ),
                      ),
                    );
                  }

                  return ListView.builder(
                    itemCount: plans.length,
                    itemBuilder: (context, index) {
                      final plan = plans[index];
                      final isSelected = selectedPlanIds.contains(plan.id);

                      return CheckboxListTile(
                        value: isSelected,
                        onChanged: (checked) =>
                            onPlanToggled(plan.id, checked == true),
                        title: Text(plan.name),
                        secondary: CircleAvatar(
                          backgroundColor: plan.color,
                          radius: 10,
                        ),
                        controlAffinity: ListTileControlAffinity.trailing,
                      );
                    },
                  );
                },
              );
            },
          ),
        ),
        Padding(
          padding: AppSpacing.screenPadding,
          child: ElevatedButton(
            onPressed: selectedPlanIds.isNotEmpty ? onNext : null,
            child: const Text('Next'),
          ),
        ),
      ],
    );
  }
}
