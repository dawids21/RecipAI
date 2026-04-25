# Mobile widget tests — first screen

**Date:** 2026-04-22
**Type:** feature
**Status:** requirements

## Summary

Establish widget testing in the Flutter mobile app by covering a single screen (recipe grid view as the likely candidate) and documenting the patterns used so subsequent screens can follow the same approach.

## Context

The mobile app currently has no automated tests. AI agents are writing an increasing share of the code, and the project needs a fast feedback loop that catches UI regressions without requiring a full backend and dependency stack to be spun up.

Widget tests are the right entry point for this app: unit tests are too narrow given that most business logic lives in the backend (the mobile app is primarily an integration layer and UI), and integration tests are too slow and heavy given the number of external dependencies that would need to be started. Widget tests hit the sweet spot — they verify that screens render correctly and respond to user interaction, while mocking out external communication.

This task is deliberately scoped to one screen. Broader coverage, shared test infrastructure, and CI integration will be considered later, once real patterns have emerged from doing it once.

## Requirements

For the chosen screen (recipe grid view as the working assumption):

- When the repository returns an empty list, the screen renders its empty state.
- When the repository returns a populated list of recipes, the screen renders the grid with the expected items.
- When the user taps a recipe card, the app navigates to the recipe detail destination (exact assertion strategy TBD — see Open questions).

A new standards document under `docs/mobile/standards/` captures the widget-test patterns used so future screens can be tested consistently.

## Anti-requirements

Explicitly out of scope for this task:

- Unit tests and integration tests — deferred until there is a concrete case for them.
- Golden / pixel-layout tests — not pursuing visual regression testing at this stage.
- Platform-specific (iOS vs Android) behavior — tests target the widget tree, not platform channels.
- A reusable test harness (pump helpers, shared mock builders) — deferred until 2–3 screens have been tested and real duplication becomes visible.
- CI integration — this pass produces local-only tests; wiring into CI is a follow-up.
- Covering more than one screen in this task.
- Mocking services. Only external dependencies (repositories) are mocked; real services run during tests.

## Constraints & assumptions

- **Architecture**: the app follows the Repository → Service → View pattern documented in `docs/mobile/standards/architecture.md`. Services expose `ValueNotifier<AsyncValue<T>>` and are injected into views via constructor.
- **Dependency injection**: `get_it` is used throughout the app. Tests will need to either override `get_it` registrations or bypass it by constructing services directly with fake repositories — the chosen approach will be documented in the new standards file.
- **Navigation**: the app uses `go_router`. The assertion strategy for navigation is not yet decided (see Open questions).
- **Mocking boundary**: repositories are the only thing mocked. Real service code runs against the fake repository, which means the service's `AsyncValue` transitions are exercised incidentally.
- **Mocking library**: not yet chosen (mocktail vs mockito). Decision deferred to design.
- **Directory layout**: tests live under the standard `test/` directory, mirroring `lib/`.

## Acceptance criteria

- [ ] One screen has passing widget tests covering the empty state.
- [ ] The same screen has passing widget tests covering the populated state.
- [ ] The same screen has a passing widget test covering tap-to-navigate behavior.
- [ ] Tests mock the repository layer only; services run as real code.
- [ ] Tests live under `test/` mirroring the `lib/` path of the screen under test.
- [ ] A new standards file under `docs/mobile/standards/` documents the widget-test patterns (how to mock a repository, how to pump a screen, how to assert navigation, how state is seeded via the fake repo).
- [ ] `flutter test` runs green locally.

## Edge cases

Not deeply explored during scoping. The `AsyncValue` loading → populated transition will be exercised implicitly because real services run against fake repositories, but no explicit list of edge cases was gathered. Edge-case depth will be revisited once the first test file exists and real gaps become visible.

## Integration points

- **Screen under test**: `lib/features/recipe/` — the recipe grid view is the working assumption; exact screen to confirm during design.
- **Repository to mock**: the corresponding `*_repository.dart` in the same feature directory.
- **Service under test (real)**: the corresponding `*_service.dart` in the same feature directory.
- **DI**: `lib/core/get_it.dart` and the feature's `*_setup.dart` — test setup must be reconciled with however `get_it` is initialized in the app.
- **Routing**: `lib/core/routes.dart` and the `go_router` configuration — relevant for navigation assertions.
- **Tests**: new files under `test/features/recipe/`.
- **Documentation**: new file under `docs/mobile/standards/` (name TBD — e.g., `testing.md` or `widget-testing.md`), plus an entry in `docs/INDEX.md`.

## Open questions

- **Navigation assertion strategy.** With `go_router`, should tests assert that a specific route was pushed, inject a callback and assert it was invoked, or render the destination screen and assert on it? Needs a brainstorming / design pass.
- **First screen confirmation.** Recipe grid view was the working example — confirm it is the right starting point, or pick a different screen if there is a better candidate.
- **Mocking library.** Mocktail vs mockito — to be decided during design.
- **Loading-state assertions.** Whether to explicitly assert the transient loading state, or only the terminal (empty / populated) states.
