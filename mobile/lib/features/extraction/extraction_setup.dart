import '../../core/get_it.dart';
import '../auth/auth_service.dart';
import 'extraction_repository.dart';
import 'extraction_service.dart';

void setupExtraction({ExtractionRepository? extractionRepository}) {
  final repository = extractionRepository ?? ExtractionRepository();
  getIt.registerSingleton(repository);
  getIt.registerLazySingleton(
    () => ExtractionService(
      extractionRepository: getIt<ExtractionRepository>(),
      authService: getIt<AuthService>(),
    ),
    dispose: (service) => service.dispose(),
  );
}
