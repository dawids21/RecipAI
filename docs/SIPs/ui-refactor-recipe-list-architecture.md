# SIP: UI Refactor - Recipe List Screen Architecture

## Goal

- Refactor recipe list screen to follow the layered architecture pattern (Repository → Service → View)
- Replace ChangeNotifier + Future pattern with ValueNotifier + AsyncValue pattern
- Implement get_it dependency injection to replace InheritedWidget pattern
- Introduce proper concurrent execution prevention with boolean flags
- Success criteria: Recipe list screen adheres to architecture.md guidelines while maintaining existing functionality

## Context

### Documentation and References

- [get_it Package Documentation](https://pub.dev/packages/get_it) - Latest version: 8.2.0
- [Flutter ValueNotifier Documentation](https://api.flutter.dev/flutter/foundation/ValueNotifier-class.html)
- [Flutter ValueListenableBuilder Documentation](https://api.flutter.dev/flutter/widgets/ValueListenableBuilder-class.html)
- Architecture documentation: `docs/mobile/architecture.md`
- Current AsyncValue implementation: `mobile/lib/core/async_value.dart`
- Current recipe list model: `mobile/lib/features/recipe/recipe_list_model.dart`
- Current recipe list screen: `mobile/lib/features/recipe/recipe_list_screen.dart`
- Current API service: `mobile/lib/core/api_service.dart`
- Current main entry point: `mobile/lib/main.dart`
- Current routes: `mobile/lib/core/routes.dart`

### Current Codebase Tree

```
mobile/lib/
├── core/
│   ├── async_value.dart              # AsyncValue sealed class (already exists) ✅
│   ├── api_service.dart              # HTTP client with all API methods
│   ├── app_config.dart               # App configuration
│   ├── routes.dart                   # Go router with AppRoute enum
│   └── theme.dart                    # Theme and spacing constants
├── features/
│   ├── auth/
│   │   ├── auth_service.dart         # Abstract AuthService with InheritedAuthService
│   │   └── firebase_auth_service.dart
│   └── recipe/
│       ├── recipe_list_model.dart    # ChangeNotifier with Future<List<Recipe>>
│       ├── recipe_list_screen.dart   # Uses FutureBuilder + InheritedRecipeListModel
│       ├── recipe_list_item.dart     # List item widget
│       ├── recipe.dart               # Recipe model (id, name)
│       ├── recipe_detail.dart        # Detailed recipe model
│       ├── recipe_detail_screen.dart
│       ├── create_recipe_screen.dart
│       ├── edit_recipe_screen.dart
│       └── [other recipe widgets]
├── shared/
│   ├── loading_widget.dart
│   └── api_error_widget.dart
└── main.dart                         # Creates RecipeListModel, wraps with InheritedRecipeListModel
```

### Desired Codebase Tree

```
mobile/lib/
├── core/
│   ├── async_value.dart              # AsyncValue sealed class (unchanged) ✅
│   ├── api_service.dart              # HTTP client with all API methods (unchanged)
│   ├── app_config.dart               # App configuration (unchanged)
│   ├── routes.dart                   # MODIFIED: Inject RecipeService via getIt
│   └── theme.dart                    # Theme and spacing constants (unchanged)
├── features/
│   ├── auth/
│   │   ├── auth_service.dart         # Abstract AuthService (unchanged)
│   │   └── firebase_auth_service.dart
│   └── recipe/
│       ├── recipe_repository.dart    # NEW: Repository layer for recipe data fetching
│       ├── recipe_service.dart       # NEW: Service layer with ValueNotifier<AsyncValue>
│       ├── recipe_setup.dart         # NEW: DI setup function for recipe feature
│       ├── recipe_list_screen.dart   # MODIFIED: Uses ValueListenableBuilder + RecipeService
│       ├── recipe_list_item.dart     # List item widget (unchanged)
│       ├── recipe.dart               # Recipe model (unchanged)
│       ├── recipe_detail.dart        # Detailed recipe model (unchanged)
│       ├── recipe_detail_screen.dart # (unchanged)
│       ├── create_recipe_screen.dart # (unchanged)
│       ├── edit_recipe_screen.dart   # (unchanged)
│       └── [other recipe widgets]    # (unchanged)
├── shared/
│   ├── loading_widget.dart           # (unchanged)
│   └── api_error_widget.dart         # (unchanged)
└── main.dart                         # MODIFIED: Call setupRecipe(), remove RecipeListModel
```

### Known Gotchas of Our Codebase and Library Quirks

- **AsyncValue Already Exists**: `lib/core/async_value.dart` already implements the sealed class pattern with `when()`
  method and `guardAsync()` helper - use this instead of creating a new one
- **Repository Layer Independence**: RecipeRepository should NOT use ApiService - copy the fetchRecipes implementation
  directly to keep repository layer independent
- **AuthService Access**: RecipeRepository needs AuthService for getting auth headers - take it as constructor
  dependency
- **Go Router Navigation**: Uses `context.goNamed(AppRoute.enumValue.name)` with AppRoute enum for type-safe navigation
- **Theme Access Pattern**: Always use `final theme = Theme.of(context);` at beginning of build methods
- **StatefulWidget Lifecycle**: Recipe list screen uses `didChangeDependencies()` for service initialization - needs
  conversion to constructor injection
- **get_it Latest Version**: 8.2.0 supports `registerSingleton()` and `registerLazySingleton()`
- **Lazy Singleton Reset**: Must call `getIt.resetLazySingleton<RecipeService>()` in screen's dispose() to prevent
  memory leaks
- **Boolean Flags**: Architecture requires `bool _isLoadRecipesRunning = false` pattern to prevent concurrent method
  executions
- **ValueNotifier vs ChangeNotifier**: ValueNotifier is lightweight and perfect for single-value state, ChangeNotifier
  is for complex objects with multiple properties
- **Isolated Refactoring**: Only refactor recipe list screen - other screens (detail, create, edit) continue using
  ApiService directly via InheritedApiService

## Implementation Plan

### Tasks

```
Task 1: Install get_it Package
  Action: MODIFY
  File: mobile/pubspec.yaml
  Changes:
    - [ ] Add `get_it: ^8.2.0` to dependencies section
    - [ ] Run `flutter pub get` to install the package
    - [ ] Verify installation with `flutter pub get`

Task 2: Create Recipe Repository Layer
  Action: CREATE
  File: mobile/lib/features/recipe/recipe_repository.dart
  Changes:
    - [ ] Create RecipeRepository class (stateless, no base class)
    - [ ] Add private final AuthService _authService field
    - [ ] Add private final http.Client _client = http.Client() field
    - [ ] Add private final String _baseUrl = AppConfig.apiBaseUrl field
    - [ ] Add constructor: RecipeRepository(this._authService)
    - [ ] Copy _getAuthHeaders() method from ApiService (needs token from _authService.idToken)
    - [ ] Copy fetchRecipes() method implementation from ApiService (lines 31-50)
    - [ ] Return Future<List<Recipe>> directly
    - [ ] Import Recipe model from 'recipe.dart'
    - [ ] Import AuthService from '../auth/auth_service.dart'
    - [ ] Import AppConfig from '../../core/app_config.dart'
    - [ ] Import http package: 'package:http/http.dart' as http
    - [ ] Import dart:convert for json.decode
    - [ ] Method should handle errors exactly as ApiService does

Task 3: Create Recipe Service Layer
  Action: CREATE
  File: mobile/lib/features/recipe/recipe_service.dart
  Changes:
    - [ ] Create RecipeService class (no base class, no ChangeNotifier)
    - [ ] Add private final RecipeRepository _recipeRepository field
    - [ ] Add constructor: RecipeService({required RecipeRepository recipeRepository}) : _recipeRepository = recipeRepository
    - [ ] Create private ValueNotifier: `final ValueNotifier<AsyncValue<List<Recipe>>> _recipes = ValueNotifier(AsyncValue.loading())`
    - [ ] Expose public getter: `ValueListenable<AsyncValue<List<Recipe>>> get recipes => _recipes`
    - [ ] Add boolean flag: `bool _isLoadRecipesRunning = false`
    - [ ] Implement loadRecipes() async method following architecture pattern
    - [ ] Use AsyncValue.guardAsync() to wrap repository call
    - [ ] Set _isLoadRecipesRunning = true at start, false at end
    - [ ] Return early if _isLoadRecipesRunning is true
    - [ ] Import AsyncValue from '../../core/async_value.dart'
    - [ ] Import Recipe from 'recipe.dart'
    - [ ] Import RecipeRepository from 'recipe_repository.dart'
    - [ ] Import Flutter foundation for ValueNotifier

Task 4: Create Recipe DI Setup
  Action: CREATE
  File: mobile/lib/features/recipe/recipe_setup.dart
  Changes:
    - [ ] Import get_it package: 'package:get_it/get_it.dart'
    - [ ] Import RecipeRepository from 'recipe_repository.dart'
    - [ ] Import RecipeService from 'recipe_service.dart'
    - [ ] Import AuthService from '../auth/auth_service.dart'
    - [ ] Create global getIt instance: `final getIt = GetIt.instance;`
    - [ ] Create setupRecipe() function (void, no parameters)
    - [ ] Register RecipeRepository as singleton: `getIt.registerSingleton(RecipeRepository(getIt<AuthService>()))`
    - [ ] Register RecipeService as lazy singleton: `getIt.registerLazySingleton(() => RecipeService(recipeRepository: getIt<RecipeRepository>()))`
    - [ ] Note: AuthService must be registered before calling setupRecipe()

Task 5: Update Main to Initialize DI
  Action: MODIFY
  File: mobile/lib/main.dart
  Changes:
    - [ ] Import recipe_setup.dart: 'features/recipe/recipe_setup.dart'
    - [ ] In main() function, BEFORE setupRecipe(), register AuthService as singleton
    - [ ] Add line: `getIt.registerSingleton<AuthService>(authService);` after authService creation
    - [ ] Call setupRecipe() after AuthService registration, before runApp()
    - [ ] Remove `late final RecipeListModel _recipeListModel;` from _RecipAIAppState
    - [ ] Remove `_recipeListModel = RecipeListModel(widget.apiService);` from initState
    - [ ] Remove `_recipeListModel.dispose();` from dispose method
    - [ ] Remove InheritedRecipeListModel wrapper from build method (keep InheritedAuthService and InheritedApiService)
    - [ ] Remove import of recipe_list_model.dart

Task 6: Update Recipe List Screen to Use Service
  Action: MODIFY
  File: mobile/lib/features/recipe/recipe_list_screen.dart
  Changes:
    - [ ] Add RecipeService field to RecipeListScreen widget: `final RecipeService recipeService;`
    - [ ] Add required constructor parameter: `required this.recipeService`
    - [ ] Remove `late AuthService _authService;` from _RecipeListScreenState (still accessible from elsewhere)
    - [ ] Keep didChangeDependencies for _authService initialization (still needed for logout)
    - [ ] In initState(), call `widget.recipeService.loadRecipes();`
    - [ ] Add dispose() method: `getIt.resetLazySingleton<RecipeService>();` then `super.dispose();`
    - [ ] In build(), remove `final recipeListModel = InheritedRecipeListModel.of(context);`
    - [ ] Replace FutureBuilder with ValueListenableBuilder
    - [ ] Set valueListenable to: `widget.recipeService.recipes`
    - [ ] Change builder parameter from `(context, snapshot, child)` to `(context, asyncValueRecipes, child)`
    - [ ] Replace snapshot.connectionState check with asyncValueRecipes.when()
    - [ ] Use when() with loading: () => LoadingWidget(), data: (recipes) => [...], error: (error) => ApiErrorWidget(...)
    - [ ] In _handleRefresh(), call `widget.recipeService.loadRecipes()` instead of recipeListModel.refresh()
    - [ ] Import get_it package for getIt access
    - [ ] Import RecipeService from 'recipe_service.dart'
    - [ ] Remove import of recipe_list_model.dart

Task 7: Update Routes to Inject RecipeService
  Action: MODIFY
  File: mobile/lib/core/routes.dart
  Changes:
    - [ ] Import get_it package: 'package:get_it/get_it.dart'
    - [ ] Import RecipeService: '../features/recipe/recipe_service.dart'
    - [ ] Import recipe_setup for getIt: '../features/recipe/recipe_setup.dart'
    - [ ] In RecipeListScreen route builder (AppRoute.recipes), change from `const RecipeListScreen()` to:
      `RecipeListScreen(recipeService: getIt<RecipeService>())`

Task 8: Delete Old Recipe List Model
  Action: DELETE
  File: mobile/lib/features/recipe/recipe_list_model.dart
  Changes:
    - [ ] Delete entire file (no longer needed)
    - [ ] Verify no other files import this (should only be main.dart and recipe_list_screen.dart which are updated)

Task 9: Update mobile.md Documentation
  Action: MODIFY
  File: docs/mobile/mobile.md
  Changes:
    - [ ] Remove "Fetching Data on Screen Load" section (lines 76-96)
    - [ ] Keep all other sections unchanged

Task 10: Update mobile CLAUDE.md
  Action: MODIFY
  File: mobile/CLAUDE.md
  Changes:
    - [ ] Add new section after "Navigation" section:
      ## Architecture
      - For new features follow architecture specification from ../docs/mobile/architecture.md
```

### Per Task Pseudocode

```dart
# Task 2: RecipeRepository Implementation
class RecipeRepository {
  final AuthService _authService;
  final http.Client _client = http.Client();
  final String _baseUrl = AppConfig.apiBaseUrl;

  RecipeRepository(this._authService);

  Future<Map<String, String>> _getAuthHeaders() async {
    final token = await _authService.idToken;
    return {
      'Content-Type': 'application/json',
      if (token != null) 'Authorization': 'Bearer $token',
    };
  }

  Future<List<Recipe>> fetchRecipes() async {
    try {
      final headers = await _getAuthHeaders();
      final response = await _client.get(
        Uri.parse('$_baseUrl/recipes'),
        headers: headers,
      );

      if (response.statusCode == 200) {
        final List<dynamic> jsonList = json.decode(response.body);
        return jsonList
            .map((json) => Recipe.fromJson(json as Map<String, dynamic>))
            .toList();
      } else {
        throw Exception('Failed to load recipes: ${response.statusCode}');
      }
    } catch (e) {
      throw Exception('Network error while fetching recipes: $e');
    }
  }
}

# Task 3: RecipeService Implementation
class RecipeService {
  final RecipeRepository _recipeRepository;

  RecipeService({required RecipeRepository recipeRepository})
    : _recipeRepository = recipeRepository;

  final ValueNotifier<AsyncValue<List<Recipe>>> _recipes =
    ValueNotifier(AsyncValue.loading());

  ValueListenable<AsyncValue<List<Recipe>>> get recipes => _recipes;

  bool _isLoadRecipesRunning = false;

  Future<void> loadRecipes() async {
    if (_isLoadRecipesRunning) return;
    _isLoadRecipesRunning = true;
    _recipes.value = AsyncValue.loading();
    _recipes.value = await AsyncValue.guardAsync(() async {
      return _recipeRepository.fetchRecipes();
    });
    _isLoadRecipesRunning = false;
  }
}

# Task 4: DI Setup Implementation
final getIt = GetIt.instance;

void setupRecipe() {
  getIt.registerSingleton(
    RecipeRepository(getIt<AuthService>())
  );
  getIt.registerLazySingleton(() =>
    RecipeService(recipeRepository: getIt<RecipeRepository>())
  );
}

# Task 5: Main.dart Changes
void main() async {
  // ... existing initialization
  final authService = FirebaseAuthService();
  final apiService = ApiService(authService);
  final appRouter = createAppRouter(authService);

  // NEW: Register AuthService in getIt
  getIt.registerSingleton<AuthService>(authService);

  // NEW: Setup recipe feature dependencies
  setupRecipe();

  runApp(RecipAIApp(
    authService: authService,
    apiService: apiService,
    appRouter: appRouter,
  ));
}

class _RecipAIAppState extends State<RecipAIApp> {
  // REMOVE: late final RecipeListModel _recipeListModel;

  @override
  void initState() {
    super.initState();
    // REMOVE: _recipeListModel = RecipeListModel(widget.apiService);
  }

  @override
  void dispose() {
    // REMOVE: _recipeListModel.dispose();
    widget.apiService.dispose();
    widget.authService.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return InheritedAuthService(
      notifier: widget.authService,
      child: InheritedApiService(
        apiService: widget.apiService,
        // REMOVE: InheritedRecipeListModel wrapper
        child: MaterialApp.router(
          title: 'RecipAI',
          theme: AppTheme.theme,
          routerConfig: widget.appRouter,
        ),
      ),
    );
  }
}

# Task 6: RecipeListScreen Refactor
class RecipeListScreen extends StatefulWidget {
  final RecipeService recipeService;

  const RecipeListScreen({
    super.key,
    required this.recipeService,
  });

  @override
  State<RecipeListScreen> createState() => _RecipeListScreenState();
}

class _RecipeListScreenState extends State<RecipeListScreen> {
  late AuthService _authService;

  @override
  void initState() {
    super.initState();
    widget.recipeService.loadRecipes();
  }

  @override
  void didChangeDependencies() {
    _authService = InheritedAuthService.of(context);
    super.didChangeDependencies();
  }

  @override
  void dispose() {
    getIt.resetLazySingleton<RecipeService>();
    super.dispose();
  }

  Future<void> _handleRefresh() async {
    await widget.recipeService.loadRecipes();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(/* ... */),
      body: RefreshIndicator(
        onRefresh: _handleRefresh,
        child: ValueListenableBuilder(
          valueListenable: widget.recipeService.recipes,
          builder: (context, asyncValueRecipes, child) {
            return asyncValueRecipes.when(
              loading: () => const LoadingWidget(),
              data: (recipes) {
                if (recipes.isEmpty) {
                  return Center(
                    child: Text(
                      'No recipes found',
                      style: theme.textTheme.labelMedium,
                    ),
                  );
                }
                return ListView.builder(
                  itemCount: recipes.length,
                  itemBuilder: (context, index) {
                    return RecipeListItem(
                      recipe: recipes[index],
                      onTap: () => _onRecipeTap(context, recipes[index]),
                    );
                  },
                );
              },
              error: (error) => ApiErrorWidget(
                errorMessage: 'Error: $error',
                onRetry: () {
                  widget.recipeService.loadRecipes();
                },
              ),
            );
          },
        ),
      ),
      floatingActionButton: SpeedDial(/* ... */),
    );
  }
}

# Task 7: Routes Update
GoRouter createAppRouter(AuthService authService) {
  return GoRouter(
    // ... existing config
    routes: [
      // ... other routes
      GoRoute(
        path: AppRoute.recipes.path,
        name: AppRoute.recipes.name,
        builder: (context, state) => RecipeListScreen(
          recipeService: getIt<RecipeService>(),  // Changed from const RecipeListScreen()
        ),
        routes: [/* ... */],
      ),
    ],
  );
}
```

## Validation

### Syntax and Style

```bash
# Run these FIRST - fix any errors before proceeding
cd mobile
flutter analyze

# Expected: No errors. If errors, READ the error and fix.
```

### Unit Tests

```bash
# Run and iterate until passing:
cd mobile
flutter test

# If failing: Read error, understand root cause, fix code, re-run (never mock to pass)
```

## Integration Points

- **No API Changes**: RecipeRepository copies fetchRecipes implementation from ApiService, no API changes needed
- **No Database Changes**: Read-only data fetching, no persistence layer changes
- **Go Router Integration**: Continues using AppRoute enum for navigation, now injects RecipeService via getIt
- **Authentication Integration**: AuthService used directly in RecipeRepository for auth headers
- **Other Recipe Screens**: Recipe detail, create, and edit screens continue using ApiService directly via
  InheritedApiService (no changes needed)
- **Theme Integration**: Maintains existing theme access pattern with `Theme.of(context)`
- **Error Widget Integration**: Existing ApiErrorWidget and LoadingWidget work seamlessly with AsyncValue.when()
- **Concurrent DI Patterns**: App now uses both InheritedWidget (for auth, api) AND get_it (for recipe service) - this
  is intentional for isolated refactoring

## Documentation

- **Update** `docs/mobile/mobile.md`:
    - Remove "Fetching Data on Screen Load" usage pattern section

- **Update** `mobile/CLAUDE.md`:
    - Add Architecture section: "For new features follow architecture specification from ../docs/mobile/architecture.md"

## Final Validation Checklist

- [ ] Correct syntax (`flutter analyze` passes with no errors)
- [ ] Correct style (no linting warnings)
- [ ] All tests pass (`flutter test` succeeds)
- [ ] Error cases handled gracefully (network errors show ApiErrorWidget)
- [ ] Logs are informative but not verbose (no excessive debug prints)
- [ ] Loading states work correctly (spinner shows during fetch)
- [ ] Pull-to-refresh works (triggers loadRecipes and updates UI)
- [ ] Lazy singleton resets properly (no memory leaks on navigation)
- [ ] Concurrent execution prevention works (boolean flag prevents duplicate calls)
- [ ] AsyncValue.when() handles all three states (loading, data, error)
- [ ] ValueListenableBuilder triggers rebuilds on state changes
- [ ] Other screens remain unaffected (detail, create, edit still work)
- [ ] Navigation flows unchanged (routing works as before)
- [ ] Documentation updated (mobile.md, CLAUDE.md)
- [ ] Architecture pattern followed exactly (Repository → Service → View)
- [ ] get_it properly initialized in main() before use
- [ ] No breaking changes to existing functionality

**SIP Confidence Score: 9.5/10**

This SIP provides comprehensive context for successful one-pass implementation. It follows the established architecture
pattern from architecture.md exactly, leverages existing AsyncValue infrastructure, and maintains backward
compatibility. The isolated refactoring approach (only recipe list screen) minimizes risk and allows gradual migration.
Clear pseudocode, validation steps, and integration points ensure the AI agent has all necessary context for autonomous
implementation.

The 0.5 point deduction accounts for the dual DI pattern complexity (InheritedWidget + get_it coexisting), but this is
intentional and well-documented to avoid disrupting existing screens during gradual architecture migration.
