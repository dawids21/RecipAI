import '../../core/get_it.dart';
import '../auth/auth_service.dart';
import 'limits_repository.dart';
import 'limits_service.dart';

void setupLimits({LimitsRepository? limitsRepository}) {
  final repository = limitsRepository ?? LimitsRepository();
  getIt.registerSingleton<LimitsRepository>(repository);
  getIt.registerSingleton<LimitsService>(
    LimitsService(
      limitsRepository: getIt<LimitsRepository>(),
      authService: getIt<AuthService>(),
    ),
    dispose: (service) => service.dispose(),
  );
}
