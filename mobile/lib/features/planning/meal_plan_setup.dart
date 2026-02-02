import 'package:recipai_mobile/core/get_it.dart';

import '../auth/auth_service.dart';
import 'meal_plan_calendar_service.dart';
import 'meal_plan_repository.dart';

void setupMealPlan() {
  getIt.registerSingleton(MealPlanRepository());
  getIt.registerLazySingleton(
    () => MealPlanCalendarService(
      repository: getIt<MealPlanRepository>(),
      authService: getIt<AuthService>(),
    ),
    dispose: (service) => service.dispose(),
  );
}
