# State Management

### ValueNotifier + AsyncValue for Async State
`ValueNotifier<AsyncValue<T>>` is the default shape for async-loaded state a service exposes to views: state is exposed read-only as `ValueListenable<AsyncValue<T>>` via getters, and async operations use `AsyncValue.guardAsync()` to wrap Futures and automatically catch errors. This is a convention, not a hard requirement — a service may hold a different state shape when the domain calls for it. For example, the shopping-list item **store service** exposes per-list `ValueNotifier<List<T>>` over a locally-owned cache (with instant, synchronous notifier updates) rather than a single `AsyncValue`.

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

### Guarding Against Concurrent Calls
Async service methods that must not run concurrently need an explicit guard. A per-method `_isXxxRunning` boolean flag — checked at start, reset at end (in a `try/finally` if needed) — is the common case for a simple "don't run this twice at once":

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

A boolean flag is not the only valid guard — choose the one that fits the concurrency shape:

- **Per-key `Lock`** (`synchronized` package) — serialises a read-modify-write section per entity so overlapping mutations and reconciles cannot interleave. The shopping-list item store service holds a `Map<listId, Lock>` and runs every local mutation inside `_lockFor(listId).synchronized(...)` (ADR-0004).
- **Single-flight-drain guard** (`_draining` / `_pending` sets) — ensures at most one loop runs per key and coalesces repeated kicks arriving mid-loop into one follow-up pass. The shopping-list sync service uses this to stop two drains double-pushing the same outbox head.

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
