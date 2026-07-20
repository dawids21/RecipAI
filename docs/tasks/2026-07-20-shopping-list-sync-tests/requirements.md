# Shopping list sync tests

**Date:** 2026-07-20
**Type:** feature
**Status:** requirements

## Summary

Add a unit-test suite for the shopping-list item sync path, treating the sync
service, store service, DAO, and items repository as a single unit tested
against a faithful in-memory fake backend and a real in-memory SQLite database.
Tests drive sync operations directly and deterministically (no timers, no
automatic draining) so each test fixes one ordering of push/reconcile and
asserts the resulting end state.

## Context

The shopping-list item sync path — `ShoppingListSyncService` +
`ShoppingListItemStoreService` + `ShoppingListItemDao` +
`ShoppingListItemRepository` — currently has no unit coverage. It is the most
concurrency- and ordering-sensitive area of the mobile app: it drains a
per-list outbox to the backend, reconciles accepts/conflicts/discards, and
merges server pulls into a local cache + SQLite store under per-list locks.

The code was not written with testability in mind; it relies on timers and
fire-and-forget draining to make progress. This suite establishes coverage of
the sync/reconcile logic by exercising the real store + DAO + a stateful fake
backend, controlling the exact order of operations from the test rather than
through the production timer/automation machinery.

## Requirements

- The unit under test is the composition of the real sync service, real store
  service, real DAO (over an in-memory SQLite database), and a stateful fake
  items repository standing in for the backend. `AuthService` is faked to
  supply a token.
- Tests control the order of sync operations directly and awaitably. A test
  triggers an individual push/drain step and an individual reconcile (server
  pull) step on demand and awaits each to completion. Tests never depend on
  timers, backoff scheduling, poll intervals, or automatic/background draining
  to make progress. (The specific test-facing entry points are a design-phase
  decision.)
- Each test exercises a **single** ordering of operations and asserts that
  ordering's own expected end state — never a single test that runs two
  orderings and compares them.
- A typical test: (1) seeds the fake backend state and the local DB/store;
  (2) optionally applies local mutations through the store service
  (`applyCreate`, `applyEdit`, `applyChecked`, `applyReorder`, `applyDelete`,
  and the bulk operations); (3) runs sync steps in a chosen order; (4) asserts
  the final state across all observed surfaces.
- Coverage should span most store operations (create, edit, check, reorder,
  delete) and most push outcomes: accept (200/201), 412 conflict
  (cascade-discard to the winner), 404 gone (discard), 400/403 rejected
  (discard), offline (network exception), and transient failure followed by
  retry.
- Some scenarios involve multiple lists — e.g. draining lists independently and
  the start/resume fan-out over `listIdsWithOutbox`.
- The exact scenario list and count are chosen during the design phase, likely
  starting from the most common/representative cases.

## Anti-requirements

- No assertions on timing or scheduling. Backoff durations, the 10-second poll
  interval, the offline-retry cadence, and app-lifecycle (pause/resume)
  behavior are out of scope. Tests assert state convergence, not *when* things
  happen.
- Not driving the unit through its production timer/automation. Automatic
  draining and periodic polling are bypassed in favor of explicit, awaited
  steps.
- Not testing UI: no widgets, no `ValueNotifier`-to-view rendering. (The
  notifier's *value* is inspected as state; the UI that consumes it is not.)

## Constraints & assumptions

- Uses `sqflite_common_ffi` for a real in-memory SQLite database, injected via
  the existing `ShoppingListItemDao(db)` constructor. `sqflite_common_ffi` is
  not currently a dev dependency and must be added.
- The fake items repository is **stateful and as faithful to the real backend
  as possible**. It enforces optimistic concurrency on every push:
  - create returns a server item at version 0;
  - update bumps the version and returns the new winner;
  - a push carrying a stale `baseVersion` returns 412 with the current winner;
  - a push against a missing item returns 404-gone.
  412 and 404-gone thus fall out of genuine version/state. The outcomes with no
  state cause — offline, transient failure, and 400/403-rejected — are driven by
  explicit fault flags on the fake, toggled per scenario.
- The fake also exposes a **direct state-setter** so a test can inject an
  out-of-band remote change (simulating another device/user) — e.g. replacing
  an item with a newer version — which the next server pull or the next push's
  412-winner then reflects.
- Test-only affordances on the production code are acceptable, and broader
  refactors are acceptable **when clearly justified** by test-driveability, not
  made speculatively.
- `AuthService` is faked to return a token; auth behavior itself is not under
  test.

## Acceptance criteria

- [ ] `sqflite_common_ffi` is added as a dev dependency and initialized for the
      test suite.
- [ ] A stateful fake items repository exists that enforces backend optimistic
      concurrency (create/update/delete version semantics, 412/404/400/403) and
      supports directly setting/replacing backend item state.
- [ ] The suite drives sync operations deterministically without relying on
      timers, backoff, poll intervals, or automatic draining.
- [ ] Each test fixes a single operation ordering and asserts that ordering's
      expected end state.
- [ ] Each scenario asserts the expected final state across **all four**
      surfaces: the DB `items` rows, the fake backend's item set, the in-memory
      cache / visible-items notifier value, and an empty `outbox` table (unless
      the scenario's explicit expectation says otherwise).
- [ ] The expected end state is stated explicitly per scenario. In normal
      scenarios local and backend are identical at quiescence; scenarios that
      end in a failure state (or otherwise legitimately diverge) assert that
      divergence explicitly.
- [ ] Coverage spans most store operations and most push outcomes (accept, 412,
      404, 400/403, offline, transient-then-retry).
- [ ] At least some scenarios involve multiple lists.

## Edge cases

- Ordering permutations of push vs. reconcile against the same starting
  divergence (each as its own test).
- 412 conflict rollback via cascade-discard to the server winner, including a
  rejected delete that must be un-tombstoned.
- 404-gone and 400/403-rejected discards, where the local row is hard-removed
  with no winner to roll back to.
- Still-dirty local edits that have not yet been pushed at the point of a
  reconcile (must not be clobbered by the pull).
- Scenarios that end in `SyncStatus.failure`, where local/backend divergence is
  the expected outcome.
- Multi-list concurrency and the start/resume fan-out over
  `listIdsWithOutbox`.
- Queued multi-entry sequences for a single item (e.g. create then edit before
  either is pushed) and the acked-seq / dirty-clearing bookkeeping.

## Integration points

- `mobile/lib/features/shopping_list/shopping_list_sync_service.dart` — unit
  under test.
- `mobile/lib/features/shopping_list/shopping_list_item_store_service.dart` —
  real, under test (mutations + reconcile paths + cache/notifier).
- `mobile/lib/features/shopping_list/shopping_list_item_dao.dart` — real, over
  an injected in-memory SQLite database.
- `mobile/lib/features/shopping_list/shopping_list_item_repository.dart` —
  replaced by the stateful fake backend; its response-classification contract
  (201/200/412/404/400/403/network) is the fidelity target.
- `AuthService` (`mobile/lib/features/auth/auth_service.dart`) — faked token
  source.
- New tests under `mobile/test/features/shopping_list/`; fake backend and any
  shared harness under `mobile/test/`.
- `mobile/pubspec.yaml` — add `sqflite_common_ffi` dev dependency.

## Open questions

- The exact scenario list and total count (chosen in design, starting from the
  most common cases).
- Which specific multi-list scenarios to include.
- The precise final-state expectation for each individual scenario, decided
  case by case (including where legitimate divergence is expected).
