# Feature Flags Standard

Feature flags are `static bool` constants in `core/feature_flags.dart`. How they are set and which flags exist is in
`docs/mobile/modules/core/module.md` → Feature Flags.

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
- Document every active flag in `docs/mobile/modules/core/module.md` → Feature Flags.
