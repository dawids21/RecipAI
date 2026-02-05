import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:recipai_mobile/features/planning/meal_entry_form_dialog.dart';
import 'package:recipai_mobile/features/planning/meal_entry_form_result.dart';
import 'package:recipai_mobile/features/planning/meal_plan_list_service.dart';
import 'package:recipai_mobile/features/recipe/collection/recipes_collection_list_service.dart';
import 'package:recipai_mobile/features/recipe/recipe_list_service.dart';

import '../../core/routes.dart';
import '../../shared/api_error_widget.dart';
import '../../shared/loading_widget.dart';
import 'day_section.dart';
import 'meal_plan_calendar_entry.dart';
import 'meal_plan_calendar_service.dart';
import 'week_strip.dart';

class MealPlanCalendarScreen extends StatefulWidget {
  final MealPlanCalendarService calendarService;
  final MealPlanListService mealPlanListService;
  final RecipeListService recipeListService;
  final RecipesCollectionListService recipesCollectionListService;

  const MealPlanCalendarScreen({
    super.key,
    required this.calendarService,
    required this.mealPlanListService,
    required this.recipeListService,
    required this.recipesCollectionListService,
  });

  @override
  State<MealPlanCalendarScreen> createState() => _MealPlanCalendarScreenState();
}

class _MealPlanCalendarScreenState extends State<MealPlanCalendarScreen> {
  @override
  void initState() {
    super.initState();
  }

  Future<void> _handleEditEntry(MealPlanCalendarEntry entry) async {
    final result = await showDialog<MealEntryFormResult>(
      context: context,
      builder: (context) => MealEntryFormDialog(
        mealPlanListService: widget.mealPlanListService,
        existingEntry: entry,
        recipeListService: widget.recipeListService,
        recipesCollectionListService: widget.recipesCollectionListService,
      ),
    );

    if (result == null || !mounted) return;

    try {
      await widget.calendarService.updateMealEntry(
        planId: result.planId,
        entryId: entry.id,
        date: result.date,
        recipeId: result.recipeId,
        placeholderText: result.placeholderText,
        servingSize: result.servingSize,
      );

      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Meal entry updated')));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Failed to update entry: ${e.toString().replaceFirst('Exception: ', '')}',
          ),
        ),
      );
    }
  }

  Future<void> _handleDeleteEntry(MealPlanCalendarEntry entry) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Delete Meal Entry'),
        content: Text(
          'Are you sure you want to delete "${entry.displayText}"?',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );

    if (confirmed != true || !mounted) return;

    try {
      await widget.calendarService.deleteMealEntry(
        planId: entry.planId,
        entryId: entry.id,
      );

      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Meal entry deleted')));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Failed to delete entry: ${e.toString().replaceFirst('Exception: ', '')}',
          ),
        ),
      );
    }
  }

  void _onEntryTap(MealPlanCalendarEntry entry) {
    if (entry.isRecipeEntry && entry.hasRecipeAccess) {
      context.goNamed(
        AppRoute.recipeDetail.name,
        pathParameters: {'id': entry.recipeId!},
      );
    } else if (entry.isRecipeEntry && !entry.hasRecipeAccess) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Recipe details not shared')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: () => widget.calendarService.loadCalendar(),
      child: Column(
        children: [
          WeekStrip(calendarService: widget.calendarService),
          Expanded(
            child: ValueListenableBuilder(
              valueListenable: widget.calendarService.calendarData,
              builder: (context, asyncValue, child) {
                return asyncValue.when(
                  loading: () => const LoadingWidget(),
                  error: (error) => ApiErrorWidget(
                    errorMessage: 'Failed to load calendar',
                    onRetry: () => widget.calendarService.loadCalendar(),
                  ),
                  data: (calendarData) {
                    return ValueListenableBuilder(
                      valueListenable: widget.calendarService.currentWeekStart,
                      builder: (context, weekStart, child) {
                        return ListView.builder(
                          itemCount: 7,
                          itemBuilder: (context, index) {
                            final date = weekStart.add(Duration(days: index));
                            final entries = calendarData.getEntriesForDate(
                              date,
                            );
                            return DaySection(
                              date: date,
                              entries: entries,
                              onEntryTap: _onEntryTap,
                              onEntryEdit: _handleEditEntry,
                              onEntryDelete: _handleDeleteEntry,
                            );
                          },
                        );
                      },
                    );
                  },
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
