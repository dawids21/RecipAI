import 'package:flutter/foundation.dart';
import 'package:recipai_mobile/core/widgets/sharing_dialog.dart';

import '../../core/async_value.dart';
import '../auth/auth_service.dart';
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

  final ValueNotifier<AsyncValue<List<SharedUser>>> _sharedUsers =
      ValueNotifier(const AsyncValue.loading());

  ValueListenable<AsyncValue<List<SharedUser>>> get sharedUsers => _sharedUsers;

  bool _isLoadSharedUsersRunning = false;
  bool _isShareMealPlanRunning = false;
  bool _isUnshareMealPlanRunning = false;

  Future<void> loadSharedUsers() async {
    if (_isLoadSharedUsersRunning) return;
    _isLoadSharedUsersRunning = true;

    _sharedUsers.value = const AsyncValue.loading();

    _sharedUsers.value = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      final permissions = await _mealPlanRepository.fetchSharedUsers(
        mealPlanId: mealPlanId,
        idToken: token,
      );
      final currentUserEmail = _authService.email;
      return permissions.map((permission) {
        return SharedUser(
          email: permission.email,
          role: permission.role.displayName,
          isCurrentUser: permission.email == currentUserEmail,
        );
      }).toList();
    });

    _isLoadSharedUsersRunning = false;
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
      await loadSharedUsers();
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
      await loadSharedUsers();
      await _mealPlanListService.loadMealPlans();
    }

    _isUnshareMealPlanRunning = false;

    if (result is AsyncError) {
      throw result.error;
    }
  }

  void dispose() {
    _sharedUsers.dispose();
  }
}
