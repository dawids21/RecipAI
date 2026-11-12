# T4: Mobile account deletion flow — Task Design

**Date:** 2026-11-12

## Summary

The Settings screen gains Delete account. A new `AccountDeletionService` in `features/settings/` runs the
flow — Google re-authentication, `DELETE /settings/account`, Firebase identity deletion, sign-out — and
returns one `AccountDeletionOutcome` per HLD path, which the screen maps to silence, a snackbar or a
blocking dialog. `AuthRepository` gains `reauthenticate()` and `deleteIdentity()`, implemented for both the
Firebase and the dev build.

## Components and responsibilities

- **`AccountRepository`** (CREATE, `mobile/lib/features/settings/account_repository.dart`) — the
  `DELETE /settings/account` call; 204 is success, anything else throws. Mirrors `InvitesRepository`.
- **`AccountDeletionService`** (CREATE, `mobile/lib/features/settings/account_deletion_service.dart`) — owns
  the deletion order and the mapping of every failure to an outcome; logs each failure. Guards against
  concurrent runs. Holds no notifiers.
- **`AccountDeletionOutcome`** (CREATE, same file as the service) — the enum the screen switches on.
- **`settings_setup.dart`** (CREATE, `mobile/lib/features/settings/`) — `setupSettings({AccountRepository?})`
  registers the repository and the service as singletons (not session-scoped: the service must outlive the
  screen, which the success path disposes mid-flow).
- **`AuthRepository`** (MODIFY, `mobile/lib/features/auth/auth_repository.dart`) — two new contract
  methods, plus `ReauthCancelledException`.
  - `FirebaseAuthRepository`: `reauthenticate()` runs `GoogleSignIn.authenticate()` and
    `currentUser.reauthenticateWithCredential(...)`; a `GoogleSignInException` with code `canceled` becomes
    `ReauthCancelledException`. `deleteIdentity()` calls `currentUser.delete()`.
  - `DevAuthRepository` (`dev_auth_repository.dart`): `reauthenticate()` completes immediately;
    `deleteIdentity()` behaves as `signOut()`.
- **`AuthService`** (MODIFY, `mobile/lib/features/auth/auth_service.dart`) — pass-through `reauthenticate()`
  and `deleteIdentity()`.
- **`SettingsScreen`** (MODIFY, `mobile/lib/features/settings/settings_screen.dart`) — takes
  `AccountDeletionService`; adds a Delete account row below Logout, the confirmation dialog, the in-progress
  state (`LoadingWidget` + `PopScope(canPop: false)`), and the outcome handling.
- **`AppRoute.settings` builder** (MODIFY, `mobile/lib/core/routes.dart`) — passes
  `getIt<AccountDeletionService>()`.
- **`main.dart`** (MODIFY) — calls `setupSettings()` after `setupAuth()`.
- **Tests** (MODIFY `mobile/test/features/settings/settings_screen_widget_test.dart`, `mobile/test/support/mocks.dart`)
  — `MockAccountRepository`; one test per outcome plus dialog dismissal.
- **Docs**:
  - MODIFY `docs/mobile/modules/settings/module.md` — backend endpoint, file map, the service and its
    lifetime.
  - MODIFY `docs/mobile/modules/settings/ui.md` — the Delete account row, its dialogs and messages.
  - MODIFY `docs/mobile/modules/auth/module.md` — the two new repository methods and their dev behaviour.

## Interfaces and method signatures

```dart
// features/auth/auth_repository.dart
abstract class AuthRepository {
  ...
  /// Fresh Google sign-in for the current user. Throws ReauthCancelledException
  /// when the user dismisses the prompt; any other exception is a failure.
  Future<void> reauthenticate();

  /// Deletes the signed-in identity. Requires a recent reauthenticate().
  Future<void> deleteIdentity();
}

class ReauthCancelledException implements Exception { const ReauthCancelledException(); }

// features/auth/auth_service.dart
Future<void> reauthenticate();
Future<void> deleteIdentity();

// features/settings/account_repository.dart
class AccountRepository {
  Future<void> deleteAccount(String? idToken);   // DELETE /settings/account, expects 204
}

// features/settings/account_deletion_service.dart
enum AccountDeletionOutcome { deleted, cancelled, reauthFailed, purgeFailed, identityDeletionFailed }

class AccountDeletionService {
  AccountDeletionService({required AccountRepository accountRepository, required AuthService authService});

  Future<AccountDeletionOutcome> deleteAccount();
  Future<void> signOut();   // called by the screen after the identityDeletionFailed dialog
}

// features/settings/settings_screen.dart
const SettingsScreen({super.key, required AuthService authService,
                      required AccountDeletionService accountDeletionService});
```

## Data flow

1. Delete account → confirmation dialog stating deletion is immediate and irreversible. *Cancel* ends the flow.
2. *Delete* → screen enters the in-progress state → `AccountDeletionService.deleteAccount()`.
3. Service: `reauthenticate()` → `accountRepository.deleteAccount(await authService.idToken)` →
   `deleteIdentity()` → `authService.signOut()` (clears the cached Google account; Firebase is already
   signed out by `delete()`).
4. On success, `delete()` makes the auth stream emit `null`; the router redirect sends every route to
   `/login` and disposes the screen while the service finishes step 3. The screen does nothing with
   `deleted`.
5. Any other outcome returns the screen to its list and is handled per the pseudo-code below.

## Pseudo-code

```
service.deleteAccount():
    if running: return cancelled           # double entry is a no-op
    running = true
    try:
        try: await auth.reauthenticate()
        on ReauthCancelledException: return cancelled
        on any: log; return reauthFailed

        try: await repo.deleteAccount(await auth.idToken)
        on any: log; return purgeFailed     # still signed in, retry is meaningful

        try: await auth.deleteIdentity()
        on any: log; return identityDeletionFailed   # data gone; screen shows dialog, then signOut()

        try: await auth.signOut()
        on any: log                         # identity is gone; nothing left to report
        return deleted
    finally: running = false

screen._onDeleteAccountTap():
    if not await confirmDialog(): return
    messenger = ScaffoldMessenger.of(context)   # captured before awaits
    setState(deleting = true)
    outcome = await service.deleteAccount()
    if not mounted: return                      # deleted path: redirect already disposed us
    setState(deleting = false)
    switch outcome:
        deleted, cancelled: nothing
        reauthFailed:  messenger.snackbar('Could not confirm your identity. Nothing was deleted.')
        purgeFailed:   messenger.snackbar('Failed to delete your account. Please try again.')
        identityDeletionFailed:
            await blockingDialog('Your data was deleted, but something went wrong. Please contact support.')
            await service.signOut()             # redirect to /login
```

The exact copy is the implementer's; the meaning of each message is fixed by the HLD.

## Decisions made

- **Dedicated `AccountDeletionService`** — resolves the HLD open question. The HLD's alternative (screen on
  `AuthService` plus a repository) breaks the architecture standard's "views cannot access repositories"
  rule, and the service keeps the load-bearing order (ADR-0009) in one testable place.
- **Re-authentication and identity deletion on `AuthRepository`** — they are Firebase/Google SDK calls and
  belong behind the existing auth abstraction, not in `settings`.
- **Dev build: no-op re-authentication, identity deletion = sign-out, backend purge still runs** — keeps the
  whole flow, including the real purge, exercisable against a dev-profile backend.
- **Outcome enum, not exceptions or `AsyncValue`** — a one-shot action with five distinct results; an
  exhaustive `switch` turns a missed path into a compile error.
- **Identity-deletion failure is a blocking dialog, then sign-out** — a "contact support" message must not
  be missed; the screen triggers the sign-out only after the dialog is dismissed, so the service exposes
  `signOut()` for that one step.
- **Screen blocks during deletion** — `LoadingWidget` plus `PopScope(canPop: false)` from confirmation to
  outcome, mirroring `LoginScreen`'s loading state; prevents double taps and a lost failure message.
- **Service registered as a plain singleton in `settings_setup.dart`** — the success path disposes the
  screen before the service finishes, so it must not be screen- or session-scoped.
- **Widget tests only, through repository mocks** — per `docs/mobile/standards/widget-testing.md`; the real
  service runs on top of `MockAuthRepository` and `MockAccountRepository`.

## Assumptions to verify

- **Assumption:** `User.delete()` emits `null` on `userChanges()`, so the redirect fires on success without
  an explicit sign-out.
  **If wrong:** the trailing `authService.signOut()` is what leaves the screen; behaviour is unchanged but
  the data-flow note in `module.md` must say so.
- **Assumption:** choosing a different Google account in the re-authentication prompt makes
  `reauthenticateWithCredential` throw (`user-mismatch`) and is treated as `reauthFailed`.
  **If wrong:** the flow could delete the wrong identity's data; the repository must compare emails first.
- **Assumption:** `GoogleSignIn.authenticate()` can be called again while a Firebase session already exists
  and returns a fresh ID token usable for `GoogleAuthProvider.credential`.
  **If wrong:** `reauthenticate()` needs `signOut()` on `GoogleSignIn` first or a different SDK call.
- **Assumption:** the root `ScaffoldMessenger` from `MaterialApp.router` serves the snackbars.
  **If wrong:** the screen needs its own `ScaffoldMessenger`; behaviour is unchanged.
- **Assumption:** the widget test can pump `SettingsScreen` with only `setupAuth` and `setupSettings`
  registered.
  **If wrong:** the test needs the fuller `setup*()` chain.

## Required reading

- `HLD.md` > Feature areas > Mobile — account deletion flow — the behaviours and failure paths
- `docs/ADRs/0009-client-side-firebase-identity-deletion.md` — why the order is backend first, identity second
- `plans/T3-task-design.md` — the screen and route this task extends
- `mobile/lib/features/settings/settings_screen.dart` — the logout dialog and snackbar to mirror
- `mobile/lib/features/auth/auth_repository.dart`, `dev_auth_repository.dart` — implementations gaining the
  new methods
- `mobile/lib/features/invites/invites_repository.dart`, `invites_setup.dart` — HTTP repository and setup
  pattern
- `mobile/lib/features/auth/login_screen.dart` — loading-state pattern
- `backend/src/main/java/xyz/stasiak/recipai/settings/SettingsController.java` — the endpoint contract (204)
- `docs/mobile/standards/architecture.md`, `dependency-injection.md`, `widget-testing.md`
