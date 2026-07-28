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
│   │   ├── scheduler.dart              # Scheduler/ScheduledTimer abstraction over dart:async Timer, injected into services for testability (ADR-0005)
│   │   ├── theme.dart                  # App theme and spacing constants
│   │   ├── logging/                    # Logging infrastructure (capture always on; flag gates share UI only)
│   │   │   ├── logging_setup.dart      # Wires root logger to AppLogSink, registers sink in get_it
│   │   │   ├── app_log_sink.dart       # Rotating file sink (~1 MB active + 1 backup) under app support dir
│   │   │   └── share_logs.dart         # Shares current log file via recipai/share platform channel (Android only)
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
│   └── support/
│       └── mocks.dart                  # mocktail Mock* class declarations for all repositories
├── pubspec.yaml                        # Flutter dependencies and project configuration
└── analysis_options.yaml              # Dart/Flutter linting rules
```
