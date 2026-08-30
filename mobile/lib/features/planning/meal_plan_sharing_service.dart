import 'package:flutter/foundation.dart';

import '../../core/async_value.dart';
import '../auth/auth_service.dart';
import '../sharing/resource_permission.dart';
import 'meal_plan_list_service.dart';
import 'meal_plan_repository.dart';

class MealPlanSharingService {
  final MealPlanRepository _mealPlanRepository;
  final AuthService _authService;
  final MealPlanListService _mealPlanListService;
  final String mealPlanId;

  MealPlanSharingService({
    required MealPlanRepository mealPlanRepository,
    required AuthService authService,
    required MealPlanListService mealPlanListService,
    required this.mealPlanId,
  }) : _mealPlanRepository = mealPlanRepository,
       _authService = authService,
       _mealPlanListService = mealPlanListService;

  final ValueNotifier<AsyncValue<List<ResourcePermission>>> _permissions =
      ValueNotifier(const AsyncValue.loading());

  ValueListenable<AsyncValue<List<ResourcePermission>>> get permissions =>
      _permissions;

  bool _isLoadPermissionsRunning = false;
  bool _isShareMealPlanRunning = false;
  bool _isUnshareMealPlanRunning = false;

  Future<void> loadPermissions() async {
    if (_isLoadPermissionsRunning) return;
    _isLoadPermissionsRunning = true;

    _permissions.value = const AsyncValue.loading();

    _permissions.value = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      return _mealPlanRepository.fetchPermissions(
        mealPlanId: mealPlanId,
        idToken: token,
      );
    });

    _isLoadPermissionsRunning = false;
  }

  Future<void> shareMealPlan(String email) async {
    if (_isShareMealPlanRunning) return;
    _isShareMealPlanRunning = true;

    final result = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      return _mealPlanRepository.shareMealPlan(
        mealPlanId: mealPlanId,
        email: email,
        idToken: token,
      );
    });

    if (result is AsyncData) {
      await loadPermissions();
      await _mealPlanListService.loadMealPlans();
    }

    _isShareMealPlanRunning = false;

    if (result is AsyncError) {
      throw result.error;
    }
  }

  Future<void> unshareMealPlan(String email) async {
    if (_isUnshareMealPlanRunning) return;
    _isUnshareMealPlanRunning = true;

    final result = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      return _mealPlanRepository.unshareMealPlan(
        mealPlanId: mealPlanId,
        email: email,
        idToken: token,
      );
    });

    if (result is AsyncData) {
      await loadPermissions();
      await _mealPlanListService.loadMealPlans();
    }

    _isUnshareMealPlanRunning = false;

    if (result is AsyncError) {
      throw result.error;
    }
  }

  void dispose() {
    _permissions.dispose();
  }
}
