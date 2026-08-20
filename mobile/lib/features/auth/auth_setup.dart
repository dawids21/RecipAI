import 'package:recipai_mobile/core/get_it.dart';

import '../../core/feature_flags.dart';
import '../../core/preferences_service.dart';
import 'auth_repository.dart';
import 'auth_service.dart';
import 'dev_auth_repository.dart';
import 'dev_auth_service.dart';

/// Registers the auth module. In a `devAuthEnabled` build the repository is a
/// [DevAuthRepository] and a [DevAuthService] is registered alongside it — that
/// registration is what the Login Screen renders its dev controls against (see
/// the Feature Flags standard). An injected [authRepository] always wins, and
/// registers no [DevAuthService].
void setupAuth({AuthRepository? authRepository}) {
  if (authRepository != null) {
    getIt.registerSingleton<AuthRepository>(authRepository);
  } else if (FeatureFlags.devAuthEnabled) {
    final devAuthRepository = DevAuthRepository(
      preferencesService: getIt<PreferencesService>(),
    );
    getIt.registerSingleton<AuthRepository>(devAuthRepository);
    getIt.registerSingleton(
      DevAuthService(devAuthRepository: devAuthRepository),
    );
  } else {
    getIt.registerSingleton<AuthRepository>(FirebaseAuthRepository());
  }
  getIt.registerSingleton(AuthService(authRepository: getIt<AuthRepository>()));
}
