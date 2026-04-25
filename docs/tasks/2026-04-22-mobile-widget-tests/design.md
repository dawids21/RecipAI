# Mobile widget tests — first screen — Design

**Date:** 2026-04-22
**Status:** final
**ADRs:** [docs/ADRs/0002-mobile-widget-test-shape.md](../../ADRs/0002-mobile-widget-test-shape.md)

## Overview

Introduce widget testing to the Flutter app by pumping `MainScreen` inside a
single-route `GoRouter` built in the test, with every repository its
services touch — including `AuthRepository` — replaced by a `mocktail` mock
and real services wired on top via the existing feature `setup*()`
functions (extended to accept repository overrides where they don't yet).
The test router, mock wiring, and `pumpWidget` call live inline in the test
file's `setUp`/test bodies — there is no separate harness/builder file.
Three tests cover the recipes tab (empty, populated, tap-to-navigate); a
new standards file captures the pattern so subsequent screens can follow it
without re-deriving it.

## Required reading for implementation

- `docs/mobile/standards/architecture.md` — repository/service/view boundary
  that governs what is mocked (repositories) and what runs real (services).
- `docs/mobile/standards/dependency-injection.md` — how `get_it` setup
  functions work and the existing "external dependencies as nullable setup
  parameters" pattern; this task extends that pattern to every feature
  `setup*()` that still misses it.
- `docs/mobile/standards/navigation.md` — `AppRoute` enum and
  `context.goNamed` usage; the tap test asserts on
  `AppRoute.recipeDetail.name` via a `NavigatorObserver`.
- `docs/mobile/modules/recipe/ui.md` — recipe feature screen/widget
  inventory.
- `docs/mobile/modules/core/ui.md` — `MainScreen` layout and tab structure
  (recipes tab is index 0, default on mount).

## Approach

Tests pump the **real `MainScreen` widget** inside a test-local
`MaterialApp.router`. The `GoRouter` used in the test is **not**
`createAppRouter()`; it is a minimal router with a single `GoRoute` at `/`
that builds `MainScreen`, plus a `NavigatorObserver` subclass that records
every `didPush`. The router is built directly in the test file's `setUp`
(or per test, when a scenario needs to vary it) — not in a shared builder
function. `MainScreen` constructs `RecipeGrid` and passes a closure that
calls `context.goNamed(AppRoute.recipeDetail.name, ...)` — the observer
records the `MaterialPage` that `go_router` pushes for it, and the test
matches on `route.settings.name`.

Every repository consumed by `MainScreen`'s services is mocked with
`mocktail` (version `^1.0.5`). Crucially, **`AuthRepository` is mocked at
the same boundary as the feature repositories** — its abstract interface
already exists, so the real `AuthService` runs on top with
`watchAuthState()` stubbed to an empty stream and `getIdToken()` stubbed to
a constant string. No mocking crosses the service layer.

Fakes are injected through each feature's `setup*()` function. Three setup
functions already accept a repository override (`setupAuth`,
`setupRecipesCollection`, `setupShoppingList`); this task adds the same
nullable parameter to `setupRecipe` and `setupMealPlan` so the test
`setUp` never has to manipulate `get_it` registrations directly.
`get_it.reset()` runs in `tearDown`.

`PreferencesService` uses `SharedPreferences`; tests call
`SharedPreferences.setMockInitialValues({})` before any setup so the
synchronous reads in `MealPlanVisibilityService`'s constructor and
`RecipeListService._loadSavedFilter()` succeed.

A new mobile standards document captures the full pattern.

## Module & component boundaries

New files:

- `mobile/test/features/recipe/main_screen_recipes_tab_widget_test.dart` —
  the three widget tests (empty state, populated grid, tap-to-navigate).
  The file owns its own `setUp`/`tearDown`, declares its mock instances as
  fields on the test group, builds the single-route test `GoRouter` and
  the `NavPushSpy`, calls each feature's `setup*()` with the mocks, and
  pumps `MainScreen` inline. There is **no** harness/builder file.
- `mobile/test/support/mocks.dart` — declares `Mock*` classes (one per
  mocked repository) using `mocktail`. This is the only shared test
  support file in this task; it contains type declarations only, no
  setup/build logic.
- `docs/mobile/standards/widget-testing.md` — new standards file.

Modified files (production wiring):

- `mobile/lib/features/recipe/recipe_setup.dart` — `setupRecipe({RecipeRepository? recipeRepository})`.
- `mobile/lib/features/planning/meal_plan_setup.dart` — `setupMealPlan({MealPlanRepository? mealPlanRepository})`.

Touched files (docs):

- `docs/INDEX.md` — add `mobile/standards/widget-testing.md` entry.
- `docs/ADRs/INDEX.md` — ADR-0002 entry (already added in draft).

Deleted files:

- `mobile/test/widget_test.dart` — default Flutter scaffolding test,
  replaced by the real test file.

## Data model changes

_No data model changes._

## Interface contracts

### Production setup-function signature changes

```dart
// recipe_setup.dart
void setupRecipe({RecipeRepository? recipeRepository}) {
  final repository = recipeRepository ?? RecipeRepository();
  getIt.registerSingleton<RecipeRepository>(repository);
  // remaining registrations use getIt<RecipeRepository>() as today
}

// meal_plan_setup.dart
void setupMealPlan({MealPlanRepository? mealPlanRepository}) {
  final repository = mealPlanRepository ?? MealPlanRepository();
  getIt.registerSingleton<MealPlanRepository>(repository);
  // remaining registrations unchanged
}
```

Callers in `main()` that pass no arguments keep working unchanged.

### Test-only interface shape

```dart
// test/support/mocks.dart
class MockAuthRepository extends Mock implements AuthRepository {}
class MockRecipeRepository extends Mock implements RecipeRepository {}
class MockRecipesCollectionRepository extends Mock implements RecipesCollectionRepository {}
class MockShoppingListRepository extends Mock implements ShoppingListRepository {}
class MockMealPlanRepository extends Mock implements MealPlanRepository {}
```

The test file declares the navigator-observer spy and the mocks/router as
local state in the test group — no shared harness type or builder
function. Sketch:

```dart
// test/features/recipe/main_screen_recipes_tab_widget_test.dart

class _NavPushSpy extends NavigatorObserver {
  final List<Route<dynamic>> pushed = [];
  @override void didPush(Route route, Route? previousRoute) { pushed.add(route); }
}

void main() {
  late MockAuthRepository authRepository;
  late MockRecipeRepository recipeRepository;
  // ...other mocks
  late _NavPushSpy navSpy;
  late Widget app;

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    GetIt.I.reset();

    authRepository = MockAuthRepository();
    recipeRepository = MockRecipeRepository();
    // ...instantiate other mocks, stub auth defaults

    setupAuth(authRepository: authRepository);
    setupRecipesCollection(/* ... */);
    setupRecipe(recipeRepository: recipeRepository);
    setupShoppingList(/* ... */);
    setupMealPlan(/* ... */);

    navSpy = _NavPushSpy();
    final router = GoRouter(
      observers: [navSpy],
      routes: [GoRoute(path: '/', builder: (_, __) => const MainScreen())],
    );
    app = MaterialApp.router(routerConfig: router);
  });

  tearDown(() => GetIt.I.reset());

  testWidgets('...', (tester) async { /* stub, pump, assert */ });
}
```

Navigation assertion shape:

```dart
expect(
  navSpy.pushed.any((r) => r.settings.name == AppRoute.recipeDetail.name),
  isTrue,
);
```

## Flows & state

Per test (all logic lives directly in the test file):

1. `setUp` —
   1. `SharedPreferences.setMockInitialValues({})`.
   2. `getIt.reset()`.
   3. Instantiate mocks; stub `authRepository.watchAuthState()` to
      `const Stream<User?>.empty()` and `authRepository.getIdToken()` to
      `async => 'fake-token'`.
   4. Invoke setup functions in the production order from `main()`, each
      with its mock repository passed through:
      `setupAuth(authRepository: mockAuth)`, `setupRecipesCollection(...)`,
      `setupRecipe(...)`, `setupShoppingList(...)`, `setupMealPlan(...)`
      (plus `PreferencesService` setup).
   5. Build the `_NavPushSpy`, a single-route `GoRouter` with the spy in
      `observers`, and assign `MaterialApp.router(routerConfig: router)`
      to a local `app` field.
2. Test body — stub the repository methods relevant to the scenario
   (`when(() => recipeRepository.fetchRecipes(any())).thenAnswer((_) async => [...])`).
3. `tester.pumpWidget(app); await tester.pumpAndSettle();` —
   `MainScreen.didChangeDependencies` fires `loadRecipes` etc. on the real
   services, which reach the mocked repositories and settle into terminal
   `AsyncValue` states.
4. Assertions — `find.byType(RecipeGridItem)` count, empty-state text, or
   `navSpy.pushed` inspection after `tester.tap(find.byType(RecipeGridItem).first)`.
5. `tearDown` — `getIt.reset()` (invokes `dispose:` callbacks on
   instantiated lazy singletons; uninstantiated ones drop silently).

Only terminal `AsyncValue.data` states are asserted; transient loading is
not checked.

## Integration changes

**`mobile/lib/features/recipe/recipe_setup.dart`** — add
`{RecipeRepository? recipeRepository}` parameter, default to
`RecipeRepository()` when null. Matches the pattern already in use in
`setupAuth`, `setupRecipesCollection`, `setupShoppingList`.

**`mobile/lib/features/planning/meal_plan_setup.dart`** — same change for
`MealPlanRepository`.

**`mobile/lib/core/main_screen.dart`** — no change. Already takes every
service via constructor.

**`mobile/lib/features/recipe/recipe_grid.dart`** — no change. Renders from
`recipeListService.recipes` and navigates via the `onRecipeTap` closure
that `MainScreen` provides.

**`mobile/lib/core/routes.dart`** — no production change. Tests build
their own single-route `GoRouter` and read `AppRoute.recipeDetail.name`
for the assertion, so renaming the enum value breaks the test at compile
time — intended coupling.

**`mobile/test/widget_test.dart`** — delete. Replaced by the real test
file.

**`mobile/pubspec.yaml`** — add `mocktail: ^1.0.5` under `dev_dependencies`.

**`docs/mobile/standards/widget-testing.md`** — new file covering: scope
and philosophy (repository-only mocking, real services — `AuthRepository`
is a repository for this purpose); directory layout under `test/` mirroring
`lib/`, with `test/support/` reserved for **type declarations only**
(e.g., `Mock*` classes) — screen setup, router construction, and
`pumpWidget` calls live in the test file itself, not in a shared builder;
`SharedPreferences` initial-values setup; `get_it.reset()` lifecycle;
writing repository mocks with mocktail (when `registerFallbackValue` is
needed); asserting navigation via `NavigatorObserver`; the rule that every
feature `setup*()` accepts a `{<Repo>? <repo>}` override; what is
explicitly **not** standardized yet (a generalized pump-any-screen helper —
deferred until 2–3 screens are tested and a reusable shape is observable).

**`docs/INDEX.md`** — add a one-line entry for
`mobile/standards/widget-testing.md` under **Mobile Standards**.

**`docs/ADRs/INDEX.md`** — ADR-0002 entry (already added in the draft
pass).

## Resolved questions

- **Q:** Which screen is first?
  **A:** `MainScreen`, assertions scoped to the recipes tab (`RecipeGrid`,
  index 0 — default on mount).

- **Q:** Mocking library — mocktail vs mockito?
  **A:** `mocktail ^1.0.5`. Confirmed compatible with the pinned Dart
  SDK.

- **Q:** Navigation assertion — `NavigatorObserver.didPush` vs reading
  `GoRouter.routeInformationProvider.value` after `pumpAndSettle`?
  **A:** `NavigatorObserver.didPush` on a test-local `GoRouter`, matching
  `route.settings.name` against `AppRoute.recipeDetail.name`. Rationale
  in [ADR-0002](../../ADRs/0002-mobile-widget-test-shape.md).

- **Q:** How are fakes injected — direct `getIt.registerSingleton(mock)`,
  or through `setup*({repository: fake})`?
  **A:** Through setup functions. `setupRecipe` and `setupMealPlan` are
  extended in this task to accept a nullable `*Repository` parameter,
  matching the existing pattern. No direct `get_it` manipulation from
  tests.

- **Q:** How is auth faked — mock `AuthService`, mock `AuthRepository`, or
  a hand-rolled `AuthService` subclass?
  **A:** Mock `AuthRepository` with mocktail. The interface is already
  abstract, `AuthService` runs real on top, and this keeps the
  repository-boundary rule uniform rather than carving an exception for
  auth. Stubs: `watchAuthState()` → `const Stream<User?>.empty()`,
  `getIdToken()` → `'fake-token'`.

- **Q:** Where do shared test helpers live?
  **A:** `mobile/test/support/` is reserved for cross-test **type
  declarations** only — `mocks.dart` (the `Mock*` classes) is the only
  file there for this task. The setup of `MainScreen` (mock instantiation,
  stubs, `setup*()` calls, single-route `GoRouter` construction,
  `pumpWidget`) lives inline in the test file's `setUp` and test bodies,
  not in a separate builder/harness file. A generalized pump-any-screen
  helper is deferred until 2–3 screens are under test and a shape worth
  extracting becomes evident.

- **Q:** Assert transient loading state?
  **A:** No. Terminal states only.

- **Q:** Is the recipes tab reliably default on mount?
  **A:** Yes — `_MainScreenState._selectedIndex` initializes to `0`.

## Assumptions to verify

_No outstanding assumptions._

## Out of scope (design-level)

- Golden tests, platform-channel tests, CI wiring — deferred per
  requirements.
- A generalized "pump any screen" harness — deferred until 2–3 screens
  are tested.
- A reusable `TestAuthRepository` helper class that bakes in the standard
  stubs. If the three inline stub calls become repetitive across future
  tests, extract it then.
