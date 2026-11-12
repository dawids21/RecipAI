import '../../core/get_it.dart';
import '../auth/auth_service.dart';
import 'account_deletion_service.dart';
import 'account_repository.dart';

/// Registers the settings module. [AccountDeletionService] is a plain
/// singleton: the success path disposes the Settings screen before the
/// service finishes, so it must outlive the screen.
void setupSettings({AccountRepository? accountRepository}) {
  final repository = accountRepository ?? AccountRepository();
  getIt.registerSingleton<AccountRepository>(repository);
  getIt.registerSingleton(
    AccountDeletionService(
      accountRepository: getIt<AccountRepository>(),
      authService: getIt<AuthService>(),
    ),
  );
}
