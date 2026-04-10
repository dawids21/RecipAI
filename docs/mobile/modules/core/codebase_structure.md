# Core — Codebase Structure

```
mobile/
├── lib/
│   ├── main.dart                       # RecipAI app entry point with DI setup
│   ├── core/                           # Core services and configuration
│   │   ├── routes.dart                 # Go router configuration with AppRoute enum and simple GoRoute structure
│   │   ├── main_screen.dart            # Main screen with embedded bottom navigation
│   │   ├── app_config.dart             # Application configuration
│   │   ├── async_value.dart            # AsyncValue sealed class (Loading/Data/Error)
│   │   ├── get_it.dart                 # Global GetIt instance
│   │   ├── feature_flags.dart          # Feature flags configuration using environment variables
│   │   ├── preferences_service.dart    # SharedPreferences wrapper for local storage (recipe filter, plan visibility)
│   │   ├── theme.dart                  # App theme and spacing constants
│   │   └── widgets/                    # Reusable widgets shared across features
│   │       └── sharing_dialog.dart     # Generic sharing dialog with SharedUser DTO
│   ├── shared/                         # Shared/reusable widgets and utilities
│   │   ├── loading_widget.dart         # Loading indicator widget
│   │   ├── api_error_widget.dart       # API error display widget
│   │   ├── error_message_widget.dart   # General error message widget
│   │   ├── error_icon.dart             # Error icon widget
│   │   ├── serving_size_input.dart     # Reusable serving size input widget with increment/decrement controls
│   │   ├── extensions.dart             # Extension methods (IsoDateFormat for DateTime, ColorExtension for Color)
│   │   └── user_role.dart              # UserRole enum with API conversion methods
├── assets/
│   └── config/
│       └── app_config.json             # App configuration file
├── android/                            # Android-specific configuration and native code
├── test/
│   └── widget_test.dart                # Smoke test
├── pubspec.yaml                        # Flutter dependencies and project configuration
└── analysis_options.yaml              # Dart/Flutter linting rules
```
