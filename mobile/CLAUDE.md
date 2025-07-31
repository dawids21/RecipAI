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

- Modules (packages) should be split by feature not by layer e.g. all widgets, screens, models related to recipes should
  be in `recipe/` package (with no sub-packages for widgets, models, etc.)
- Each module should have all required classes to provide a single feature

## Architecture Patterns

- **API Service**: Singleton pattern with HTTP client
- **Error Handling**: Try-catch blocks with user-friendly error messages