import 'package:flutter/foundation.dart';

import '../../core/async_value.dart';
import '../auth/auth_service.dart';
import 'meal_plan.dart';
import 'meal_plan_repository.dart';
import 'meal_plan_visibility_service.dart';

class MealPlanListService {
  final MealPlanRepository _repository;
  final AuthService _authService;
  final MealPlanVisibilityService _visibilityService;

  MealPlanListService({
    required MealPlanRepository repository,
    required AuthService authService,
    required MealPlanVisibilityService visibilityService,
  }) : _repository = repository,
       _authService = authService,
       _visibilityService = visibilityService,
       _mealPlans = ValueNotifier(const AsyncValue.loading());

  final ValueNotifier<AsyncValue<List<MealPlan>>> _mealPlans;

  ValueListenable<AsyncValue<List<MealPlan>>> get mealPlans => _mealPlans;

  bool _isLoadMealPlansRunning = false;

  Future<void> loadMealPlans() async {
    if (_isLoadMealPlansRunning) return;
    _isLoadMealPlansRunning = true;

    _mealPlans.value = const AsyncValue.loading();
    _mealPlans.value = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      final plans = await _repository.fetchMealPlans(idToken: token);

      for (final plan in plans) {
        _visibilityService.ensurePlanVisibility(plan.id);
      }

      return plans;
    });

    _isLoadMealPlansRunning = false;
  }

  void dispose() {
    _mealPlans.dispose();
  }
}
