import 'package:flutter/material.dart';

import '../../shared/api_error_widget.dart';
import '../../shared/loading_widget.dart';
import 'day_section.dart';
import 'meal_plan_calendar_entry.dart';
import 'meal_plan_calendar_service.dart';
import 'week_strip.dart';

class MealPlanCalendarScreen extends StatefulWidget {
  final MealPlanCalendarService calendarService;

  const MealPlanCalendarScreen({super.key, required this.calendarService});

  @override
  State<MealPlanCalendarScreen> createState() => _MealPlanCalendarScreenState();
}

class _MealPlanCalendarScreenState extends State<MealPlanCalendarScreen> {
  @override
  void initState() {
    super.initState();
  }

  void _onEntryTap(MealPlanCalendarEntry entry) {
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(const SnackBar(content: Text('Entry details coming soon')));
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
