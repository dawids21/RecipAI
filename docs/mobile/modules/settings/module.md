# Settings Module

The signed-in user's account actions, gathered on one screen reached from the Main Screen's overflow menu: logging out
and sending the app's logs.

## Boundary

- **Backend endpoints**: none.
- **Uses**: `auth` (`AuthService` signs the user out), `core` (the Send logs share in `core/logging/`).
- **Used by**: `core` (Main Screen menu row, `/settings` route).

## File Map

```
mobile/lib/features/settings/
└── settings_screen.dart   # The /settings screen with the account actions
```

```
mobile/test/features/settings/
└── settings_screen_widget_test.dart
```

## Services and State

- **No service of its own.** The screen drives `AuthService` directly and holds no state beyond its dialog.
- **Sign-out leaves the screen by redirect.** The screen does not navigate after signing out: the auth redirect
  sends every route to `/login`, which disposes the Main Screen beneath it and resets the session-scoped services
  (see `docs/mobile/modules/core/module.md` → Services and State).
