# T3: Mobile settings screen — Task Design

**Date:** 2026-11-12

## Summary

A new `settings` mobile feature adds a Settings screen on a nested `/settings` route, opened from the
Main Screen overflow menu. Logout (with its confirmation) and Send logs move off the overflow menu onto
it unchanged. The screen sits directly on `AuthService` with no service of its own; T4 extends it with
Delete account.

## Components and responsibilities

- **`SettingsScreen`** (CREATE, `mobile/lib/features/settings/settings_screen.dart`) — renders the
  account actions as a list: Logout and Send logs. Owns the logout confirmation dialog and the
  "Failed to logout" snackbar, carried over verbatim from `MainScreen._onLogoutTap`. Send logs calls
  the existing `shareLogs()`.
- **`AppRoute.settings`** (MODIFY, `mobile/lib/core/routes.dart`) — new enum value `settings('settings')`
  (`/settings`), a `GoRoute` nested under `AppRoute.main` beside `invites`, building `SettingsScreen`
  with `getIt<AuthService>()`.
- **`MainScreen`** (MODIFY, `mobile/lib/core/main_screen.dart`) — removes `_onLogoutTap`, the `logout`
  and `send_logs` menu items and their `onSelected` branches, and the `logging/share_logs.dart`
  import; adds a `settings` menu item (`Icons.settings`, "Settings") that navigates to
  `AppRoute.settings`. Keeps its `authService` parameter: `MealPlanDrawer` still uses it.
- **`shareLogs()`** (unchanged, `mobile/lib/core/logging/share_logs.dart`) — stays in `core`; only its
  caller moves.
- **Settings widget test** (CREATE, `mobile/test/features/settings/settings_screen_widget_test.dart`) —
  covers the Logout path.
- **Docs**:
  - CREATE `docs/mobile/modules/settings/module.md` (description, boundary, file map) and `ui.md`
    (the screen, the Logout confirmation, Send logs).
  - MODIFY `docs/mobile/modules/core/ui.md`: overflow menu contents, the *Logout* bullet moved out,
    `/settings` added to the route list.
  - MODIFY `docs/mobile/modules/auth/ui.md` > Flow step 4: Main Screen → Settings → Logout.
  - MODIFY `docs/INDEX.md` and `docs/project/architecture.md`: list the new mobile module.

No `settings_setup.dart`: T3 registers nothing in `get_it`.

## Interfaces and method signatures

```dart
// lib/features/settings/settings_screen.dart
class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key, required AuthService authService});
}

class _SettingsScreenState extends State<SettingsScreen> {
  Future<void> _onLogoutTap(BuildContext context);   // moved from MainScreen
  Future<void> _onSendLogsTap();                     // => shareLogs()
}

// lib/core/routes.dart
enum AppRoute { ..., settings('settings'), ... }     // '/settings'

GoRoute(
  path: AppRoute.settings.path,
  name: AppRoute.settings.name,
  builder: (context, state) => SettingsScreen(authService: getIt<AuthService>()),
)
```

The screen takes `AuthService` itself, not a callback, because it drives the logout action (see
`docs/mobile/standards/architecture.md` > Widget Inputs).

## Data flow

1. Main Screen overflow menu → "Settings" → `context.goNamed(AppRoute.settings.name)` pushes
   `/settings` on top of the Main Screen.
2. **Logout**: tap → confirmation dialog → *Cancel* closes it with nothing changed; *Logout* →
   `AuthService.signOut()`. The auth-state stream flips `isAuthenticated`, the router redirect sends
   every route to `/login`, and both `SettingsScreen` and `MainScreen` are disposed (`MainScreen.dispose`
   resets the session-scoped services as today). A thrown `signOut` shows the existing snackbar.
3. **Send logs**: tap → `shareLogs()` → `recipai/share` `shareFile` with the current log file (Android);
   a no-op elsewhere, as today.

## Pseudo-code

_None — logic is straightforward; both actions are moved, not rewritten._

## Decisions made

- **New `features/settings/` module, not `core/` or `auth/`** — T4 adds a backend deletion call here, and
  the architecture standard reserves `core/` for code with no feature identity; it also mirrors the
  backend `settings` module.
- **No service for the screen in T3** — Logout and Send logs need only `AuthService` and `shareLogs()`.
  Whether account deletion needs a dedicated service is the HLD open question, decided in T4.
- **`shareLogs()` stays in `core/logging`** — it belongs to the logging infrastructure; only the entry
  point moves.
- **Nested `/settings` route under `/`** — matches `/invites` and the other overflow-menu destinations,
  so back returns to the Main Screen.
- **Widget test covers Logout only** — dialog shown, Cancel does not call `AuthRepository.signOut`,
  confirm calls it once. Send logs goes through a platform channel and stays a manual check. T4 extends
  the same test file.
- **Behaviour carried over unchanged** — dialog title/copy/buttons, snackbar text, and Send logs still
  visible on non-Android platforms, per the task's out-of-scope.

## Assumptions to verify

- **Assumption:** signing out from the nested `/settings` route is handled entirely by the existing
  router redirect, with `MainScreen.dispose` still running and resetting the session-scoped services.
  **If wrong:** the screen needs an explicit `context.goNamed(AppRoute.login.name)` after `signOut`,
  or a session-scoped list survives into the next account.
- **Assumption:** `main_screen_recipes_tab_widget_test.dart` doesn't depend on the overflow menu items
  being removed (a grep finds no Logout/Send logs reference in it).
  **If wrong:** that test needs updating alongside the menu change.
- **Assumption:** the settings widget test can pump `SettingsScreen` with only `setupAuth` (plus
  `PreferencesService`) registered, because the screen touches no other service.
  **If wrong:** the test needs the full `setup*()` chain from `docs/mobile/standards/widget-testing.md`.

## Required reading

- `HLD.md` > Feature areas > Mobile — settings screen — the behaviours this task delivers
- `mobile/lib/core/main_screen.dart` — the overflow menu and `_onLogoutTap` being moved
- `mobile/lib/core/routes.dart` — the `invites` route to mirror for `settings`
- `mobile/lib/core/logging/share_logs.dart` — the Send logs entry point, reused as is
- `mobile/test/features/invites/invites_screen_widget_test.dart` — sibling widget test to mirror
- `docs/mobile/standards/architecture.md`, `navigation.md`, `widget-testing.md` — layering, routes, test
  shape
- `docs/mobile/modules/invites/module.md` — sibling module doc layout for the new `settings` docs
