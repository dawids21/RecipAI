import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../core/theme.dart';
import 'meal_plan_calendar_service.dart';

class WeekStrip extends StatelessWidget {
  final MealPlanCalendarService calendarService;

  const WeekStrip({super.key, required this.calendarService});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return ValueListenableBuilder(
      valueListenable: calendarService.currentWeekStart,
      builder: (context, weekStart, child) {
        final weekEnd = weekStart.add(const Duration(days: 6));
        final weekLabel = _formatWeekRange(context, weekStart, weekEnd);

        return Container(
          padding: AppSpacing.smallVertical,
          color: theme.colorScheme.surfaceContainer,
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              IconButton(
                icon: const Icon(Icons.chevron_left),
                onPressed: calendarService.goToPreviousWeek,
              ),
              TextButton(
                onPressed: calendarService.goToToday,
                child: Text(weekLabel, style: theme.textTheme.titleMedium),
              ),
              IconButton(
                icon: const Icon(Icons.chevron_right),
                onPressed: calendarService.goToNextWeek,
              ),
            ],
          ),
        );
      },
    );
  }

  String _formatWeekRange(BuildContext context, DateTime start, DateTime end) {
    final locale = Localizations.localeOf(context).toString();
    final dateFormat = DateFormat.MMMd(locale);

    final startStr = dateFormat.format(start);
    final endStr = dateFormat.format(end);
    final year = start.year;

    return '$startStr - $endStr, $year';
  }
}
