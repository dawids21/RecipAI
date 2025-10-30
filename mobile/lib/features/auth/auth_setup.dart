import 'package:recipai_mobile/core/get_it.dart';

import 'auth_repository.dart';
import 'auth_service.dart';

void setupAuth({AuthRepository? authRepository}) {
  final repository = authRepository ?? FirebaseAuthRepository();
  getIt.registerSingleton<AuthRepository>(repository);
  getIt.registerSingleton(AuthService(authRepository: getIt<AuthRepository>()));
}
