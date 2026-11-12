# T3: Mobile settings screen — Implementation Plan

**Date:** 2026-11-12

## Required reading

**Docs & standards** (from `docs/INDEX.md`)
- `docs/mobile/standards/architecture.md` > Widget Inputs, File Naming Conventions — the screen
  takes `AuthService` because it drives the logout action, and lives in `settings_screen.dart`.
- `docs/mobile/standards/navigation.md` — `AppRoute` enum entry, `context.goNamed`, services
  injected through the route builder closure.
- `docs/mobile/standards/widget-testing.md` — `setUp`/`tearDown` lifecycle, repository-only
  mocking, test-local `GoRouter`.
- `docs/mobile/standards/theming.md` — `Theme.of(context)` first, then `AppSpacing`, for the list.
- `docs/mobile/modules/core/module.md` > Services and State — the auth redirect and the
  session-scoped reset in `MainScreen.dispose` that logout relies on.

**Design & ADRs**
- `docs/tasks/2026-09-04-account-deletion/plans/T3-task-design.md` — the whole document, especially
  Components, Data flow and Assumptions to verify.
- `docs/tasks/2026-09-04-account-deletion/HLD.md` > Feature areas > Mobile — settings screen — the
  behaviours this task delivers.

**Code to mirror**
- `mobile/lib/core/main_screen.dart` — `_onLogoutTap` (dialog, `signOut`, snackbar) moved verbatim;
  overflow-menu `PopupMenuItem` shape for the new Settings row.
- `mobile/lib/core/routes.dart` — the `invites` enum value and nested `GoRoute` to mirror.
- `mobile/lib/features/invites/invites_screen.dart` — sibling screen: `StatefulWidget` taking its
  service through the constructor, `Scaffold` + `AppBar` shape.
- `mobile/lib/core/logging/share_logs.dart` — Send logs entry point, called as is.
- `mobile/test/features/invites/invites_screen_widget_test.dart` — sibling widget test: mock setup,
  `watchAuthState` stub, test-local router, dialog-button finders scoped to `AlertDialog`.

## File inventory

- **CREATE** `mobile/lib/features/settings/settings_screen.dart` — Settings screen with Logout and Send logs rows.
- **MODIFY** `mobile/lib/core/routes.dart` — `AppRoute.settings('settings')` and its nested `GoRoute` beside `invites`.
- **MODIFY** `mobile/lib/core/main_screen.dart` — drop Logout/Send logs items, `_onLogoutTap`, `shareLogs` import; add Settings item.
- **CREATE** `mobile/test/features/settings/settings_screen_widget_test.dart` — Logout path widget tests.

No change to `test/support/mocks.dart` (`MockAuthRepository` already exists), to any `*_setup.dart`
or to `main.dart` — T3 registers nothing in `get_it`.

## Step-by-step plan

1. **Settings screen, route and its widget test** — add `SettingsScreen` and wire it on `/settings`
   while the overflow menu still holds the old items, so the screen is testable on its own.
   - Files: `mobile/lib/features/settings/settings_screen.dart`, `mobile/lib/core/routes.dart`,
     `mobile/test/features/settings/settings_screen_widget_test.dart`
   - `SettingsScreen`: `StatefulWidget` with `final AuthService authService`. `build` returns a
     `Scaffold` with `AppBar(title: Text('Settings'), backgroundColor:
     theme.colorScheme.inversePrimary)` (as `MainScreen`) and a `ListView` of two `ListTile`s:
     `Icons.logout` / "Logout" → `_onLogoutTap(context)`; `Icons.bug_report` / "Send logs" →
     `_onSendLogsTap()`. `_onLogoutTap` is `MainScreen._onLogoutTap` copied verbatim (title
     "Logout", content "Are you sure you want to logout?", Cancel/Logout buttons popping
     `false`/`true`, snackbar `Failed to logout: $e`), with `widget.authService.signOut()`.
     `_onSendLogsTap` awaits `shareLogs()`.
   - `routes.dart`: add `settings('settings'), // '/settings'` after `invites`; add the `GoRoute`
     right after the `invites` one, building `SettingsScreen(authService: getIt<AuthService>())`;
     import `../features/settings/settings_screen.dart`.
   - Test: mirror the invites test's `setUp`, but register only `PreferencesService` and
     `setupAuth(authRepository: authRepository)`; stub `watchAuthState` (empty stream) and
     `getIdToken`. Router: a single `GoRoute` at `'/${AppRoute.settings.path}'` building
     `SettingsScreen(authService: GetIt.I<AuthService>())`.
   - Verify: `cd mobile && flutter test test/features/settings/settings_screen_widget_test.dart`
     passes; `cd mobile && flutter analyze` reports no issues.

2. **Move the entry point off the overflow menu** — Main Screen leads to Settings instead of
   offering Logout and Send logs.
   - Files: `mobile/lib/core/main_screen.dart`
   - Delete `_onLogoutTap`, the `'logout'` and `'send_logs'` `PopupMenuItem`s and their
     `onSelected` branches, and the `import 'logging/share_logs.dart';`. Add, after
     `generate_shopping_list`, a `PopupMenuItem<String>(value: 'settings')` with
     `Icon(Icons.settings)`, `SizedBox(width: AppSpacing.small)`, `Text('Settings')`, and the branch
     `else if (value == 'settings') context.goNamed(AppRoute.settings.name);`. Keep the `authService`
     field — `MealPlanDrawer` still reads it.
   - Verify: `cd mobile && flutter analyze` clean (no unused import/element); `cd mobile && flutter
     test` passes in full, including the untouched `main_screen_recipes_tab_widget_test.dart`.

3. **Manual check in the running app** — the task's "How to verify" on a device/emulator.
   - Files: none
   - Verify: run the app (Android, so Send logs is live). Overflow menu shows Invites, Recipes
     collections, Generate shopping list, Settings — no Logout, no Send logs. Settings opens on top of
     the Main Screen and back returns to it. Send logs opens the platform share sheet with a `.log`
     file. Logout → Cancel stays put; Logout → Logout lands on the Login Screen. Sign in again (a
     different dev identity if `devAuthEnabled`) and confirm the Recipes/Shopping tabs show the new
     account's data, not the previous one's — this settles the redirect/dispose assumption.

## Test plan

**Unit tests**
_N/A — no new service or logic; the screen only calls `AuthService.signOut` and `shareLogs()`._

**Integration tests**
_N/A — no backend change._

**Flutter widget/integration tests** (if applicable)
- `settings_screen_widget_test.dart`:
  - `renders Logout and Send logs rows`
  - `tapping Logout shows the confirmation dialog` — finds "Are you sure you want to logout?"
  - `Cancel closes the dialog without calling signOut` — `verifyNever(() => authRepository.signOut())`,
    dialog gone, screen still shown
  - `confirming calls signOut once` — stub `signOut` to complete; tap the `TextButton` "Logout"
    scoped with `find.descendant(of: find.byType(AlertDialog), …)` (the row and dialog title also
    read "Logout"); `verify(() => authRepository.signOut()).called(1)`
  - `a failed signOut shows the Failed to logout snackbar` — stub `signOut` to throw
    `Exception('boom')`; expect `find.textContaining('Failed to logout')`

**Manual verification**
- Step 3 above: menu contents, back navigation, Send logs share sheet (platform channel, not
  testable in a widget test), logout to Login Screen and clean session on re-sign-in.

## Verification checklist

- [ ] `cd mobile && dart format --output=none --set-exit-if-changed lib test` passes
- [ ] `cd mobile && flutter analyze` reports no issues
- [ ] `cd mobile && flutter test` passes in full
- [ ] `tasks.md` > T3 "How to verify" succeeds in the running app (step 3)
- [ ] Task design Assumptions to verify:
  - [ ] Logout from `/settings` reaches `/login` via the router redirect alone and the next account
        starts with fresh session-scoped lists — confirmed manually in step 3
  - [x] `main_screen_recipes_tab_widget_test.dart` has no Logout/Send logs/overflow-menu reference —
        confirmed during planning (grep finds none)
  - [ ] The settings test runs on `PreferencesService` + `setupAuth` alone — confirmed when step 1's
        test goes green (planning read: an injected repository skips the only `setupAuth` branch that
        reads preferences)

## Risks surfaced during planning

- **Risk:** the task design lists documentation deliverables (`docs/mobile/modules/settings/`,
  `core/ui.md`, `auth/ui.md`, `docs/INDEX.md`, `docs/project/architecture.md`).
  **Why it matters:** this plan covers code only; dropping the docs here would lose them.
  **Mitigation:** run `docs-updating` after the implementation lands, with the task design's Docs
  list as its input — the same handling as T1 and T2.

- **Risk:** the logout assumption can't be checked by a widget test. The test router has no
  `/login` route and no redirect, and the mocked auth stream never emits.
  **Why it matters:** if the redirect leaves `MainScreen` alive under `/login`, the session-scoped
  services aren't reset and the next account sees the previous one's lists.
  **Mitigation:** the step 3 manual re-sign-in check. If it fails, add
  `context.goNamed(AppRoute.login.name)` after `signOut` in `_onLogoutTap`, as the task design says.
