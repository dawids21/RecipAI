import 'package:logging/logging.dart';

import '../auth/auth_repository.dart';
import '../auth/auth_service.dart';
import 'account_repository.dart';

enum AccountDeletionOutcome {
  deleted,
  cancelled,
  reauthFailed,
  purgeFailed,
  identityDeletionFailed,
}

/// Runs account deletion in the order ADR-0009 fixes: re-authenticate, purge
/// the backend, delete the Firebase identity, sign out. Every failure maps to
/// an [AccountDeletionOutcome] for the screen to report.
class AccountDeletionService {
  static final _log = Logger('recipai.settings.service');

  final AccountRepository _accountRepository;
  final AuthService _authService;

  AccountDeletionService({
    required AccountRepository accountRepository,
    required AuthService authService,
  }) : _accountRepository = accountRepository,
       _authService = authService;

  bool _isDeleteAccountRunning = false;

  Future<AccountDeletionOutcome> deleteAccount() async {
    if (_isDeleteAccountRunning) return AccountDeletionOutcome.cancelled;
    _isDeleteAccountRunning = true;
    try {
      try {
        await _authService.reauthenticate();
      } on ReauthCancelledException {
        return AccountDeletionOutcome.cancelled;
      } catch (e, st) {
        _log.warning('Re-authentication failed', e, st);
        return AccountDeletionOutcome.reauthFailed;
      }

      try {
        await _accountRepository.deleteAccount(await _authService.idToken);
      } catch (e, st) {
        // Still signed in with nothing deleted — a retry is meaningful.
        _log.warning('Account purge failed', e, st);
        return AccountDeletionOutcome.purgeFailed;
      }

      try {
        await _authService.deleteIdentity();
      } catch (e, st) {
        // The data is gone; the screen reports it, then calls signOut().
        _log.warning('Identity deletion failed', e, st);
        return AccountDeletionOutcome.identityDeletionFailed;
      }

      // Deleting the identity already signs Firebase out; this clears the
      // cached Google account so the next sign-in prompts for an account.
      try {
        await _authService.signOut();
      } catch (e, st) {
        // The identity is gone; there is nothing left to report.
        _log.warning('Sign-out after deletion failed', e, st);
      }

      _log.info('Account deleted');
      return AccountDeletionOutcome.deleted;
    } finally {
      _isDeleteAccountRunning = false;
    }
  }

  /// Signs out after [AccountDeletionOutcome.identityDeletionFailed] has been
  /// reported. A failure is logged and swallowed — the data is already gone.
  Future<void> signOut() async {
    try {
      await _authService.signOut();
    } catch (e, st) {
      _log.warning('Sign-out after deletion failed', e, st);
    }
  }
}
