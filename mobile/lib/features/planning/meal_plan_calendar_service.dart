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

  MealPlanCalendarService({
    required MealPlanRepository repository,
    required AuthService authService,
    required MealPlanVisibilityService visibilityService,
  }) : _repository = repository,
       _authService = authService,
       _visibilityService = visibilityService,
       _currentWeekStart = ValueNotifier(DateTime(0)),
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

  void goToWeek(DateTime weekStart) {
    _currentWeekStart.value = weekStart;
    loadCalendar();
  }

  Future<void> createMealEntry({
    required String planId,
    required DateTime date,
    String? recipeId,
    String? placeholderText,
    int? servingSize,
  }) async {
    final token = await _authService.idToken;
    await _repository.createMealEntry(
      planId: planId,
      date: date,
      recipeId: recipeId,
      placeholderText: placeholderText,
      servingSize: servingSize,
      idToken: token,
    );

    await loadCalendar();
  }

  Future<void> updateMealEntry({
    required String planId,
    required int entryId,
    required DateTime date,
    String? recipeId,
    String? placeholderText,
    int? servingSize,
  }) async {
    final token = await _authService.idToken;
    await _repository.updateMealEntry(
      planId: planId,
      entryId: entryId,
      date: date,
      recipeId: recipeId,
      placeholderText: placeholderText,
      servingSize: servingSize,
      idToken: token,
    );

    await loadCalendar();
  }

  Future<void> deleteMealEntry({
    required String planId,
    required int entryId,
  }) async {
    final token = await _authService.idToken;
    await _repository.deleteMealEntry(
      planId: planId,
      entryId: entryId,
      idToken: token,
    );

    await loadCalendar();
  }

  void dispose() {
    _visibilityService.visibility.removeListener(_onVisibilityChanged);
    _currentWeekStart.dispose();
    _calendarData.dispose();
  }
}
