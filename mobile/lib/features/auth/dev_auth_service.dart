import 'dev_auth_repository.dart';

/// Dev-only sign-in for the Login Screen. It is registered only in a
/// `devAuthEnabled` build (see the Feature Flags standard), so views take it as
/// a nullable dependency and render the dev sign-in controls only when one is
/// injected.
class DevAuthService {
  final DevAuthRepository _devAuthRepository;

  DevAuthService({required DevAuthRepository devAuthRepository})
    : _devAuthRepository = devAuthRepository;

  /// Signs in as [name].
  Future<void> signIn(String name) => _devAuthRepository.signInAs(name);
}
