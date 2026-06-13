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

- Flags control **rendering only** — do not gate repository or service logic behind flags.
- Remove the flag and its usages once the feature is fully rolled out; do not leave dead flag checks in the codebase.
- Document every active flag in the table below.

## Active Flags

| Flag | Env Variable | Default | Description |
|---|---|---|---|
| `loggingEnabled` | `LOGGING_ENABLED` | `false` | Gates the "Send logs" item in the main-screen app-bar overflow menu. Log capture is always on; only the share UI is behind the flag. |
