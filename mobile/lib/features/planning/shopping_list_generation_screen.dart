import 'package:flutter/material.dart';

import '../../core/get_it.dart';
import '../../core/theme.dart';
import '../shopping_list/shopping_list_list_service.dart';
import '../shopping_list/shopping_list_sync_service.dart';
import 'meal_plan_list_service.dart';
import 'shopping_list_generation_calendar_service.dart';
import 'shopping_list_generation_review_step.dart';
import 'shopping_list_generation_select_dates_step.dart';
import 'shopping_list_generation_select_plan_step.dart';
import 'shopping_list_generation_service.dart';

class ShoppingListGenerationScreen extends StatefulWidget {
  final MealPlanListService mealPlanListService;
  final ShoppingListListService shoppingListListService;
  final ShoppingListSyncService shoppingListSyncService;
  final ShoppingListGenerationService generationService;
  final ShoppingListGenerationCalendarService calendarService;

  const ShoppingListGenerationScreen({
    super.key,
    required this.mealPlanListService,
    required this.shoppingListListService,
    required this.shoppingListSyncService,
    required this.generationService,
    required this.calendarService,
  });

  @override
  State<ShoppingListGenerationScreen> createState() =>
      _ShoppingListGenerationScreenState();
}

enum _ShoppingListGenerationStep { selectPlans, selectDates, reviewItems }

class _ShoppingListGenerationScreenState
    extends State<ShoppingListGenerationScreen> {
  final PageController _pageController = PageController();

  _ShoppingListGenerationStep _currentStep =
      _ShoppingListGenerationStep.selectPlans;
  final Set<String> _selectedPlanIds = {};
  final Set<DateTime> _selectedDates = {};

  @override
  void dispose() {
    _pageController.dispose();
    if (getIt.isRegistered<ShoppingListGenerationService>()) {
      getIt.resetLazySingleton<ShoppingListGenerationService>();
    }
    if (getIt.isRegistered<ShoppingListGenerationCalendarService>()) {
      getIt.resetLazySingleton<ShoppingListGenerationCalendarService>();
    }
    super.dispose();
  }

  void _goToStep(_ShoppingListGenerationStep step) {
    _pageController.animateToPage(
      step.index,
      duration: AppAnimations.sectionTransition,
      curve: AppAnimations.sectionCurve,
    );
    setState(() => _currentStep = step);
  }

  Future<void> _onNextFromSelectPlansStep() async {
    _goToStep(_ShoppingListGenerationStep.selectDates);
  }

  Future<void> _onGenerateFromSelectDatesStep() async {
    await widget.generationService.generateShoppingList(
      planIds: _selectedPlanIds.toList(),
      selectedDates: _selectedDates.toList(),
    );

    _goToStep(_ShoppingListGenerationStep.reviewItems);
  }

  void _onBack() {
    final steps = _ShoppingListGenerationStep.values;
    final currentIndex = _currentStep.index;
    if (currentIndex > 0) {
      _goToStep(steps[currentIndex - 1]);
    }
  }

  void _onDateToggled(DateTime date) {
    setState(() {
      if (_selectedDates.contains(date)) {
        _selectedDates.remove(date);
      } else {
        _selectedDates.add(date);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Generate Shopping List'),
        backgroundColor: theme.colorScheme.inversePrimary,
        leading: _currentStep != _ShoppingListGenerationStep.selectPlans
            ? IconButton(icon: const Icon(Icons.arrow_back), onPressed: _onBack)
            : null,
      ),
      body: SafeArea(
        top: false,
        child: Column(
          children: [
            _buildStepIndicator(theme),
            Expanded(
              child: PageView(
                controller: _pageController,
                physics: const NeverScrollableScrollPhysics(),
                children: [
                  ShoppingListGenerationSelectPlanStep(
                    mealPlanListService: widget.mealPlanListService,
                    selectedPlanIds: _selectedPlanIds,
                    onPlanToggled: (id, selected) {
                      setState(() {
                        if (selected) {
                          _selectedPlanIds.add(id);
                        } else {
                          _selectedPlanIds.remove(id);
                        }
                      });
                    },
                    onNext: _onNextFromSelectPlansStep,
                  ),
                  ShoppingListGenerationSelectDatesStep(
                    calendarService: widget.calendarService,
                    selectedPlanIds: _selectedPlanIds,
                    selectedDates: _selectedDates,
                    onDateToggled: _onDateToggled,
                    onGenerate: _onGenerateFromSelectDatesStep,
                  ),
                  ShoppingListGenerationReviewStep(
                    generationService: widget.generationService,
                    shoppingListListService: widget.shoppingListListService,
                    shoppingListSyncService: widget.shoppingListSyncService,
                    onRetry: _onGenerateFromSelectDatesStep,
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildStepIndicator(ThemeData theme) {
    const steps = ['Select Plans', 'Select Dates', 'Review Items'];

    return Padding(
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.medium,
        vertical: AppSpacing.small,
      ),
      child: Row(
        children: List.generate(steps.length, (i) {
          final isActive = i == _currentStep.index;
          final isCompleted = i < _currentStep.index;

          return Expanded(
            child: Row(
              children: [
                Expanded(
                  child: AnimatedContainer(
                    duration: AppAnimations.sectionTransition,
                    padding: const EdgeInsets.symmetric(
                      vertical: AppSpacing.extraSmall,
                      horizontal: AppSpacing.extraSmall,
                    ),
                    decoration: BoxDecoration(
                      color: isActive
                          ? theme.colorScheme.primary
                          : isCompleted
                          ? theme.colorScheme.primary.withValues(alpha: 0.3)
                          : theme.colorScheme.surfaceContainerHighest,
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: Text(
                      steps[i],
                      textAlign: TextAlign.center,
                      style: theme.textTheme.labelSmall?.copyWith(
                        color: isActive
                            ? theme.colorScheme.onPrimary
                            : theme.colorScheme.onSurfaceVariant,
                        fontWeight: isActive
                            ? FontWeight.bold
                            : FontWeight.normal,
                      ),
                    ),
                  ),
                ),
                if (i < steps.length - 1)
                  Container(
                    width: AppSpacing.small,
                    height: 2,
                    color: isCompleted
                        ? theme.colorScheme.primary.withValues(alpha: 0.5)
                        : theme.colorScheme.outlineVariant,
                  ),
              ],
            ),
          );
        }),
      ),
    );
  }
}
