# State Management

### ValueNotifier + AsyncValue for Async State
All service classes manage state using `ValueNotifier<AsyncValue<T>>`. State is exposed read-only as `ValueListenable<AsyncValue<T>>` via getters. Async operations use `AsyncValue.guardAsync()` to wrap Futures and automatically catch errors.

```dart
class RecipeListService {
  final ValueNotifier<AsyncValue<List<Recipe>>> _recipes =
      ValueNotifier(const AsyncValue.loading());

  ValueListenable<AsyncValue<List<Recipe>>> get recipes => _recipes;

  Future<void> loadRecipes() async {
    _recipes.value = await AsyncValue.guardAsync(() async {
      return await _recipeRepository.fetchRecipes();
    });
  }
}
```

Views consume state using `ValueListenableBuilder` and `AsyncValue.when()`:

```dart
ValueListenableBuilder(
  valueListenable: service.recipes,
  builder: (context, asyncValue, _) => asyncValue.when(
    loading: () => const CircularProgressIndicator(),
    data: (recipes) => RecipeList(recipes: recipes),
    error: (e, _) => ErrorWidget(e),
  ),
)
```

### Boolean Flags to Prevent Concurrent Calls
Each async service method must use a corresponding `_isXxxRunning` boolean flag to prevent concurrent duplicate calls. Check at start, reset at end (in a `try/finally` if needed).

```dart
bool _isLoadRecipesRunning = false;

Future<void> loadRecipes() async {
  if (_isLoadRecipesRunning) return;
  _isLoadRecipesRunning = true;
  try {
    _recipes.value = await AsyncValue.guardAsync(() async { ... });
  } finally {
    _isLoadRecipesRunning = false;
  }
}
```

### Error Handling via AsyncValue
Repositories throw exceptions — they do not return error states. Services catch them automatically via `AsyncValue.guardAsync()`, which wraps the result in `AsyncValue.error`. Views display errors using the `error` branch of `AsyncValue.when()`.

```dart
// Repository — throws on failure
Future<List<Recipe>> fetchRecipes() async {
  final response = await _apiClient.get('/recipes');
  if (response.statusCode != 200) throw ApiException(response);
  return ...;
}

// Service — guardAsync catches the exception
Future<void> loadRecipes() async {
  _recipes.value = await AsyncValue.guardAsync(() async {
    return await _recipeRepository.fetchRecipes();
  });
}

// View — handles error state
asyncValue.when(
  loading: () => const CircularProgressIndicator(),
  data: (recipes) => RecipeList(recipes: recipes),
  error: (e, _) => ErrorWidget(e),
)
```

### Service dispose() Method
Service classes that own `ValueNotifier` instances must implement `dispose()` that calls `.dispose()` on each notifier. Register the dispose callback with get_it.

```dart
void dispose() {
  _recipes.dispose();
  _selectedCollectionId.dispose();
}
```
