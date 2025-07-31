# Mobile App Overview - RecipAI

## Modules

- `config` -
- `services` -
- `recipe` -

## Codebase Structure

```
mobile/
├── lib/
│   ├── main.dart                       # RecipAI app entry point with MaterialApp setup
│   ├── config/                         # "config" module
│   ├── recipe/                         # "recipe" module
│   └── services/                       # "services" module
├── android/                            # Android-specific configuration and native code
├── test/
│   └── widget_test.dart               # Widget and unit tests
├── pubspec.yaml                       # Flutter dependencies and project configuration
└── analysis_options.yaml             # Dart/Flutter linting rules
```