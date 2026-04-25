# Widget Testing Standards

## Scope & philosophy

Widget tests pump the **real screen widget** inside a test-local `MaterialApp.router`. Every repository the screen's services touch — including `AuthRepository` — is replaced by a `mocktail` mock. Real services run on top of those mocks via the existing feature `setup*()` functions. No mocking crosses the service layer.

This rule keeps the test boundary uniform: repositories are infrastructure, services are behaviour. Mocking at a higher layer (e.g. faking an entire `RecipeListService`) would hide the wiring bugs that widget tests are specifically good at catching.

## Directory layout

`test/` mirrors `lib/` feature by feature:

```
mobile/
  lib/
    features/
      recipe/
        recipe_list_service.dart
  test/
    features/
      recipe/
        main_screen_recipes_tab_widget_test.dart
    support/
      mocks.dart           ← type declarations only
```

`test/support/` is reserved for **type declarations only** — the `Mock*` classes. Screen setup, router construction, stub calls, and `pumpWidget` all live in the test file itself. Do not create shared builder or harness files until 2–3 screens are under test and a reusable shape becomes evident.

## setUp / tearDown lifecycle

```dart
setUp(() async {
  SharedPreferences.setMockInitialValues({});   // must come first
  GetIt.I.reset();

  // instantiate and stub mocks ...

  final prefs = await SharedPreferences.getInstance();
  GetIt.I.registerSingleton(PreferencesService(prefs));  // before setup*() calls

  setupAuth(authRepository: mockAuth);
  setupRecipesCollection(recipesCollectionRepository: mockCollections);
  setupRecipe(recipeRepository: mockRecipes);
  setupShoppingList(shoppingListRepository: mockShoppingList);
  setupMealPlan(mealPlanRepository: mockMealPlan);

  // build router and app ...
});

tearDown(() => GetIt.I.reset());
```

Key rules:
- `SharedPreferences.setMockInitialValues({})` must run **before** any `setup*()` call — `MealPlanVisibilityService` and `RecipeListService` read preferences synchronously on construction.
- Register `PreferencesService` manually **before** calling any `setup*()` — it is not registered by any feature setup function; it comes from `main()`.
- `GetIt.I.reset()` in both `setUp` *and* `tearDown` keeps tests fully isolated.
- Call `setup*()` functions in the same order as `main()` so cross-feature `getIt<>` lookups inside service constructors resolve correctly.

## Writing mocks with mocktail

Declare mock classes in `test/support/mocks.dart`:

```dart
class MockRecipeRepository extends Mock implements RecipeRepository {}
```

For concrete classes (non-abstract), `extends Mock implements ConcreteClass` works in the same way.

`registerFallbackValue` is needed only when `any()` is used on a non-primitive type in a stub or verify call. Register it in `setUpAll`:

```dart
setUpAll(() {
  registerFallbackValue(MyValueObject());
});
```

For simple nullable primitives (`String?`) and typed futures, `any()` works without registration.

## Single-route GoRouter with NavigatorObserver

Build a minimal router in the test file — do not use `createAppRouter()` from production code:

```dart
class _NavPushSpy extends NavigatorObserver {
  final List<Route<dynamic>> pushed = [];
  @override
  void didPush(Route route, Route? previousRoute) => pushed.add(route);
}

final navSpy = _NavPushSpy();
final router = GoRouter(
  observers: [navSpy],
  routes: [
    GoRoute(
      path: AppRoute.main.path,
      builder: (context, state) => MainScreen(/* services from getIt */),
    ),
    GoRoute(
      path: '/${AppRoute.recipeDetail.path}',
      name: AppRoute.recipeDetail.name,
      builder: (context, state) => const Scaffold(body: Text('Recipe Detail')),
    ),
  ],
);
final app = MaterialApp.router(routerConfig: router);
```

Navigation assertion:

```dart
expect(
  navSpy.pushed.any((r) => r.settings.name == AppRoute.recipeDetail.name),
  isTrue,
);
```

Always add a stub route for every named route the screen under test navigates to, even if the destination is a placeholder `Scaffold`. Without the route, `go_router` throws at runtime.

## Asserting state

The standard pump sequence:

```dart
await tester.pumpWidget(app);
await tester.pumpAndSettle();
// assert here
```

## Dispose ordering

`flutter_test` tears down the widget tree before the test's `tearDown` callback runs. This means `MainScreen.dispose()` (which calls `getIt.resetLazySingleton`) fires while `getIt` is still populated — the safe order. If a test fails with a `get_it` assertion rather than the expected assertion, add `await tester.pumpWidget(Container())` at the end of the failing test body to force widget disposal before `tearDown`.
