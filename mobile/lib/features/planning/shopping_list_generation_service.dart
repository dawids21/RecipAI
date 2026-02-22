import 'package:flutter/foundation.dart';

import '../../core/async_value.dart';
import '../auth/auth_service.dart';
import 'meal_plan_repository.dart';
import 'shopping_list_generated_items.dart';

class ShoppingListGenerationService {
  final MealPlanRepository _mealPlanRepository;
  final AuthService _authService;

  ShoppingListGenerationService({
    required MealPlanRepository mealPlanRepository,
    required AuthService authService,
  }) : _mealPlanRepository = mealPlanRepository,
       _authService = authService,
       _generatedItems = ValueNotifier(const AsyncValue.loading());

  final ValueNotifier<AsyncValue<ShoppingListGeneratedItems>> _generatedItems;

  ValueListenable<AsyncValue<ShoppingListGeneratedItems>> get generatedItems =>
      _generatedItems;

  bool _isGenerateRunning = false;

  Future<void> generateShoppingList({
    required List<String> planIds,
    required List<DateTime> selectedDates,
  }) async {
    if (_isGenerateRunning) return;
    _isGenerateRunning = true;

    _generatedItems.value = const AsyncValue.loading();

    _generatedItems.value = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      return _mealPlanRepository.generateShoppingList(
        planIds: planIds,
        selectedDates: selectedDates,
        idToken: token,
      );
    });

    _isGenerateRunning = false;
  }

  void reset() {
    _generatedItems.value = const AsyncValue.loading();
    _isGenerateRunning = false;
  }

  void dispose() {
    _generatedItems.dispose();
  }
}
