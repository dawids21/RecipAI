# Mobile widget tests — first screen — solution brainstorming

**Date:** 2026-04-22
**Status:** brainstorming

## Summary

Establish a widget-testing pattern for the Flutter app by covering the recipe
grid tab of `MainScreen` and capturing the resulting pattern in a new mobile
standards document. This doc explores four distinct approaches to the test
shape (component vs. screen scope, real vs. stubbed router, hand-rolled fakes
vs. mocking library) and recommends a hybrid: pump the full `MainScreen`
inside a single-route test `GoRouter`, register mocktail-based fake
repositories for every feature `MainScreen` touches, and verify navigation
through a `NavigatorObserver` attached to the test router.

## Approaches considered

### Approach 1: Component test with callback spy

**Sketch.** Pump `RecipeGrid` directly inside a minimal `MaterialApp` (no
router). Construct a real `RecipeListService` in the test, injected with a
hand-rolled `FakeRecipeRepository` that returns whatever list the test
needs. Pass a test spy as the `onRecipeTap` callback and assert it was
invoked with the tapped recipe. `get_it` is bypassed entirely — services
come in via constructor, which the architecture already supports. The new
standards doc would document: how to write a fake repository, how to seed
state by varying what the fake returns, how to pump a widget, and that
navigation is asserted via the widget's callback parameter.

**Trade-offs.**
- Smallest, fastest, least machinery — likely under ~50 lines total for all
  three tests plus a fake.
- Exercises real service code and the widget's `AsyncValue` rendering, which
  matches the requirements.
- Does **not** verify that the hosting screen actually calls `goNamed(...)` —
  that wiring is outside the test.
- Pattern only directly applies to widgets that already expose navigation as
  a callback; screens that call `context.goNamed` inline need a different
  recipe.

**When it's the right choice.** When the team is comfortable that navigation
assertions at the callback seam are "close enough" and wants the
lowest-ceremony pattern.

**Main risk.** Future screens don't follow the callback-injection pattern,
so the standard doesn't transfer and we re-do this exercise at screen 2.

### Approach 2: Screen test with the real app router

**Sketch.** Pump the actual screen (`MainScreen`, which hosts the recipe
grid as a tab) inside `MaterialApp.router` using the app's existing
`AppRoute` configuration. Replace `get_it` registrations in `setUp` with
fake repositories. Trigger a tap and assert navigation by reading
`GoRouter.of(context).routeInformationProvider.value.uri` or equivalent
router state.

**Trade-offs.**
- High-fidelity: tests against the same routing config shipped to users.
- Reusable pattern for every future screen without modification.
- Requires every route in `AppRoute` to be constructible during test, which
  means either all feature `setup*` functions run or the route table is
  partially stubbed — both add weight to `setUp`.
- Asserting via `routeInformationProvider` is a little indirect; naïve
  assertions tend to race with `pumpAndSettle`, and destination screens may
  themselves want data.

**When it's the right choice.** When the team expects most future screens to
have navigation logic inline (not via callback), and wants one canonical
testing recipe that works everywhere.

**Main risk.** Boot cost for the real router grows with the route table;
the "first screen" test becomes a de-facto integration test and slows the
suite before it has even 10 tests.

### Approach 3: Screen test with a minimal stub-destination router

**Sketch.** Pump the screen inside a tiny `MaterialApp.router` defined in
the test itself: two routes — the one under test, and a stub destination
that renders `const Text('detail-stub')`. Services are constructed directly
and passed into the screen via its constructor (bypassing `get_it`). Tap,
`pumpAndSettle`, then `expect(find.text('detail-stub'), findsOneWidget)` —
that proves the real `goNamed` call reached the right route.

**Trade-offs.**
- Verifies the actual `go_router` call without booting the whole app router.
- Destination assertion is concrete and readable (a visible widget),
  avoiding `routeInformationProvider` plumbing.
- Duplicates the route path in the test, which must stay in sync with
  `AppRoute` — one more place to update on rename.
- Slightly more code per test than approach 1; noticeably less than
  approach 2.

**When it's the right choice.** When navigation *correctness* matters but
booting the app's full router feels heavy for a single-screen test.

**Main risk.** If `AppRoute` paths drift and tests aren't updated, the stub
router silently diverges from reality and tests pass against a fiction.

### Approach 4: Mocktail mocks layered on any of the above

**Sketch.** Same structure as approach 1, 2, or 3, but replace hand-rolled
fakes with `mocktail` mocks and use `when(...).thenAnswer(...)` to seed
repository responses. For navigation, a `MockGoRouter` wrapped in an
`InheritedGoRouter` can intercept calls and `verify(() => router.goNamed(...))`.

**Trade-offs.**
- Flexible per-test stubbing; easier to mix behaviors across tests without
  writing a fake variant each time.
- Adds a dev dependency and a small amount of boilerplate
  (`registerFallbackValue`, class-per-mock).
- Harder to read for someone unfamiliar with mocktail; hand-rolled fakes
  read like normal Dart.
- Mocking the repository means the service's `AsyncValue` transitions still
  run for real — the benefit over a hand-rolled fake is marginal when the
  interface is small.

**When it's the right choice.** When repositories have wide surfaces and
test-specific behavior tuning would be painful to express as fakes.

**Main risk.** The team standardizes on a mocking dependency before knowing
if it's needed — hand-rolled fakes often suffice for small repositories.

## At a glance

| Approach | Setup weight | Verifies real `go_router` call | Pattern reusability across screens | New deps |
|----------|--------------|-------------------------------|-----------------------------------|----------|
| 1. Component + callback spy | Very low | No | Only callback-style widgets | None |
| 2. Screen + real app router | High | Yes | High (uniform pattern) | None |
| 3. Screen + stub-destination router | Medium | Yes | High (uniform pattern) | None |
| 4. Mocktail layered on any | +small | Depends on base | Same as base | `mocktail` |

## Recommendation

**Chosen: a hybrid of approaches 2 and 4, modified to use a single-route
test router instead of the full `AppRoute` table.**

Concretely:

- Pump the full `MainScreen` inside a `MaterialApp.router` whose `GoRouter`
  contains a **single route** pointing at `MainScreen`, with a
  `NavigatorObserver` (or equivalent push spy) attached to record
  navigation events.
- In `setUp`, reset `get_it` and register **fake repositories for every
  feature `MainScreen` touches** — recipes, collections, shopping lists,
  and planning (list, calendar, visibility). Real services run on top of
  those fakes, keeping the repository-only mocking rule from the
  requirements intact uniformly across tabs.
- Use **mocktail** for the fake repositories.
- Scope assertions to the recipes tab (assumed default on mount). Three
  tests: empty state, populated grid, tap-to-navigate — the last asserted
  by inspecting the recorded navigation event for `AppRoute.recipeDetail`
  with the tapped recipe's id.

**Why this combination over the runners-up.** Pumping the real `MainScreen`
(vs. approach 1's `RecipeGrid`-only test) keeps the test honest about the
production widget tree and makes the pattern generalize to future screens
whose navigation lives inline. Fakes-for-everything avoids carving per-tab
exceptions to the "mock only at the repository boundary" rule. The
single-route test router (vs. approach 2's real router) sidesteps the cost
of making every destination constructible under test, while still letting
us verify navigation through a real `go_router` rather than coupling to its
internal inherited-widget API — which also insulates the test from
go_router version upgrades better than mocking `InheritedGoRouter` would.
Mocktail is accepted because the team already prefers it and the boilerplate
cost is small.

What we give up: `setUp` will grow each time `MainScreen` adopts a new
service, and go_router upgrades can still affect us through its public API.
Both are accepted as worth the fidelity gain.

## Questions for design

- Exact shape of the push spy — `NavigatorObserver.didPush` reading the
  route's name/arguments, or reading
  `GoRouter.routeInformationProvider.value` after `pumpAndSettle`. Pick one
  for the standard.
- How fakes are registered: directly via `getIt.registerSingleton(fakeRepo)`
  in the test, or by invoking each feature's `setup<Feature>({repository:
  fake})` function to reuse production wiring.
- Where shared test helpers (pumping `MainScreen`, building the test
  router, resetting `get_it`) live — given the requirement to defer a
  reusable harness until 2–3 screens are tested, likely a single local
  helper file under `test/features/recipe/` for now.
- Whether to assert the transient loading state explicitly, or only
  terminal states (already flagged as open in requirements).
- Mocktail's `registerFallbackValue` needs for any value types passed to
  repository methods.
- Whether the recipes tab is reliably the default tab on `MainScreen`
  mount, or the test needs to programmatically select it first.
