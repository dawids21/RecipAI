import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../core/theme.dart';
import 'meal_entry_calendar_card.dart';
import 'meal_plan_calendar_entry.dart';

class DaySection extends StatelessWidget {
  final DateTime date;
  final List<MealPlanCalendarEntry> entries;
  final Function(MealPlanCalendarEntry) onEntryTap;

  const DaySection({
    super.key,
    required this.date,
    required this.entries,
    required this.onEntryTap,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isToday = _isToday(date);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          padding: AppSpacing.listTilePadding,
          color: isToday
              ? theme.colorScheme.primaryContainer
              : theme.colorScheme.surface,
          child: Text(
            _formatDayHeader(context, date),
            style: theme.textTheme.titleMedium?.copyWith(
              fontWeight: isToday ? FontWeight.bold : FontWeight.normal,
            ),
          ),
        ),

        // Entries or empty state
        if (entries.isEmpty)
          Padding(
            padding: AppSpacing.listTilePadding,
            child: Text(
              'No meals planned',
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          )
        else
          ...entries.map(
            (entry) => MealEntryCalendarCard(
              entry: entry,
              onTap: () => onEntryTap(entry),
            ),
          ),

        const Divider(height: 1),
      ],
    );
  }

  bool _isToday(DateTime date) {
    final now = DateTime.now();
    return date.year == now.year &&
        date.month == now.month &&
        date.day == now.day;
  }

  String _formatDayHeader(BuildContext context, DateTime date) {
    final locale = Localizations.localeOf(context).toString();
    final weekdayFormat = DateFormat.EEEE(locale);
    final dateFormat = DateFormat.MMMd(locale);

    return '${weekdayFormat.format(date)}, ${dateFormat.format(date)}';
  }
}
