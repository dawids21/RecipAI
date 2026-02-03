import 'package:flutter/foundation.dart';

import '../../core/async_value.dart';
import '../auth/auth_service.dart';
import 'meal_plan_calendar_data.dart';
import 'meal_plan_repository.dart';
import 'meal_plan_visibility_service.dart';

class MealPlanCalendarService {
  final MealPlanRepository _repository;
  final AuthService _authService;
  final MealPlanVisibilityService _visibilityService;
  final int _firstDayOfWeek; // 0 = Sunday, 1 = Monday, etc.

  MealPlanCalendarService({
    required MealPlanRepository repository,
    required AuthService authService,
    required MealPlanVisibilityService visibilityService,
    int firstDayOfWeek = 1, // Default to Monday
  }) : _repository = repository,
       _authService = authService,
       _visibilityService = visibilityService,
       _firstDayOfWeek = firstDayOfWeek,
       _currentWeekStart = ValueNotifier(
         _getWeekStart(DateTime.now(), firstDayOfWeek),
       ),
       _calendarData = ValueNotifier(const AsyncValue.loading()) {
    _visibilityService.visibility.addListener(_onVisibilityChanged);
  }

  final ValueNotifier<DateTime> _currentWeekStart;

  ValueListenable<DateTime> get currentWeekStart => _currentWeekStart;

  final ValueNotifier<AsyncValue<MealPlanCalendarData>> _calendarData;

  ValueListenable<AsyncValue<MealPlanCalendarData>> get calendarData =>
      _calendarData;

  bool _isLoadingCalendar = false;

  void _onVisibilityChanged() {
    loadCalendar();
  }

  Future<void> loadCalendar() async {
    if (_isLoadingCalendar) return;
    _isLoadingCalendar = true;

    _calendarData.value = const AsyncValue.loading();

    final weekStart = _currentWeekStart.value;
    final weekEnd = weekStart.add(const Duration(days: 6));
    final visiblePlanIds = _visibilityService.getVisiblePlanIds();

    _calendarData.value = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      return _repository.fetchCalendar(
        startDate: weekStart,
        endDate: weekEnd,
        planIds: visiblePlanIds,
        idToken: token,
      );
    });

    _isLoadingCalendar = false;
  }

  void goToNextWeek() {
    _currentWeekStart.value = _currentWeekStart.value.add(
      const Duration(days: 7),
    );
    loadCalendar();
  }

  void goToPreviousWeek() {
    _currentWeekStart.value = _currentWeekStart.value.subtract(
      const Duration(days: 7),
    );
    loadCalendar();
  }

  void goToToday() {
    _currentWeekStart.value = _getWeekStart(DateTime.now(), _firstDayOfWeek);
    loadCalendar();
  }

  static DateTime _getWeekStart(DateTime date, int firstDayOfWeek) {
    final currentWeekday = date.weekday % 7;
    final daysToSubtract = (currentWeekday - firstDayOfWeek + 7) % 7;

    return DateTime(date.year, date.month, date.day - daysToSubtract);
  }

  void dispose() {
    _visibilityService.visibility.removeListener(_onVisibilityChanged);
    _currentWeekStart.dispose();
    _calendarData.dispose();
  }
}
