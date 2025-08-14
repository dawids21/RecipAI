# AI Rules for RecipAI mobile

## Tech Stack

- Dart SDK 3.8.1
- Flutter 3.32
- Cupertino Icons 1.0.8
- HTTP 1.1.0 - HTTP client for API communication
- Flutter Lints 5.0.0 (dev dependency)
- Flutter Test (dev dependency)

## Coding Practices

### Modular Architecture

- Code should be split by feature not by layer e.g. all widgets, screens, models related to recipes should
  be in `features/recipe/` folder with no sub-folders for widgets, models, screens, etc.
- All files related to a feature are placed directly in the single feature directory
- Each module should have all required classes to provide a single feature

## Architecture Patterns

- **API Service**: Singleton pattern with HTTP client
- **Error Handling**: Try-catch blocks with user-friendly error messages

### Theming and Styling

- **Hardcoded Values**: If some value is very specific to a widget and not used anywhere else, it can be hardcoded in a
  widget
- **Theme Values**: Always prefer values from `Theme.of(context)` (e.g., `theme.textTheme.bodyLarge`,
  `theme.colorScheme.primary`)
- **Constants**: If Theme.of() doesn't provide a suitable value, look for constants in `core/theme.dart`
- **New Constants**: If no suitable constant exists, create a new one in `core/theme.dart` following Material Design 3
  8dp grid system but only if it is generic enough to be reused across multiple widgets
- **Theme Access Pattern**: Use `final theme = Theme.of(context);` at the beginning of build methods for consistent
  theme access