# Mobile App Overview - RecipAI

## Features

- `recipe` - Contains recipe data models, UI screens, and widgets for displaying recipe lists and details

## Codebase Structure

```
mobile/
├── lib/
│   ├── main.dart                       # RecipAI app entry point with MaterialApp setup
│   ├── core/                           # Core services and configuration
│   │   ├── api_service.dart           # API service for backend communication
│   │   └── app_config.dart            # Application configuration
│   └── features/                       # Feature modules
│       └── recipe/                     # Recipe feature
├── assets/
│   └── config/
│       └── app_config.json            # App configuration file
├── android/                            # Android-specific configuration and native code
├── test/
│   └── widget_test.dart               # Widget and unit tests
├── pubspec.yaml                       # Flutter dependencies and project configuration
└── analysis_options.yaml             # Dart/Flutter linting rules
```