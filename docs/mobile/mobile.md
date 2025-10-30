# Mobile App Overview - RecipAI

## Features

- `recipe` - Contains recipe data models, UI screens, and widgets for displaying recipe lists and details
- `extraction` - Recipe extraction functionality supporting both URL extraction via WebView and image extraction via
  camera/gallery
- `auth` - User authentication using Firebase Authentication with Google Sign-In

## Data Models

### Recipe module

- Recipe (`recipe.dart`) - Basic recipe data model with id and name
- Recipe Detail (`recipe_detail.dart`) - Complex nested structure for detailed recipe information including UserRole
  enum (
  owner/editor)
- Shared User (`shared_user.dart`) - Data model for recipe sharing API responses containing email and UserRole enum
- User Role (`user_role.dart`) - Enum defining user roles (owner, editor) with API conversion methods for
  uppercase/lowercase handling

## Codebase Structure

```
mobile/
├── lib/
│   ├── main.dart                       # RecipAI app entry point with DI setup
│   ├── core/                           # Core services and configuration
│   │   ├── routes.dart                 # Go router configuration with AppRoute enum
│   │   ├── api_service.dart           # API service with InheritedApiService
│   │   ├── app_config.dart            # Application configuration
│   │   ├── feature_flags.dart         # Feature flags configuration using environment variables
│   │   └── theme.dart                 # App theme and spacing constants
│   ├── shared/                         # Shared/reusable widgets and utilities
│   │   ├── loading_widget.dart        # Loading indicator widget
│   │   ├── api_error_widget.dart      # API error display widget
│   │   ├── error_message_widget.dart  # General error message widget
│   │   ├── error_icon.dart           # Error icon widget
│   │   └── user_role.dart            # UserRole enum with API conversion methods
│   └── features/                       # Feature modules
│       ├── auth/                       # "authentication" feature
│       ├── recipe/                     # "recipe" feature
│       │   ├── recipe_repository.dart  # Recipe data access layer with sharing operations
│       │   ├── recipe_list_service.dart # Recipe list business logic with ValueNotifier
│       │   ├── recipe_detail_service.dart # Recipe detail and sharing business logic with ValueNotifier
│       │   ├── recipe_setup.dart       # Dependency injection setup for recipe module
│       │   └── ...                     # Screens, models, and widgets
│       └── extraction/                 # "extraction" feature
├── assets/
│   └── config/
│       └── app_config.json            # App configuration file
├── android/                            # Android-specific configuration and native code
├── test/
│   └── widget_test.dart               # Smoke test
├── pubspec.yaml                       # Flutter dependencies and project configuration
└── analysis_options.yaml             # Dart/Flutter linting rules
```

## Usage Patterns

### Using Feature Flags

```dart
import 'package:mobile/core/feature_flags.dart';

class _MyScreenState extends State<MyScreen> {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Column(
        children: [
          // Always visible content
          Text('Main content'),

          // Conditionally rendered based on feature flag
          if (FeatureFlags.newFeatureEnabled)
            Container(
              padding: EdgeInsets.all(16),
              child: Text('This is a new experimental feature!'),
            ),
        ],
      ),
    );
  }
}
```