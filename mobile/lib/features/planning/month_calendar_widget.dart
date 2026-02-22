import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../core/theme.dart';
import '../../shared/extensions.dart';
import 'meal_plan_calendar_data.dart';

class MonthCalendarWidget extends StatelessWidget {
  final DateTime displayMonth;
  final Set<DateTime> selectedDates;
  final MealPlanCalendarData? calendarData;
  final ValueChanged<DateTime> onDateToggled;
  final VoidCallback onPreviousMonth;
  final VoidCallback onNextMonth;

  const MonthCalendarWidget({
    super.key,
    required this.displayMonth,
    required this.selectedDates,
    this.calendarData,
    required this.onDateToggled,
    required this.onPreviousMonth,
    required this.onNextMonth,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        _buildHeader(context, theme),
        const SizedBox(height: AppSpacing.small),
        _buildWeekdayLabels(context, theme),
        const SizedBox(height: AppSpacing.extraSmall),
        _buildGrid(context, theme),
      ],
    );
  }

  Widget _buildHeader(BuildContext context, ThemeData theme) {
    final locale = Localizations.localeOf(context).toString();
    final monthLabel =
        '${DateFormat.MMMM(locale).format(displayMonth)} ${displayMonth.year}';

    return Row(
      children: [
        IconButton(
          icon: const Icon(Icons.chevron_left),
          onPressed: onPreviousMonth,
          tooltip: 'Previous month',
        ),
        Expanded(
          child: Text(
            monthLabel,
            textAlign: TextAlign.center,
            style: theme.textTheme.titleMedium?.copyWith(
              fontWeight: FontWeight.bold,
            ),
          ),
        ),
        IconButton(
          icon: const Icon(Icons.chevron_right),
          onPressed: onNextMonth,
          tooltip: 'Next month',
        ),
      ],
    );
  }

  Widget _buildWeekdayLabels(BuildContext context, ThemeData theme) {
    final locale = Localizations.localeOf(context).toString();
    final dartFirstDay = DateTimeLocalizations.dartFirstDayOfWeek(context);
    // 2024-01-01 is a known Monday (weekday=1)
    final refMonday = DateTime(2024, 1, 1);
    final firstDayDate = refMonday.add(Duration(days: dartFirstDay - 1));
    final weekdayLabels = List.generate(
      7,
      (i) => DateFormat.E(locale).format(firstDayDate.add(Duration(days: i))),
    );

    return Row(
      children: weekdayLabels
          .map(
            (label) => Expanded(
              child: Text(
                label,
                textAlign: TextAlign.center,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
          )
          .toList(),
    );
  }

  Widget _buildGrid(BuildContext context, ThemeData theme) {
    final dartFirstDay = DateTimeLocalizations.dartFirstDayOfWeek(context);
    final firstDayOfMonth = DateTime(displayMonth.year, displayMonth.month, 1);
    final startOffset = (firstDayOfMonth.weekday - dartFirstDay + 7) % 7;
    final daysInMonth = DateTime(
      displayMonth.year,
      displayMonth.month + 1,
      0,
    ).day;
    final totalCells = startOffset + daysInMonth;
    // Round up to full weeks
    final rowCount = (totalCells / 7).ceil();

    return GridView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 7,
        childAspectRatio: 1.0,
      ),
      itemCount: rowCount * 7,
      itemBuilder: (context, index) {
        final dayNumber = index - startOffset + 1;

        if (dayNumber < 1 || dayNumber > daysInMonth) {
          return const SizedBox.shrink();
        }

        final date = DateTime(displayMonth.year, displayMonth.month, dayNumber);
        final isSelected = selectedDates.contains(date);
        final hasEntries = calendarData?.hasEntriesForDate(date) ?? false;

        return _DayCell(
          day: dayNumber,
          isSelected: isSelected,
          hasEntries: hasEntries,
          onTap: () => onDateToggled(date),
          theme: theme,
        );
      },
    );
  }
}

class _DayCell extends StatelessWidget {
  final int day;
  final bool isSelected;
  final bool hasEntries;
  final VoidCallback onTap;
  final ThemeData theme;

  const _DayCell({
    required this.day,
    required this.isSelected,
    required this.hasEntries,
    required this.onTap,
    required this.theme,
  });

  @override
  Widget build(BuildContext context) {
    final backgroundColor = isSelected
        ? theme.colorScheme.primary
        : Colors.transparent;
    final textColor = isSelected
        ? theme.colorScheme.onPrimary
        : theme.colorScheme.onSurface;
    final dotColor = isSelected
        ? theme.colorScheme.onPrimary.withValues(alpha: 0.8)
        : theme.colorScheme.primary;

    return GestureDetector(
      onTap: onTap,
      child: Container(
        margin: const EdgeInsets.all(2),
        decoration: BoxDecoration(
          color: backgroundColor,
          shape: BoxShape.circle,
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text(
              '$day',
              style: theme.textTheme.bodyMedium?.copyWith(
                color: textColor,
                fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
              ),
            ),
            if (hasEntries)
              Container(
                width: 4,
                height: 4,
                decoration: BoxDecoration(
                  color: dotColor,
                  shape: BoxShape.circle,
                ),
              )
            else
              const SizedBox(height: 4),
          ],
        ),
      ),
    );
  }
}
