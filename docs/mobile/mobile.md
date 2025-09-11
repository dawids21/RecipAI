# Mobile App Overview - RecipAI

## Features

- `recipe` - Contains recipe data models, UI screens, and widgets for displaying recipe lists and details
- `extraction` - Recipe extraction functionality using WebView to browse recipe websites and extract recipe data
- `auth` - User authentication using Firebase Authentication with Google Sign-In

## Data Models

### Recipe module

- Recipe (`recipe.dart`) - Basic recipe data model with id and name
- Recipe Detail (`recipe_detail.dart`) - Complex nested structure for detailed recipe information

## Codebase Structure

```
mobile/
├── lib/
│   ├── main.dart                       # RecipAI app entry point with DI setup
│   ├── core/                           # Core services and configuration
│   │   ├── routes.dart                 # Go router configuration with AppRoute enum
│   │   ├── api_service.dart           # API service with InheritedApiService
│   │   ├── app_config.dart            # Application configuration
│   │   └── theme.dart                 # App theme and spacing constants
│   ├── shared/                         # Shared/reusable widgets and utilities
│   │   ├── loading_widget.dart        # Loading indicator widget
│   │   ├── api_error_widget.dart      # API error display widget
│   │   ├── error_message_widget.dart  # General error message widget
│   │   └── error_icon.dart           # Error icon widget
│   └── features/                       # Feature modules
│       ├── auth/                       # "authentication" feature
│       ├── recipe/                     # "recipe" feature
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

### Accessing Services in Widgets

```dart
class _MyScreenState extends State<MyScreen> {
  late ApiService _apiService;
  late AuthService _authService;

  @override
  void didChangeDependencies() {
    _apiService = InheritedApiService.of(context);
    _authService = InheritedAuthService.of(context);
    super.didChangeDependencies();
  }

  Future<void> _loadData() async {
    // Use cached service references
    final recipes = await _apiService.fetchRecipes();
  }

  Future<void> _signIn() async {
    await _authService.signIn();
  }
}
```