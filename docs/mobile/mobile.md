# Mobile App Overview - RecipAI

## Modules

- `config` - Manages application configuration including API base URL with support for environment variables and JSON
  config files
- `services` - Provides HTTP API communication services for fetching data from the backend
- `recipe` - Contains recipe data models, UI screens, and widgets for displaying recipe lists and details

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