# Feature Flags Standard

Feature flags are defined as `static bool` constants in `core/feature_flags.dart` using `bool.fromEnvironment()`.
This allows flags to be toggled at build time via `--dart-define` without touching source code.

## Defining a Flag

Add a static constant to the `FeatureFlags` class:

```dart
class FeatureFlags {
  static const bool myFeatureEnabled =
      bool.fromEnvironment('MY_FEATURE_ENABLED', defaultValue: false);
}
```

- Use `defaultValue: false` — flags are **opt-in** and off by default.
- Name the constant `<featureName>Enabled` (camelCase, `Enabled` suffix).
- Name the env variable `SCREAMING_SNAKE_CASE`.

## Using a Flag in UI

Import `feature_flags.dart` and guard the widget with an `if` expression:

```dart
import 'package:mobile/core/feature_flags.dart';

// Inside a build method:
if (FeatureFlags.myFeatureEnabled)
  MyFeatureWidget(),
```

## Guidelines

- Flags control **rendering** in UI code, or **implementation selection** in a `*_setup.dart` or `main.dart`
  composition root — never anything in between. A flag must never be read inside a service or repository body.
- Remove the flag and its usages once the feature is fully rolled out; do not leave dead flag checks in the codebase.
- Document every active flag in the table below.

## Active Flags

| Flag | Env Variable | Default | Description |
|---|---|---|---|
| `devAuthEnabled` | `DEV_AUTH_ENABLED` | `false` | In `auth_setup.dart`: selects `DevAuthRepository` over `FirebaseAuthRepository` and registers `DevAuthService` alongside it. In `main.dart`: skips `Firebase.initializeApp()` / `GoogleSignIn.initialize()`. The Login Screen renders the dev sign-in controls instead of the Google button whenever `DevAuthService` is registered — it reads no flag itself. Lets the app authenticate against a backend running the `dev` profile without Firebase credentials. |
