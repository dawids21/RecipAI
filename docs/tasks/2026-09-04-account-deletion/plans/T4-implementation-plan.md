# T4: Mobile account deletion flow — Implementation Plan

**Date:** 2026-11-12

## Required reading

**Docs & standards** (from `docs/INDEX.md`)
- `docs/mobile/standards/architecture.md` > Repository-Service-View — why the screen goes through
  `AccountDeletionService`, never `AccountRepository`.
- `docs/mobile/standards/dependency-injection.md` — `setupSettings({AccountRepository?})` shape, plain
  singleton for app-lifetime state.
- `docs/mobile/standards/state-management.md` > Guarding Against Concurrent Calls — the
  `_isDeleteAccountRunning` flag in `try/finally`.
- `docs/mobile/standards/logging.md` — logger names `recipai.settings.service` /
  `recipai.settings.repository`, `WARNING` for failures, never log the `idToken`.
- `docs/mobile/standards/widget-testing.md` — repository-only mocks, `setup*()` order as in `main()`,
  test-local `GoRouter`.
- `docs/mobile/modules/core/module.md` > Services and State — the auth redirect that removes the screen on
  the success path.

**Design & ADRs**
- `docs/tasks/2026-09-04-account-deletion/plans/T4-task-design.md` — the whole document; Pseudo-code is the
  contract for the service and the screen.
- `docs/tasks/2026-09-04-account-deletion/HLD.md` > Feature areas > Mobile — account deletion flow — the
  failure paths and their meaning.
- `docs/ADRs/0009-client-side-firebase-identity-deletion.md` — backend purge first, identity second.
- `docs/backend/modules/settings/api.md` — `DELETE /settings/account` contract (204 on success).

**Code to mirror**
- `mobile/lib/features/invites/invites_repository.dart` — HTTP repository shape (`_getAuthHeaders`,
  status check, `throw Exception(...)`).
- `mobile/lib/features/shopping_list/shopping_list_item_repository.dart` — the `METHOD url -> status (ms)`
  `INFO` network line and the `WARNING` on a transport failure.
- `mobile/lib/features/invites/invites_setup.dart` — setup function with an injectable repository.
- `mobile/lib/features/invites/invites_service.dart` — `static final _log`, constructor injection.
- `mobile/lib/features/auth/auth_service.dart` — pass-through method shape for the two new methods.
- `mobile/lib/features/auth/auth_repository.dart` > `FirebaseAuthRepository.signIn` — Google credential →
  Firebase credential, reused for `reauthenticate()`.
- `mobile/lib/features/auth/login_screen.dart` — `_isLoading` → `LoadingWidget` body swap.
- `mobile/lib/features/settings/settings_screen.dart` > `_onLogoutTap` — confirmation dialog and snackbar.
- `mobile/test/features/settings/settings_screen_widget_test.dart` — existing setUp, `dialogButton` finder.

## File inventory

- **MODIFY** `mobile/lib/features/auth/auth_repository.dart` — `reauthenticate`, `deleteIdentity`, `ReauthCancelledException`; Firebase implementation.
- **MODIFY** `mobile/lib/features/auth/dev_auth_repository.dart` — no-op `reauthenticate`; `deleteIdentity` delegates to `signOut`.
- **MODIFY** `mobile/lib/features/auth/auth_service.dart` — pass-through `reauthenticate()` and `deleteIdentity()`.
- **CREATE** `mobile/lib/features/settings/account_repository.dart` — `DELETE /settings/account`; 204 success, else throws.
- **CREATE** `mobile/lib/features/settings/account_deletion_service.dart` — `AccountDeletionOutcome` enum and the ordered deletion flow.
- **CREATE** `mobile/lib/features/settings/settings_setup.dart` — registers `AccountRepository` and `AccountDeletionService` singletons.
- **MODIFY** `mobile/lib/features/settings/settings_screen.dart` — Delete account row, dialogs, in-progress state, outcome handling.
- **MODIFY** `mobile/lib/core/routes.dart` — settings builder passes `getIt<AccountDeletionService>()`.
- **MODIFY** `mobile/lib/main.dart` — call `setupSettings()` after `setupAuth()`.
- **MODIFY** `mobile/test/support/mocks.dart` — add `MockAccountRepository`.
- **MODIFY** `mobile/test/features/settings/settings_screen_widget_test.dart` — `setupSettings` in setUp; deletion-flow tests.
- **MODIFY** `docs/mobile/modules/settings/module.md` — endpoint, file map, service and its lifetime (named deliverable).
- **MODIFY** `docs/mobile/modules/settings/ui.md` — Delete account row, dialogs, messages (named deliverable).
- **MODIFY** `docs/mobile/modules/auth/module.md` — the two repository methods and their dev behaviour (named deliverable).

`MockAuthRepository` needs no change — `Mock` covers new abstract members. `FirebaseAuthRepository` and
`DevAuthRepository` are the only implementers of `AuthRepository`. No dependency change: `firebase_auth`
6.5.6 and `google_sign_in` 7.2.0 already provide `User.reauthenticateWithCredential`, `User.delete` and
`GoogleSignInException` / `GoogleSignInExceptionCode.canceled` (re-exported by `google_sign_in`).

## Step-by-step plan

1. **Auth contract: re-authenticate and delete identity** — extend the auth layer; no caller yet.
   - Files: `mobile/lib/features/auth/auth_repository.dart`, `mobile/lib/features/auth/dev_auth_repository.dart`,
     `mobile/lib/features/auth/auth_service.dart`
   - `AuthRepository`: add `Future<void> reauthenticate()` and `Future<void> deleteIdentity()` with the doc
     comments from the task design; add `class ReauthCancelledException implements Exception { const ReauthCancelledException(); }`
     in the same file.
   - `FirebaseAuthRepository.reauthenticate()`: `final user = _firebaseAuth.currentUser;` — `null` throws
     `StateError('No signed-in user')`. Then `try { googleUser = await _googleSignIn.authenticate(); } on
     GoogleSignInException catch (e) { if (e.code == GoogleSignInExceptionCode.canceled) throw const
     ReauthCancelledException(); rethrow; }`, build `GoogleAuthProvider.credential(idToken:
     googleUser.authentication.idToken)` as `signIn()` does, and `await user.reauthenticateWithCredential(credential)`.
     `deleteIdentity()`: `currentUser` null throws `StateError`; else `await user.delete()`.
   - `DevAuthRepository.reauthenticate()`: `async {}`. `deleteIdentity()`: `=> signOut()`.
   - `AuthService`: `Future<void> reauthenticate() => _authRepository.reauthenticate();` and the same for
     `deleteIdentity()`. No running guard — `AccountDeletionService` owns it.
   - Verify: `cd mobile && flutter analyze` clean; `flutter test` still green.

2. **Account repository, deletion service and setup** — the flow exists and is registered, not yet reachable
   from UI.
   - Files: `mobile/lib/features/settings/account_repository.dart`,
     `mobile/lib/features/settings/account_deletion_service.dart`,
     `mobile/lib/features/settings/settings_setup.dart`, `mobile/lib/main.dart`, `mobile/test/support/mocks.dart`
   - `AccountRepository`: `InvitesRepository` shape plus `static final _log =
     Logger('recipai.settings.repository')`. `deleteAccount(String? idToken)` sends
     `_client.delete(Uri.parse('$_baseUrl/settings/account'), headers: ...)` inside a stopwatch; a transport
     exception logs `WARNING 'DELETE $url failed (N ms)'` and rethrows; otherwise logs `INFO 'DELETE $url ->
     $status (N ms)'`. `204` returns; anything else throws `Exception('Failed to delete account: $status')`.
   - `account_deletion_service.dart`: `enum AccountDeletionOutcome { deleted, cancelled, reauthFailed,
     purgeFailed, identityDeletionFailed }` and `AccountDeletionService` with named required
     `accountRepository` / `authService`, `static final _log = Logger('recipai.settings.service')`,
     `bool _isDeleteAccountRunning`. `deleteAccount()` follows the task design's pseudo-code exactly (four
     sequential try blocks inside an outer `try/finally` that clears the flag); each failure logs
     `_log.warning('<step> failed', e, st)`; a successful run logs `INFO 'Account deleted'`. Note in a code
     comment why the trailing `signOut()` exists (clears the cached Google account). `signOut()` calls
     `_authService.signOut()` and logs-and-swallows a failure, like the trailing sign-out in `deleteAccount()`
     (the data is already gone, so there is nothing to report). No `dispose()` — no notifiers.
   - `settings_setup.dart`: `void setupSettings({AccountRepository? accountRepository})` — register the
     repository (`accountRepository ?? AccountRepository()`) with `registerSingleton`, then
     `registerSingleton(AccountDeletionService(accountRepository: getIt<AccountRepository>(), authService:
     getIt<AuthService>()))`.
   - `main.dart`: import `features/settings/settings_setup.dart`; call `setupSettings();` directly after
     `setupAuth();`.
   - `mocks.dart`: `class MockAccountRepository extends Mock implements AccountRepository {}` with its import.
   - Verify: `cd mobile && flutter analyze` clean; app still launches (`flutter run --dart-define=DEV_AUTH_ENABLED=true`)
     and Settings opens unchanged.

3. **Delete account on the Settings screen** — the user-visible flow, with its widget tests.
   - Files: `mobile/lib/features/settings/settings_screen.dart`, `mobile/lib/core/routes.dart`,
     `mobile/test/features/settings/settings_screen_widget_test.dart`
   - `SettingsScreen`: add `final AccountDeletionService accountDeletionService` (required). State gains
     `bool _isDeleting = false`. Body: `PopScope(canPop: !_isDeleting, child: _isDeleting ? const
     LoadingWidget(message: 'Deleting your account...') : ListView(...))`. Add, below Logout in the same
     `Card`, a `Divider(height: 1)` and a `ListTile` with `Icons.delete_forever`, title `Delete account`,
     icon and text coloured `theme.colorScheme.error`, `onTap: _onDeleteAccountTap`.
   - `_onDeleteAccountTap()`: confirmation `AlertDialog` (title `Delete account`, content stating deletion
     of all recipes, collections, shopping lists and meal plans is immediate and cannot be undone,
     `Cancel` / `Delete` popping `false` / `true`, mirroring `_onLogoutTap`). On anything but `true`, return.
     Capture `ScaffoldMessenger.of(context)`, `setState(_isDeleting = true)`, await
     `widget.accountDeletionService.deleteAccount()`, `if (!mounted) return`, `setState(_isDeleting = false)`,
     then an exhaustive `switch` per the pseudo-code: `deleted`/`cancelled` nothing; `reauthFailed` →
     snackbar `Could not confirm your identity. Nothing was deleted.`; `purgeFailed` → snackbar `Failed to
     delete your account. Please try again.`; `identityDeletionFailed` → `showDialog(barrierDismissible:
     false)` with `AlertDialog(title: 'Something went wrong', content: 'Your data was deleted, but your
     account could not be removed. Please contact support.', actions: [TextButton('OK')])`, then
     `await widget.accountDeletionService.signOut()` — the redirect handles navigation.
   - `routes.dart`: settings builder becomes `SettingsScreen(authService: getIt<AuthService>(),
     accountDeletionService: getIt<AccountDeletionService>())`; add the import.
   - Test file: setUp adds `accountRepository = MockAccountRepository()` and `setupSettings(accountRepository:
     accountRepository)` after `setupAuth(...)`; the router builder passes
     `GetIt.I<AccountDeletionService>()`. Add an `openDeleteDialog(tester)` helper beside `openLogoutDialog`.
     Cases per Test plan.
   - Verify: `cd mobile && flutter test test/features/settings/settings_screen_widget_test.dart` green.

4. **Module docs** — the three docs the task design names.
   - Files: `docs/mobile/modules/settings/module.md`, `docs/mobile/modules/settings/ui.md`,
     `docs/mobile/modules/auth/module.md`
   - `settings/module.md`: Boundary → backend endpoint `DELETE /settings/account`; Uses gains the auth
     re-authentication/identity deletion; File Map gains the three new files; Services and State replaces
     "No service of its own" with `AccountDeletionService` (app-lifetime singleton because the success path
     disposes the screen mid-flow; order per ADR-0009; outcomes). Follow `docs/project/documentation-standard.md`.
   - `settings/ui.md`: the Delete account row, its confirmation dialog, the in-progress state and each
     outcome's message.
   - `auth/module.md`: `reauthenticate()` / `deleteIdentity()` and the dev build's no-op / sign-out behaviour.
   - Verify: links resolve; no fact repeated across the three files.

5. **End-to-end check** — run the Manual verification below.

## Test plan

**Unit tests**
_N/A — `docs/mobile/standards/widget-testing.md` puts service behaviour under widget tests with real services
on repository mocks; the task design chooses widget tests only._

**Integration tests**
_N/A — no backend change; T1 owns the endpoint's integration suite._

**Flutter widget/integration tests** — `settings_screen_widget_test.dart` (existing tests kept, plus):
- `renders Delete account below Logout`
- `tapping Delete account shows the confirmation dialog`
- `Cancel on the confirmation dialog calls nothing` — `verifyNever` on `reauthenticate`,
  `deleteAccount(any())`, `deleteIdentity`.
- `confirming runs reauthenticate, the backend purge, deleteIdentity and signOut in that order` —
  `verifyInOrder`; `deleteAccount` receives `'fake-token'`; no snackbar, no dialog.
- `a cancelled re-authentication returns silently and deletes nothing` — `reauthenticate` throws
  `ReauthCancelledException`; no snackbar; `verifyNever(deleteAccount)`; Delete account row visible again.
- `a failed re-authentication shows the identity snackbar and deletes nothing` — `reauthenticate` throws
  `Exception`; `Could not confirm your identity` shown; `verifyNever(deleteAccount)`, `verifyNever(signOut)`.
- `a failed backend purge shows the retry snackbar and keeps the user signed in` — `deleteAccount` throws;
  `Failed to delete your account` shown; `verifyNever(deleteIdentity)`, `verifyNever(signOut)`.
- `a failed identity deletion shows the contact-support dialog and signs out after OK` — `deleteIdentity`
  throws; dialog shown, `verifyNever(signOut)` before OK; after tapping OK `verify(signOut).called(1)`.
- `shows the loading state while deletion is in progress` — `reauthenticate` returns a `Completer` future;
  after confirming, `find.byType(LoadingWidget)` and no Delete account row; complete it and settle.

**Manual verification**
- Dev build (`flutter run --dart-define=DEV_AUTH_ENABLED=true`) against a local `dev`-profile backend
  (`recipai.sh`): sign in as `alice`, create a recipe, Settings → Delete account → Delete. Lands on Login;
  signing in as `alice` again shows no recipes. Repeat with Cancel on the dialog: recipe stays.
- Firebase build against a backend that validates Firebase tokens (see Risks): same flow with a real Google
  account — Google prompt appears, Login Screen afterwards, the user is gone from the Firebase console, a
  fresh sign-in shows an empty account. Cancelling the Google prompt leaves the recipe and shows nothing.
- Firebase build: choose a *different* Google account in the prompt — expect the `Could not confirm your
  identity` snackbar and no data deleted (task design assumption 2).
- Backend stopped: Delete → re-auth succeeds → `Failed to delete your account` snackbar, still signed in.
- Send logs afterwards: the trace shows the `recipai.settings.*` lines and no bearer token.

## Verification checklist

- [ ] `cd mobile && dart format --set-exit-if-changed lib test` and `flutter analyze` pass
- [ ] `cd mobile && flutter test` passes
- [ ] `tasks.md` > T4 "How to verify" succeeds end-to-end (Firebase build)
- [ ] Task design Assumptions to verify resolved or deferred: 1 (`delete()` signs out — SDK doc confirms
      "Deletes and signs out the user"; observe on device), 2 (`user-mismatch` — documented on
      `reauthenticateWithCredential`; observe on device), 3 (`authenticate()` while signed in — on device),
      4 (root `ScaffoldMessenger` — covered by the snackbar widget tests), 5 (only `setupAuth` +
      `setupSettings` — covered by the widget tests pumping)
- [ ] Logs at `INFO` on the happy path show the DELETE line and `Account deleted`, never the token
- [ ] The three module docs are updated

## Risks surfaced during planning

- **Risk:** `tasks.md` > T4 "How to verify" runs the Google re-authentication "against a backend built from
  T1", but a `dev`-profile backend replaces JWT validation with the dev decoder
  (`docs/backend/standards/configuration-profiles.md`), so a Firebase build cannot call it.
  **Why it matters:** the full Firebase path — the only one exercising re-auth and real identity deletion —
  has no documented local target.
  **Mitigation:** run the Firebase-build check against a backend without the `dev` profile and with the
  Firebase issuer configured (or a deployed T1 build); the dev build covers the purge locally. Confirm the
  target with the user before implementing step 5.
- **Risk:** the widget test's single-route router has no auth redirect and `watchAuthState` is stubbed with
  an empty stream, so the success path's "screen disposed mid-flow, `!mounted` return" is not exercised in
  tests.
  **Why it matters:** a regression that touches `context` after the await on the success path would only
  surface on device.
  **Mitigation:** covered by the manual dev-build run; acceptable per the task design's test scope.
- **Risk:** `AccountRepository` mirrors `InvitesRepository`, which does not log, while the logging standard
  says network logging lives in the repository; the plan adds the `ShoppingListItemRepository`-style INFO line.
  **Why it matters:** minor divergence from the "mirror `InvitesRepository`" note in the task design.
  **Mitigation:** none needed beyond review; the standard wins.
- **Unresolved assumptions 1–3** in the task design are SDK behaviours that only a device run can confirm;
  they are listed in the Verification checklist and the Manual verification above.
