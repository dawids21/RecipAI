# Dependency Injection

### get_it Service Locator with Feature Setup Functions
Use `get_it` as the service locator. Each feature has a `setup<Feature>()` function in `*_setup.dart` that registers all feature services and repositories. Setup functions are called in `main()` before app initialization.

```dart
void setupRecipe() {
  getIt.registerSingleton(RecipeRepository());
  getIt.registerLazySingleton(
    () => RecipeListService(
      recipeRepository: getIt<RecipeRepository>(),
      authService: getIt<AuthService>(),
    ),
    dispose: (service) => service.dispose(),
  );
}
```

### Constructor-Based Dependency Injection
Dependencies are passed via **named required constructor parameters**. Services and repositories receive dependencies through constructors — never call `getIt<>()` inside class bodies. Positional parameters are not used for dependencies.

```dart
// Correct
class RecipeListService {
  RecipeListService({
    required RecipeRepository recipeRepository,
    required AuthService authService,
  }) : _recipeRepository = recipeRepository,
       _authService = authService;
}

// Wrong — avoid getIt calls inside class body
class RecipeListService {
  final _recipeRepository = getIt<RecipeRepository>(); // don't do this
}
```

### External Dependencies as Setup Function Parameters
Dependencies connecting to the external world (APIs, Firebase, databases, local storage) are passed as nullable named parameters to setup functions. Production implementations are used by default; test implementations can be injected.

```dart
void setupAuth({AuthRepository? authRepository}) {
  final repository = authRepository ?? FirebaseAuthRepository();
  getIt.registerSingleton(AuthService(authRepository: repository));
}
```

### State Scoping: Global vs Screen-Specific vs Local
- **Global state** (auth, shared data): Register as singleton — lives for app lifetime
- **Screen-specific state**: Register as lazySingleton; reset in screen's `dispose()` with `isRegistered()` guard
- **Local/form state**: Use `StatefulWidget` state

```dart
// Screen-specific service cleanup
@override
void dispose() {
  if (getIt.isRegistered<RecipeDetailService>()) {
    getIt.resetLazySingleton<RecipeDetailService>();
  }
  super.dispose();
}
```
