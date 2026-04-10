# Navigation

### AppRoute Enum for Type-Safe Navigation
All routes are defined in the `AppRoute` enum in `core/routes.dart`. Each enum value holds its path segment. Navigate using `context.goNamed(AppRoute.xxx.name)`.

```dart
enum AppRoute {
  login('/login'),
  main('/'),
  recipeCreate('recipes/create'),
  recipeDetail(':id');

  final String path;
  const AppRoute(this.path);
}

// Navigation
context.goNamed(AppRoute.main.name);
context.goNamed(AppRoute.recipeDetail.name, pathParameters: {'id': recipe.id.toString()});
```

### go_router with Auth Guards
GoRouter uses `refreshListenable` pointing to `authService.isAuthenticated` to trigger automatic redirects when auth state changes.

```dart
GoRouter(
  refreshListenable: authService.isAuthenticated,
  redirect: (context, state) { ... },
  routes: [ ... ],
)
```

Services are injected into screens via route builder closures using `getIt<>()` — screens receive services as constructor params, not via getIt inside the widget.
