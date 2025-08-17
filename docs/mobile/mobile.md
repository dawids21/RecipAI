# Mobile App Overview - RecipAI

## Features

- `recipe` - Contains recipe data models, UI screens, and widgets for displaying recipe lists and details
- `import` - Recipe import functionality using WebView to browse recipe websites and extract recipe data

## Data Models

### Recipe module

- Recipe (`recipe.dart`) - Basic recipe data model with id and name
- Recipe Detail (`recipe_detail.dart`) - Complex nested structure for detailed recipe information

## Codebase Structure

```
mobile/
├── lib/
│   ├── main.dart                       # RecipAI app entry point with MaterialApp.router setup
│   ├── core/                           # Core services and configuration
│   │   ├── routes.dart                 # Go router configuration with AppRoute enum
│   │   ├── api_service.dart           # API service for backend communication
│   │   ├── app_config.dart            # Application configuration
│   │   └── theme.dart                 # App theme and spacing constants
│   ├── shared/                         # Shared/reusable widgets and utilities
│   │   ├── loading_widget.dart        # Loading indicator widget
│   │   ├── api_error_widget.dart      # API error display widget
│   │   ├── error_message_widget.dart  # General error message widget
│   │   └── error_icon.dart           # Error icon widget
│   └── features/                       # Feature modules
│       ├── recipe/                     # Recipe feature
│       └── import/                     # Recipe import feature
├── assets/
│   └── config/
│       └── app_config.json            # App configuration file
├── android/                            # Android-specific configuration and native code
├── test/
│   └── widget_test.dart               # Widget and unit tests
├── pubspec.yaml                       # Flutter dependencies and project configuration
└── analysis_options.yaml             # Dart/Flutter linting rules
```