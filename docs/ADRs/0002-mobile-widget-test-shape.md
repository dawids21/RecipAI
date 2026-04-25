# ADR-0002: Mobile widget tests pump the real screen against a single-route test router with mocktail repositories

**Date:** 2026-04-22
**Status:** accepted
**Related ADRs:** —

## Context

The Flutter mobile app had no automated tests when this decision was made.
AI agents were writing a growing share of the code and the team needed a
fast feedback loop that would catch UI regressions without booting a real
backend or the full dependency graph.

The app's architecture fixes some variables up front:

- A strict Repository → Service → View layering, where services expose
  `ValueNotifier<AsyncValue<T>>` and are injected into views via
  constructor.
- `get_it` as the service locator, with per-feature `setup*()` functions.
- `go_router` with a single `AppRoute` enum and `context.goNamed(...)` used
  inline inside widgets for navigation (not via injected callbacks in most
  places).
- Screens that own multiple services at once — the main landing screen
  hosts several feature tabs in one widget tree.

Forces in tension when choosing a widget-test shape:

- **Fidelity**: we want the test to exercise the production widget tree and
  the production navigation call, not a reduced stand-in. A test that
  passes while the real screen is broken has negative value.
- **Speed and ceremony**: widget tests must stay fast and cheap enough that
  writing one is the default, not a chore. Booting the full app router and
  making every destination constructible under test is heavyweight and
  grows with the route table.
- **Pattern reusability**: this is the first test, but the team needs a
  recipe that transfers to future screens. A pattern that only works for
  widgets whose navigation is passed in as a callback won't transfer —
  most screens call `context.goNamed(...)` inline.
- **Mocking boundary**: the only external boundary we want to fake is the
  repository layer. Services, navigation, and the widget tree itself
  should run as production code.
- **Decoupling from `go_router` internals**: mocking
  `InheritedGoRouter` / `GoRouter` directly couples tests to go_router's
  private API and makes version upgrades harder. Observing navigation via
  `Navigator`'s public API (`NavigatorObserver`) is more stable.

## Decision

Widget tests for the mobile app pump the **real screen** inside a
test-local `MaterialApp.router` whose `GoRouter` contains a **single route
pointing at the screen under test**, with a `NavigatorObserver` subclass
attached to record every `didPush`. Navigation is asserted by inspecting
`route.settings.name` on the recorded pushes against the expected
`AppRoute.<value>.name`.

Repositories — including `AuthRepository` — are replaced with **`mocktail`
mocks** and injected through each feature's `setup*()` function, which
takes a nullable `{<Repo>? <repo>}` override parameter. Real services
(including `AuthService`) run on top of the mocked repositories. The
repository-boundary mocking rule applies uniformly: nothing is mocked
above the repository layer. `get_it.reset()` runs in `tearDown`. Only
terminal `AsyncValue` states (`data`) are asserted; the transient loading
state is skipped unless a specific regression warrants it.

The screen-specific setup — instantiating mocks, stubbing auth defaults,
calling each feature's `setup*()`, building the single-route `GoRouter`,
attaching the observer, and calling `pumpWidget` — lives inline in the
test file's `setUp` and test bodies. It is **not** extracted into a
shared harness/builder file. Cross-test sharing is limited to type
declarations (the `Mock*` classes in `test/support/mocks.dart`). A
generalized pump-any-screen helper is deferred until 2–3 screens are
tested and a reusable shape becomes evident.

A new mobile standards document (`docs/mobile/standards/widget-testing.md`)
captures the shape so future screens follow the same recipe.

## Alternatives considered

- **Component test with callback spy.** Pump the leaf widget only
  (e.g., `RecipeGrid`), inject a callback in place of real navigation, and
  assert on the callback. **Rejected** because most screens in this app
  call `context.goNamed(...)` inline rather than accepting a navigation
  callback — the pattern wouldn't transfer to screen 2 without reworking
  the production widget to accept a callback purely for test purposes.

- **Screen test with the real app router (`createAppRouter()`).** High
  fidelity and maximally reusable. **Rejected** for this first pass
  because it requires every route in `AppRoute` to be constructible under
  test, which drags in every feature's `setup*()` and pushes the "first
  widget test" toward de-facto integration-test weight before the suite
  even has ten tests.

- **Mocking `GoRouter` / `InheritedGoRouter` directly to intercept
  `goNamed`.** Removes the need for any test router at all. **Rejected**
  because it couples tests to go_router's inherited-widget internals,
  which have changed between major versions; a `NavigatorObserver` is
  public `Navigator` API and survives go_router upgrades.

- **Reading `GoRouter.routeInformationProvider.value.uri` after
  `pumpAndSettle`.** Also works without a stub destination, using
  `go_router`'s public state. **Rejected** as the primary mechanism
  because assertions tend to race with settle timing and URI matching is
  less direct than matching on a route `name`. We prefer the observer.

- **Hand-rolled fake repositories instead of mocktail.** Readable without
  knowing a mocking library. **Rejected** because the team already prefers
  mocktail and per-test stubbing is more flexible as repository surfaces
  grow; the boilerplate cost (one class per repository, occasional
  `registerFallbackValue`) is small.

- **Mocking `AuthService` directly instead of `AuthRepository`.** Simpler
  because `AuthService` surface is small. **Rejected** because it
  violates the "mock only at the repository boundary" rule for one
  service while enforcing it everywhere else; the `AuthRepository`
  interface is already abstract, so mocking it is no harder and keeps
  the rule uniform.

- **Pre-registering mocks in `get_it` before calling the unmodified
  `setup*()` functions.** Keeps production wiring untouched. **Rejected**
  because two setup functions (`setupRecipe`, `setupMealPlan`) call
  `registerSingleton<Repo>(Repo())` unconditionally, which collides with
  a pre-registered mock. Extending those two setup functions to accept a
  `{<Repo>? <repo>}` override parameter — matching the pattern the other
  three setup functions already use — is a smaller and more consistent
  change than working around the collision in test code.

- **Extracting screen setup into a `buildMainScreenHarness()` helper
  under `test/support/`.** Hides the boilerplate (mock instantiation,
  `setup*()` calls, router/observer construction, `pumpWidget`) behind a
  single call. **Rejected** for the first screen. With only one screen
  under test, an extracted helper is premature — it would lock in a
  shape before we have evidence of which parts repeat unchanged across
  screens and which need per-screen variation (different service mixes,
  different default stubs, different starting tabs/routes). Inlining the
  setup keeps each test file self-contained and readable end-to-end, and
  defers the abstraction decision to when 2–3 screens give us a real
  signal about the right boundary. Cross-test sharing in the meantime is
  limited to type declarations (`Mock*` classes).

## Consequences

**Easier**

- Writing a widget test for any screen now has a single recipe: pump the
  real screen, register repository mocks, attach a `NavigatorObserver`,
  assert on route names. The pattern transfers to screens whose navigation
  lives inline (the common case).
- Navigation assertions survive `go_router` upgrades that change
  inherited-widget internals, because they go through `Navigator`'s public
  API.
- Service-level state transitions (`AsyncValue.loading` → `data` / `error`)
  are exercised incidentally in every test, because real services run on
  top of the mocked repositories.

**Harder / committed to**

- Every screen under test requires mocks for every repository its services
  touch. For a screen like `MainScreen` this is a handful of mocks on
  every test file. Rewriting this into a `repository:` override on each
  `setup*()` is a plausible follow-up when the boilerplate bites.
- Test setup is coupled to each screen's service composition. When a
  screen adopts a new service, the screen's test `setUp` must add the
  corresponding repository mock. This is accepted because the fidelity
  gain (running the real widget tree) is worth it.
- `AuthService` sits above the repository boundary but is consumed by most
  services, so tests need either a minimal `AuthService` fake or a mocktail
  mock for it. Doing this consistently is on the pattern documentation.
- The standards doc becomes a load-bearing artifact — if it drifts, the
  pattern drifts. Updates to the pattern (new harness helpers, new
  `setup*()` override parameters) must be reflected there.

**Follow-ups this implies**

- After 2–3 screens are under test, extract a generalized
  "pump-any-screen" helper and, likely, add `repository:` override
  parameters to each feature `setup*()`.
- Wire `flutter test` into CI once the first few tests have proven stable
  locally.
