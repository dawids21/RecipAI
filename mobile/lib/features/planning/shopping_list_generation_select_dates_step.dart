import 'package:flutter/material.dart';

import '../../core/theme.dart';
import 'month_calendar_widget.dart';
import 'shopping_list_generation_calendar_service.dart';

class ShoppingListGenerationSelectDatesStep extends StatefulWidget {
  final ShoppingListGenerationCalendarService calendarService;
  final Set<String> selectedPlanIds;
  final Set<DateTime> selectedDates;
  final void Function(DateTime date) onDateToggled;
  final VoidCallback onGenerate;

  const ShoppingListGenerationSelectDatesStep({
    super.key,
    required this.calendarService,
    required this.selectedPlanIds,
    required this.selectedDates,
    required this.onDateToggled,
    required this.onGenerate,
  });

  @override
  State<ShoppingListGenerationSelectDatesStep> createState() =>
      _ShoppingListGenerationSelectDatesStepState();
}

class _ShoppingListGenerationSelectDatesStepState
    extends State<ShoppingListGenerationSelectDatesStep> {
  DateTime _displayMonth = DateTime(DateTime.now().year, DateTime.now().month);

  Future<void> _loadCalendarForDisplayMonth() async {
    await widget.calendarService.loadCalendar(
      startDate: DateTime(_displayMonth.year, _displayMonth.month, 1),
      endDate: DateTime(_displayMonth.year, _displayMonth.month + 1, 0),
      planIds: widget.selectedPlanIds.toList(),
    );
  }

  Future<void> _onPreviousMonth() async {
    setState(() {
      _displayMonth = DateTime(_displayMonth.year, _displayMonth.month - 1);
    });
    await _loadCalendarForDisplayMonth();
  }

  Future<void> _onNextMonth() async {
    setState(() {
      _displayMonth = DateTime(_displayMonth.year, _displayMonth.month + 1);
    });
    await _loadCalendarForDisplayMonth();
  }

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
            'Select the dates to include',
            style: theme.textTheme.titleMedium,
          ),
        ),
        Expanded(
          child: SingleChildScrollView(
            padding: AppSpacing.screenPadding,
            child: ValueListenableBuilder(
              valueListenable: widget.calendarService.calendarData,
              builder: (context, calendarAsync, _) {
                final calendarData = calendarAsync.valueOrNull;

                return MonthCalendarWidget(
                  displayMonth: _displayMonth,
                  selectedDates: widget.selectedDates,
                  calendarData: calendarData,
                  onDateToggled: widget.onDateToggled,
                  onPreviousMonth: _onPreviousMonth,
                  onNextMonth: _onNextMonth,
                );
              },
            ),
          ),
        ),
        if (widget.selectedDates.isNotEmpty)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: AppSpacing.medium),
            child: Text(
              '${widget.selectedDates.length} date(s) selected',
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.primary,
              ),
              textAlign: TextAlign.center,
            ),
          ),
        Padding(
          padding: AppSpacing.screenPadding,
          child: ElevatedButton(
            onPressed: widget.selectedDates.isNotEmpty
                ? widget.onGenerate
                : null,
            child: const Text('Generate Shopping List'),
          ),
        ),
      ],
    );
  }
}
