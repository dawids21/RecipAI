import 'package:recipai_mobile/core/get_it.dart';

import 'auth_repository.dart';
import 'auth_service.dart';

void setupAuth(AuthRepository authRepository) {
  getIt.registerSingleton<AuthRepository>(authRepository);
  getIt.registerSingleton(AuthService(authRepository: getIt<AuthRepository>()));
}
