import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../../core/async_value.dart';
import '../auth/auth_service.dart';
import '../limits/limit_usage.dart';
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

  final ValueNotifier<AsyncValue<LimitUsage>> _planUsage = ValueNotifier(
    const AsyncValue.loading(),
  );

  ValueListenable<AsyncValue<LimitUsage>> get planUsage => _planUsage;

  bool _isLoadMealPlansRunning = false;
  bool _isLoadPlanUsageRunning = false;

  Future<void> loadPlanUsage() async {
    if (_isLoadPlanUsageRunning) return;
    _isLoadPlanUsageRunning = true;
    _planUsage.value = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      return _repository.fetchPlanUsage(idToken: token);
    });
    _isLoadPlanUsageRunning = false;
  }

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

  Future<MealPlan> createMealPlan({
    required String name,
    required Color color,
  }) async {
    final token = await _authService.idToken;
    final newPlan = await _repository.createMealPlan(
      name: name,
      color: color,
      idToken: token,
    );

    await loadMealPlans();

    return newPlan;
  }

  Future<MealPlan> updateMealPlan({
    required String id,
    required String name,
    required Color color,
  }) async {
    final token = await _authService.idToken;
    final updatedPlan = await _repository.updateMealPlan(
      id: id,
      name: name,
      color: color,
      idToken: token,
    );

    await loadMealPlans();

    return updatedPlan;
  }

  Future<void> deleteMealPlan({required String id}) async {
    final token = await _authService.idToken;
    await _repository.deleteMealPlan(id: id, idToken: token);

    await loadMealPlans();
  }

  void dispose() {
    _mealPlans.dispose();
    _planUsage.dispose();
  }
}
