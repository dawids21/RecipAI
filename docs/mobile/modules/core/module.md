# Core Module

The app shell: `main.dart` (start-up and DI wiring), routing and the auth redirect, the Main Screen with its
bottom-navigation tabs, and the app-wide infrastructure every feature builds on (`AsyncValue`, `get_it`, preferences,
the scheduler, theme, logging, feature flags). `shared/` holds widgets and helpers used by more than one feature.

## Boundary

- **Backend endpoints**: none of its own.
- **Uses**: `auth` (`AuthService.isAuthenticated` drives the router redirect; Login Screen), and every feature's
  screens and services, which the router and the Main Screen construct and inject.
- **Used by**: every feature (`get_it`, `AsyncValue`, `PreferencesService`, `Scheduler`, theme, `shared/` widgets,
  `AppRoute`).

## File Map

```
mobile/
├── lib/
│   ├── main.dart                       # Entry point: Firebase (skipped in a dev-auth build), config, logging, DI setup order
│   ├── firebase_options.dart           # Generated Firebase configuration
│   ├── core/
│   │   ├── routes.dart                 # AppRoute enum, GoRouter with the auth redirect; builds every screen with its services
│   │   ├── main_screen.dart            # Bottom-navigation shell; owns session-scoped services (see Services and State)
│   │   ├── app_config.dart             # Loads assets/config/app_config.json (API base URL)
│   │   ├── async_value.dart            # AsyncValue sealed class (Loading/Data/Error) and guardAsync
│   │   ├── get_it.dart                 # Global GetIt instance
│   │   ├── feature_flags.dart          # Compile-time flags (see Feature Flags)
│   │   ├── preferences_service.dart    # SharedPreferences wrapper, the only local key-value store (see its standard)
│   │   ├── scheduler.dart              # Scheduler/ScheduledTimer over dart:async Timer, injectable for tests (ADR-0005)
│   │   ├── theme.dart                  # AppTheme, AppSpacing and AppAnimations
│   │   └── logging/                    # Always-on log capture and the share action (see Logging standard)
│   │       ├── logging_setup.dart      # Wires the root logger to AppLogSink and registers the sink
│   │       ├── app_log_sink.dart       # Rotating file sink (~1 MB active + 1 backup) under the app support dir
│   │       └── share_logs.dart         # Shares the current log file via the recipai/share platform channel (Android only)
│   └── shared/
│       ├── loading_widget.dart
│       ├── api_error_widget.dart       # Error display with a Retry button
│       ├── error_message_widget.dart
│       ├── error_icon.dart
│       ├── serving_size_input.dart     # Increment/decrement serving-size control, minimum 1
│       ├── extensions.dart             # DateTime locale/ISO formatting, URL detection and extraction, Color helpers
│       └── user_role.dart              # UserRole enum (OWNER/EDITOR) with API conversion
├── assets/
│   └── config/
│       └── app_config.json             # App configuration (API base URL)
├── test/                               # Mirrors lib/ (see Widget Testing standard); feature tests are in each module.md
│   ├── shared/
│   │   └── extensions_test.dart        # URL detection and in-text URL extraction
│   └── support/
│       └── mocks.dart                  # mocktail Mock* declarations for every repository
├── android/                            # Android configuration and native code (share target, share-logs channel)
├── pubspec.yaml
└── analysis_options.yaml
```

## Services and State

- **App-lifetime singletons** registered in `main()`: `PreferencesService`, the shopping-list item `Database`
  (opened before any feature setup; see `docs/mobile/modules/shopping_list/module.md`) and `AppLogSink`. Feature
  `setup*()` functions run in a fixed order after them, because later features resolve earlier ones' services:
  auth → settings → limits → recipe → collections → planning → shopping list → invites → extraction, then the
  share-intent handler, which needs the router.
- **Auth redirect.** The router listens to `AuthService.isAuthenticated`: signed out, every route redirects to
  `/login`; signed in, `/login` redirects to `/`.
- **Session scope.** The Main Screen lives exactly as long as the user is signed in, since sign-out redirects away
  from it. It loads the recipe, collection, shopping list, meal plan and invite lists once when it first builds, and
  on dispose resets the lazy singletons that hold them (`RecipeListService`, `RecipesCollectionListService`,
  `ShoppingListListService`, `MealPlanListService`, `MealPlanCalendarService`, `MealPlanVisibilityService`,
  `InvitesService`). "Session-scoped" in the other modules' docs means this; the next sign-in builds fresh
  instances, so no list leaks from one account to the next.

## Feature Flags

`FeatureFlags` holds compile-time `bool` constants read with `bool.fromEnvironment()`, so a flag is set per build with
`--dart-define=<BUILD_VARIABLE>=true` and cannot change at runtime. The rules for adding, reading and removing a flag
are in `docs/mobile/standards/feature-flags.md`.

| Flag             | Build variable     | Default | What it changes                                                                                                                                                                                   |
|------------------|--------------------|---------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `devAuthEnabled` | `DEV_AUTH_ENABLED` | `false` | Signs in by name against a `dev`-profile backend instead of Firebase: `main.dart` skips Firebase and Google Sign-In initialisation, and `setupAuth` picks the dev repository (see `docs/mobile/modules/auth/module.md` → Services and State). |
