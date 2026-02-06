import 'package:flutter/material.dart';
import 'package:recipai_mobile/features/planning/meal_entry_form_dialog.dart';
import 'package:recipai_mobile/features/planning/meal_entry_form_result.dart';
import 'package:recipai_mobile/features/planning/meal_plan_calendar_service.dart';
import 'package:recipai_mobile/features/planning/meal_plan_list_service.dart';
import 'package:recipai_mobile/features/recipe/collection/recipes_collection_list_service.dart';

class MealPlanCalendarFab extends StatefulWidget {
  final MealPlanCalendarService calendarService;
  final MealPlanListService mealPlanListService;
  final RecipesCollectionListService recipesCollectionListService;

  const MealPlanCalendarFab({
    super.key,
    required this.calendarService,
    required this.mealPlanListService,
    required this.recipesCollectionListService,
  });

  @override
  State<MealPlanCalendarFab> createState() => _MealPlanCalendarFabState();
}

class _MealPlanCalendarFabState extends State<MealPlanCalendarFab> {
  Future<void> _handleAddEntry(BuildContext context) async {
    final result = await showDialog<MealEntryFormResult>(
      context: context,
      builder: (context) => MealEntryFormDialog(
        mealPlanListService: widget.mealPlanListService,
        defaultDate: widget.calendarService.currentWeekStart.value,
        recipesCollectionListService: widget.recipesCollectionListService,
      ),
    );

    if (result == null || !context.mounted) return;

    try {
      await widget.calendarService.createMealEntry(
        planId: result.planId,
        date: result.date,
        recipeId: result.recipeId,
        placeholderText: result.placeholderText,
        servingSize: result.servingSize,
      );

      if (!context.mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Meal entry added')));
    } catch (e) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Failed to add entry: ${e.toString().replaceFirst('Exception: ', '')}',
          ),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return FloatingActionButton(
      onPressed: () => _handleAddEntry(context),
      tooltip: 'Add meal entry',
      child: const Icon(Icons.add),
    );
  }
}
