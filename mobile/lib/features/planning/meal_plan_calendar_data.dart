import 'package:recipai_mobile/shared/extensions.dart';

import 'meal_plan_calendar_entry.dart';

class MealPlanCalendarData {
  final Map<String, List<MealPlanCalendarEntry>> entriesByDate;

  const MealPlanCalendarData({required this.entriesByDate});

  factory MealPlanCalendarData.fromJson(Map<String, dynamic> json) {
    final Map<String, List<MealPlanCalendarEntry>> entriesMap = {};

    json.forEach((key, value) {
      if (value is List) {
        entriesMap[key] = value
            .map(
              (entry) =>
                  MealPlanCalendarEntry.fromJson(entry as Map<String, dynamic>),
            )
            .toList();
      }
    });

    return MealPlanCalendarData(entriesByDate: entriesMap);
  }

  List<MealPlanCalendarEntry> getEntriesForDate(DateTime date) {
    return entriesByDate[date.toIso8601DateString()] ?? [];
  }

  bool hasEntriesForDate(DateTime date) {
    final entries = entriesByDate[date.toIso8601DateString()];
    return entries != null && entries.isNotEmpty;
  }
}
