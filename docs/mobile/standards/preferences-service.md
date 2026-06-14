# Preferences Service Standard

`PreferencesService` (`core/preferences_service.dart`) is the approved way to persist small key/value local data. It
wraps `SharedPreferences` with typed getter/setter pairs and private string key constants. Larger or
structured local state (e.g. an offline-first feature's local store and change queue) may use a local database instead.

## Registration

`PreferencesService` is registered as a **singleton** in `main.dart`. Inject it into services via constructor injection
— never call `getIt<PreferencesService>()` inside class bodies.

```dart
class MyService {
  final PreferencesService _preferencesService;

  MyService({required PreferencesService preferencesService})
      : _preferencesService = preferencesService;
}
```

## Read / Write Pattern

- Reads are **synchronous** — `SharedPreferences` caches values in memory after the initial async load.
- Writes are **asynchronous** — always `await` setters.
- Nullable preferences use `remove()` to clear rather than storing a sentinel value.

```dart
// Read (synchronous)
final savedFilter = _preferencesService.getRecipeFilterCollectionId();

// Write (asynchronous)
await _preferencesService.setRecipeFilterCollectionId(filterId);

// Clear (asynchronous)
await _preferencesService.clearRecipeFilter();
```

## Adding a New Preference

1. Add a private key constant: `static const String _myKey = 'my_preference_key';`
2. Add a typed getter method following the existing naming pattern (`getXxx()`).
3. Add a typed setter method (`setXxx()`). For nullable values, call `remove()` when the value is `null`.
4. Inject `PreferencesService` into any service that needs the preference.

## Current Preferences

| Preference | Key | Type | Description |
|---|---|---|---|
| Recipe filter collection ID | `recipe_filter_collection_id` | `String?` | Persists the selected collection chip filter across app restarts |
| Meal plan visibility | `meal_plan_visibility` | `Map<String, bool>` (JSON) | Persists per-plan calendar visibility toggles |
