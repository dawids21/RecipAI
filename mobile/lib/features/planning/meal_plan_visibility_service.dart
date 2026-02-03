import 'package:flutter/foundation.dart';

import '../../core/preferences_service.dart';

class MealPlanVisibilityService {
  final PreferencesService _preferencesService;
  final ValueNotifier<Map<String, bool>> _visibility;

  MealPlanVisibilityService({required PreferencesService preferencesService})
    : _preferencesService = preferencesService,
      _visibility = ValueNotifier(preferencesService.getMealPlanVisibility());

  ValueListenable<Map<String, bool>> get visibility => _visibility;

  void ensurePlanVisibility(String planId) {
    if (!_visibility.value.containsKey(planId)) {
      final updated = Map<String, bool>.from(_visibility.value);
      updated[planId] = true;
      _visibility.value = updated;
      _preferencesService.setMealPlanVisibility(updated);
    }
  }

  Future<void> toggleVisibility(String planId) async {
    final updated = Map<String, bool>.from(_visibility.value);
    updated[planId] = !(updated[planId] ?? true);
    _visibility.value = updated;
    await _preferencesService.setMealPlanVisibility(updated);
  }

  bool isVisible(String planId) {
    return _visibility.value[planId] ?? true;
  }

  List<String> getVisiblePlanIds() {
    return _visibility.value.entries
        .where((entry) => entry.value)
        .map((entry) => entry.key)
        .toList();
  }

  void dispose() {
    _visibility.dispose();
  }
}
