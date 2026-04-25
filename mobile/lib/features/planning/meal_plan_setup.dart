import 'package:recipai_mobile/core/get_it.dart';

import '../../core/preferences_service.dart';
import '../auth/auth_service.dart';
import 'meal_plan_calendar_service.dart';
import 'meal_plan_list_service.dart';
import 'meal_plan_repository.dart';
import 'meal_plan_visibility_service.dart';
import 'shopping_list_generation_calendar_service.dart';
import 'shopping_list_generation_service.dart';

void setupMealPlan({MealPlanRepository? mealPlanRepository}) {
  final repository = mealPlanRepository ?? MealPlanRepository();
  getIt.registerSingleton<MealPlanRepository>(repository);

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
      mealPlanListService: getIt<MealPlanListService>(),
    ),
    dispose: (service) => service.dispose(),
  );

  getIt.registerLazySingleton(
    () => ShoppingListGenerationCalendarService(
      repository: getIt<MealPlanRepository>(),
      authService: getIt<AuthService>(),
    ),
    dispose: (service) => service.dispose(),
  );

  getIt.registerLazySingleton(
    () => ShoppingListGenerationService(
      mealPlanRepository: getIt<MealPlanRepository>(),
      authService: getIt<AuthService>(),
    ),
    dispose: (service) => service.dispose(),
  );
}
