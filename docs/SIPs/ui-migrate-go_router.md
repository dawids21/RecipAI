# SIP: Migrate Mobile Navigation to go_router

## Goal

- Migrate current navigator-based navigation in mobile app to use the `go_router` package for declarative routing
- Implement proper URL routing structure with nested routes: `/recipes` with sub-routes `/:id`, `/import`, `/create`
- Create centralized route configuration with type-safe navigation using named routes and enums
- Enable deep linking capabilities for future web platform support
- Maintain existing navigation behavior while improving code maintainability

## Context

### Documentation and References

- [go_router official documentation](https://pub.dev/packages/go_router)
- [Current mobile app structure](docs/mobile/mobile.md)
- [Mobile UI components](docs/mobile/ui.md)
- [Project PRD](docs/prd.md)
- [Flutter go_router nested routes guide](https://blog.codemagic.io/flutter-go-router-guide/)
- [go_router migration best practices](https://dev.to/rootstrap/flutter-navigation-navigator-vs-go-router-1bi1)

### Current Codebase Tree

```
mobile/
├── lib/
│   ├── main.dart                       # MaterialApp setup (needs migration)
│   ├── core/                           # Core services and configuration
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
│       │   ├── recipe_list_screen.dart        # Uses Navigator.push
│       │   ├── recipe_detail_screen.dart      # Uses Navigator.pop
│       │   ├── create_recipe_screen.dart      # Uses Navigator.pop
│       │   └── [other widgets...]
│       └── import/                     # Recipe import feature
│           ├── import_screen.dart             # Uses Navigator.pop
│           └── web_recipe_extractor.dart
```

### Desired Codebase Tree

```
mobile/
├── lib/
│   ├── main.dart                       # MaterialApp.router setup
│   ├── core/                           
│   │   ├── routes.dart                 # *** NEW *** Route configuration with AppRouter
│   │   ├── api_service.dart           
│   │   ├── app_config.dart            
│   │   └── theme.dart                 
│   ├── shared/                         
│   │   ├── loading_widget.dart        
│   │   ├── api_error_widget.dart      
│   │   ├── error_message_widget.dart  
│   │   └── error_icon.dart           
│   └── features/                       
│       ├── recipe/                     
│       │   ├── recipe_list_screen.dart        # Uses context.go/context.push
│       │   ├── recipe_detail_screen.dart      # Uses context.pop
│       │   ├── create_recipe_screen.dart      # Uses context.pop
│       │   └── [other widgets...]
│       └── import/                     
│           ├── import_screen.dart             # Uses context.pop
│           └── web_recipe_extractor.dart
```

### Known Gotchas of Our Codebase and Library Quirks

- Current navigation uses `Navigator.push` with result handling for Import and Create screens
- Recipe refresh logic depends on returned results from navigation
- `go_router` requires adding dependency to pubspec.yaml
- `MaterialApp` needs to be replaced with `MaterialApp.router`
- Parameters in go_router are accessed via `state.pathParameters` (not `state.params` in older versions)
- Result passing between screens requires using `extra` parameter and careful state management
- `context.go()` replaces entire navigation stack, while `context.push()` adds to stack (equivalent to Navigator.push)

## Implementation Plan

### Tasks

```
Task 1: Add go_router dependency and update pubspec
  Action: MODIFY
  File: mobile/pubspec.yaml
  Changes:
    - [ ] Add go_router: ^16.1.0 to dependencies section
    - [ ] Run flutter pub get to install dependency
    - [ ] Follow existing dependency pattern in pubspec.yaml

Task 2: Create centralized route configuration
  Action: CREATE
  File: mobile/lib/core/routes.dart
  Changes:
    - [ ] Create AppRoute enum with route names following feature request pattern
    - [ ] Define appRouter GoRouter instance with routes structure
    - [ ] Implement error page handling with builder
    - [ ] Use nested routes pattern: '/recipes' with sub-routes '/:id', '/import', '/create'
    - [ ] Implement named routes using enum pattern for type-safe navigation
    - [ ] Follow example pattern from feature request for GoRoute definition
    - [ ] Import all required screen widgets

Task 3: Update main.dart to use MaterialApp.router
  Action: MODIFY
  File: mobile/lib/main.dart
  Changes:
    - [ ] Import routes.dart
    - [ ] Replace MaterialApp with MaterialApp.router
    - [ ] Set routerConfig: appRouter
    - [ ] Remove home parameter (now handled by routes)
    - [ ] Maintain existing theme and title configuration

Task 4: Update RecipeListScreen navigation calls
  Action: MODIFY
  File: mobile/lib/features/recipe/recipe_list_screen.dart
  Changes:
    - [ ] Import 'package:go_router/go_router.dart' and routes.dart
    - [ ] Replace _onRecipeTap Navigator.push with context.pushNamed(AppRoute.recipeDetail.name, pathParameters: {'id': recipe.id})
    - [ ] Replace _onImportTap Navigator.push with context.pushNamed(AppRoute.recipeImport.name)
    - [ ] Replace _onCreateTap Navigator.push with context.pushNamed(AppRoute.recipeCreate.name)
    - [ ] Maintain result handling pattern using async/await on context.pushNamed
    - [ ] Keep existing _refreshRecipeList() functionality

Task 5: Update RecipeDetailScreen to use path parameters
  Action: MODIFY
  File: mobile/lib/features/recipe/recipe_detail_screen.dart
  Changes:
    - [ ] Update constructor to get recipeId from GoRouterState instead of widget parameter
    - [ ] Import 'package:go_router/go_router.dart'
    - [ ] Use GoRouter.of(context).routeInformationProvider to access route state if needed
    - [ ] Maintain all existing UI functionality

Task 6: Update ImportScreen navigation
  Action: MODIFY
  File: mobile/lib/features/import/import_screen.dart
  Changes:
    - [ ] Import 'package:go_router/go_router.dart'  
    - [ ] Replace Navigator.pop(context, recipe) with context.pop(recipe)
    - [ ] Maintain result passing for recipe refresh functionality
    - [ ] Keep all existing WebView and import logic

Task 7: Update CreateRecipeScreen navigation
  Action: MODIFY
  File: mobile/lib/features/recipe/create_recipe_screen.dart
  Changes:
    - [ ] Import 'package:go_router/go_router.dart'
    - [ ] Replace Navigator.pop(context, createdRecipe) with context.pop(createdRecipe)
    - [ ] Maintain result passing for recipe refresh functionality
    - [ ] Keep all existing form and API logic

Task 8: Update mobile/CLAUDE.md with go_router rules
  Action: MODIFY
  File: mobile/CLAUDE.md
  Changes:
    - [ ] Add section for go_router usage patterns
    - [ ] Document route definition patterns
    - [ ] Add navigation method guidelines (go vs push)
    - [ ] Include parameter access patterns
    - [ ] Document result passing approach with context.pop()
```

### Per Task Pseudocode

```dart
// Task 2: Route configuration pseudocode with named routes using built-in enum.name
enum AppRoute {
  home('/'),
  recipes('/recipes'),
  recipeDetail(':id'), // nested under /recipes
  recipeImport('import'), // nested under /recipes  
  recipeCreate('create'); // nested under /recipes

  const AppRoute(this.path);

  final String path;
}

final GoRouter appRouter = GoRouter(
  initialLocation: AppRoute.recipes.path,
  errorBuilder: (context, state) => ErrorPage(error: state.error),
  routes: [
    GoRoute(
      path: AppRoute.home.path,
      name: AppRoute.home.name,
      redirect: (context, state) => AppRoute.recipes.path,
    ),
    GoRoute(
      path: AppRoute.recipes.path,
      name: AppRoute.recipes.name,
      builder: (context, state) => RecipeListScreen(),
      routes: [
        GoRoute(
          path: AppRoute.recipeDetail.path,
          name: AppRoute.recipeDetail.name,
          builder: (context, state) {
            final id = state.pathParameters['id']!;
            return RecipeDetailScreen(recipeId: id);
          },
        ),
        GoRoute(
          path: AppRoute.recipeImport.path,
          name: AppRoute.recipeImport.name,
          builder: (context, state) => ImportScreen(),
        ),
        GoRoute(
          path: AppRoute.recipeCreate.path,
          name: AppRoute.recipeCreate.name,
          builder: (context, state) => CreateRecipeScreen(),
        ),
      ],
    ),
  ],
);

// Navigation usage with named routes:
// context.goNamed(AppRoute.recipeDetail.name, pathParameters: {'id': recipeId})
// context.pushNamed(AppRoute.recipeImport.name)
// context.pushNamed(AppRoute.recipeCreate.name)
```

## Validation

### Syntax and Style

```bash
# Run these FIRST - fix any errors before proceeding
cd mobile
flutter analyze
dart format --set-exit-if-changed .

# Expected: No errors. If errors, READ the error and fix.
```

### Unit Tests

```bash
# Run and iterate until passing:
cd mobile
flutter test

# If failing: Read error, understand root cause, fix code, re-run (never mock to pass)
```

### Integration Tests

```bash
# Manual testing required:
cd mobile
flutter run

# Test scenarios:
# 1. App launches to recipe list
# 2. Tap recipe -> navigate to detail screen
# 3. Back navigation works correctly  
# 4. Speed dial -> Import -> successful import -> list refreshes
# 5. Speed dial -> Create -> successful creation -> list refreshes
# 6. Deep linking to /recipes/[id] works (future capability)
```

## Integration Points

- No backend API changes required - all existing API calls remain the same
- No database schema changes required
- Navigation state management between screens maintained through go_router's built-in mechanisms
- Result passing from Import and Create screens continues to work via context.pop(result)

## Documentation

- Update `mobile/CLAUDE.md` with go_router navigation patterns and rules
- Update `docs/mobile/mobile.md` with new codebase structure including routes.dart and go_router navigation approach
- No other documentation files need updating as this is an internal implementation change
- API documentation remains unchanged

## Final Validation Checklist

- [ ] Correct syntax (flutter analyze passes)
- [ ] Correct style (dart format passes)
- [ ] All tests pass (flutter test succeeds)
- [ ] Manual test successful (all navigation flows work)
- [ ] Error cases handled gracefully (error page shows for invalid routes)
- [ ] Logs are informative but not verbose
- [ ] Documentation updated (mobile/CLAUDE.md includes go_router rules)
- [ ] Deep linking capability verified (can navigate directly to routes)
- [ ] Result passing still works (recipe list refreshes after import/create)

---
**Confidence Score: 9/10** - High confidence in one-pass implementation success. The migration is well-documented,
follows established patterns, and maintains existing functionality while providing improved routing capabilities.