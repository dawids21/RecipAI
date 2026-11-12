# Settings Module

The signed-in user's account actions, gathered on one screen reached from the Main Screen's overflow menu: logging out,
sending the app's logs and deleting the account.

## Boundary

- **Backend endpoints**: `DELETE /settings/account` (`docs/backend/modules/settings/api.md`).
- **Uses**: `auth` (`AuthService` signs the user out, re-authenticates them and deletes their identity), `core` (the
  Send logs share in `core/logging/`).
- **Used by**: `core` (Main Screen menu row, `/settings` route).

## File Map

```
mobile/lib/features/settings/
├── account_deletion_service.dart   # Runs account deletion and maps each failure to an outcome (see Services and State)
├── account_repository.dart         # The backend account purge call
├── settings_screen.dart            # The /settings screen with the account actions
└── settings_setup.dart
```

```
mobile/test/features/settings/
└── settings_screen_widget_test.dart
```

## Services and State

- **`AccountDeletionService`** — app-lifetime singleton, not screen- or session-scoped: on success the auth redirect
  disposes the screen while the service is still finishing. Holds only a guard that turns a second concurrent run
  into `cancelled`. It runs the steps in a fixed order — Google re-authentication, backend purge, identity deletion,
  sign-out — because purging first can only fail towards "data gone, identity alive" (see
  `docs/ADRs/0009-client-side-firebase-identity-deletion.md`). Each step's failure stops the flow and becomes one
  outcome:
  - re-authentication dismissed → `cancelled`; failed for any other reason → `reauthFailed`; nothing is deleted;
  - backend purge failed → `purgeFailed`; the user stays signed in and can retry;
  - identity deletion failed → `identityDeletionFailed`; the data is already gone, so the screen reports it and then
    calls the service's `signOut()`;
  - a failure of the closing sign-out is logged and ignored — the identity is already gone.
- **Leaving the screen is the redirect's job.** The screen does not navigate after signing out or deleting: the auth
  redirect sends every route to `/login`, which disposes the Main Screen beneath it and resets the session-scoped
  services (see `docs/mobile/modules/core/module.md` → Services and State). Deleting the identity signs Firebase out
  by itself; the closing sign-out clears the cached Google account.
- **Local data stays.** Deletion does not clear the device's shopping-list store, log files or preferences.
