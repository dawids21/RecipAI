import '../../core/get_it.dart';
import '../auth/auth_service.dart';
import 'extraction_repository.dart';
import 'extraction_service.dart';

void setupExtraction() {
  getIt.registerSingleton(ExtractionRepository(getIt<AuthService>()));
  getIt.registerLazySingleton(
    () =>
        ExtractionService(extractionRepository: getIt<ExtractionRepository>()),
  );
}
