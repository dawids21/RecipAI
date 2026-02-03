import 'package:recipai_mobile/core/get_it.dart';

import '../../core/preferences_service.dart';
import '../auth/auth_service.dart';
import 'meal_plan_calendar_service.dart';
import 'meal_plan_list_service.dart';
import 'meal_plan_repository.dart';
import 'meal_plan_visibility_service.dart';

void setupMealPlan() {
  getIt.registerSingleton(MealPlanRepository());

  getIt.registerLazySingleton(
    () => MealPlanVisibilityService(
      preferencesService: getIt<PreferencesService>(),
    ),
    dispose: (service) => service.dispose(),
  );

  getIt.registerLazySingleton(
    () => MealPlanListService(
      repository: getIt<MealPlanRepository>(),
      authService: getIt<AuthService>(),
      visibilityService: getIt<MealPlanVisibilityService>(),
    ),
    dispose: (service) => service.dispose(),
  );

  getIt.registerLazySingleton(
    () => MealPlanCalendarService(
      repository: getIt<MealPlanRepository>(),
      authService: getIt<AuthService>(),
      visibilityService: getIt<MealPlanVisibilityService>(),
    ),
    dispose: (service) => service.dispose(),
  );
}
