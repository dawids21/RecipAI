import 'package:flutter/foundation.dart';

import '../../core/async_value.dart';
import '../auth/auth_service.dart';
import 'meal_plan_calendar_data.dart';
import 'meal_plan_repository.dart';

class ShoppingListGenerationCalendarService {
  final MealPlanRepository _repository;
  final AuthService _authService;

  ShoppingListGenerationCalendarService({
    required MealPlanRepository repository,
    required AuthService authService,
  }) : _repository = repository,
       _authService = authService,
       _calendarData = ValueNotifier(const AsyncValue.loading());

  final ValueNotifier<AsyncValue<MealPlanCalendarData>> _calendarData;

  ValueListenable<AsyncValue<MealPlanCalendarData>> get calendarData =>
      _calendarData;

  bool _isLoadRunning = false;

  Future<void> loadCalendar({
    required DateTime startDate,
    required DateTime endDate,
    required List<String> planIds,
  }) async {
    if (_isLoadRunning) return;
    _isLoadRunning = true;

    _calendarData.value = const AsyncValue.loading();

    _calendarData.value = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      return _repository.fetchCalendar(
        startDate: startDate,
        endDate: endDate,
        planIds: planIds,
        idToken: token,
      );
    });

    _isLoadRunning = false;
  }

  void reset() {
    _calendarData.value = const AsyncValue.loading();
  }

  void dispose() {
    _calendarData.dispose();
  }
}
