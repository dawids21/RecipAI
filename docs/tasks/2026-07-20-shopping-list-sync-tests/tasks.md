# Shopping list sync tests — Tasks

**Date:** 2026-07-22
**Status:** final

## Summary

- **T1:** Add sync-service test seams (inject scheduler, decouple poll from drain, single-entry push/reconcile steps)
- **T2:** Build test doubles + one reviewed reference test
- **T3:** Write the full scenario catalog

The "someone" giving feedback here is the developer of this suite: T1 ships
drivable production seams they can construct and await, T2 ships a harness and a
single test whose *structure* the user reviews, and T3 fills out the scenarios
using the approved structure.

## Cross-task notes

- **T2 is a deliberate feedback checkpoint.** T3 must not start until the user
  has reviewed T2's single test and the harness shape (test doubles, four-surface
  assertion, single-ordering-per-test pattern). This is the whole point of the
  split — do not fold T2 and T3 together.
- **T1 vs. T2 review split.** T1 is reviewable on its own because it must leave
  production behaviour equivalent (no observable app change); T2 is reviewed for
  test structure (and carries one small, behaviour-preserving production refactor
  — the DB factory). They are kept separate per the requested split even though
  both are pre-suite groundwork.
- **Harness-file discipline.** Per the widget-testing standard, keep doubles and
  helpers inlined (or in `test/support/` for type declarations only) until a
  reusable shape is evident. Do not pre-build a harness in T2; let T3 extract one
  only if repetition demands it (HLD Open questions).

---

## T1: Add sync-service test seams

**User-visible outcome**

A developer can construct `ShoppingListSyncService` with an injected scheduler
and drive it step-by-step — awaiting a single outbox-entry push and a single
fetch-and-reconcile in isolation — while the shipping app behaves exactly as it
does today.

**Scope**

- Route all timer creation (poll, drain, backoff, offline) through an injected
  scheduler; wire a real-timer scheduler in `main()` / `shopping_list_setup.dart`.
- Decouple polling from draining: a server pull becomes pure fetch-and-reconcile
  and no longer kicks a drain; add a dedicated drain timer (via the scheduler)
  that owns the periodic drain-kick in production.
- Expose an awaitable, test-visible single-entry push step with the per-list sync
  lock acquired *inside* it, and an awaitable fetch-and-reconcile step
  (`@visibleForTesting`).
- Make the per-list drain kick return the future of its drain loop and have the
  fan-out await/collect those futures, so the fan-out method is awaitable; every
  production call site keeps fire-and-forget behaviour via `unawaited()`.
- HLD feature area: Sync-service test seams. Keep production behaviour equivalent
  (async draining, per-entry lock acquire/release, retry/offline timing unchanged).

**Out of scope**

- Any test double or test — covered in T2.
- Changing reconcile/classification logic or the store — HLD Out of scope; seams
  change wiring and entry points only.
- Covering the drain loop's single-flight coalescing guard — HLD Out of scope.

**Depends on:** none

**HLD references**

- `HLD.md` > Feature areas > Sync-service test seams
- `docs/ADRs/0005-shopping-list-sync-test-seam.md`

**How to verify**

- App builds and a manual shopping-list sync on device still works end-to-end:
  add/edit/check/delete an item, background then foreground the app, confirm
  changes converge and the sync indicator behaves as before.
- The new scheduler seam and `@visibleForTesting` push / reconcile steps are
  present and callable — a throwaway `flutter test` snippet constructing the
  service with an inert scheduler and awaiting one push step compiles and runs.

**Risks / unknowns**

- This touches the app's most concurrency-sensitive code with no coverage yet
  (ADR-0005): a wiring regression here is the one thing no test catches until T2.
  Lean on behaviour-equivalence review and the manual smoke.
- Exact entry-point shape and how a test observes push completion is a
  task-design detail (HLD Open questions); settle it here since T2 builds on it.

---

## T2: Build test doubles + one reviewed reference test

**User-visible outcome**

The user can read one complete, passing test — a store-op happy path (e.g.
create pushed and accepted) — plus its supporting doubles, and give feedback on
the structure that every later test will follow.

**Scope**

- Build the test doubles from the HLD: inert scheduler, stateful fake
  backend (optimistic-concurrency-faithful, with a direct state-setter and plain
  bool fault flags), faked auth (mocked `AuthRepository` under a real
  `AuthService`), and the in-memory `sqflite_common_ffi` DB harness injected via
  the existing DAO constructor.
- Move DB creation into a `ShoppingListItemDatabaseFactory` (production +
  in-memory test subclass) so the test DB shares production's schema — a
  behaviour-preserving production refactor justified by test-driveability.
- Stand up the four-surface assertion approach (DB `items` rows, fake backend
  item set, visible-items notifier/cache, outbox emptiness).
- Write exactly one scenario end-to-end using T1's single-step entry points,
  fixing one push/reconcile ordering and asserting the explicit end state.
- Place tests under `mobile/test/features/shopping_list/`, mirroring `lib/`.

**Out of scope**

- The remaining ~14–17 scenarios — covered in T3.
- Extracting a shared harness/builder file — deferred to T3, and only if a
  reusable shape emerges (widget-testing standard; HLD Open questions).

**Depends on:** T1

**HLD references**

- `HLD.md` > Feature areas > Inert scheduler / Stateful fake backend /
  Faked auth / In-memory database harness / Four-surface assertion harness
- `HLD.md` > Feature areas > Scenario catalog (pick one store-op happy path)
- `docs/mobile/standards/widget-testing.md` (repository-boundary rule,
  `test/support/` discipline, setUp/tearDown lifecycle)

**How to verify**

- `flutter test test/features/shopping_list/` passes with the single new test
  green, and the user confirms the test's structure is the shape they want
  before T3 proceeds.
- The DB-factory refactor is behaviour-preserving: `flutter analyze` is clean and
  the app still opens its on-device shopping-list database via the new factory.

**Risks / unknowns**

- The fake must reproduce 412 and 404-gone faithfully from genuine version/state,
  and expose clean bool fault flags for the outcomes with no state cause (offline,
  transient, 400/403-rejected) — get this shape right here so T3's push-outcome
  scenarios rest on it.

---

## T3: Write the full scenario catalog

**User-visible outcome**

A developer has deterministic unit coverage of the shopping-list item sync path:
the full catalog of ~15–18 single-ordering scenarios runs green, each asserting
its own fixed end state.

**Scope**

- Implement the remaining scenarios from the HLD catalog, following T2's approved
  structure: store-op happy paths (create/edit/check/reorder/delete), push
  outcomes (412 cascade-discard incl. un-tombstoned rejected delete, 404-gone,
  400/403-rejected, offline stall, transient-then-retry-succeeds), ordering edge
  cases (still-dirty edit not clobbered, queued multi-entry sequence with
  acked-seq/dirty bookkeeping, push-vs-reconcile permutations), and multi-list
  (independent drains + start/resume fan-out driven by awaiting the fan-out method
  to quiescence and asserting converged end state).
- State each scenario's expected end state explicitly, including scenarios that
  legitimately diverge (end in a failure state).
- Extract shared helpers into a support file only if repetition makes the shape
  obvious.

**Out of scope**

- Production code changes — the sync seams landed in T1 and the test DB factory
  in T2.
- The drain loop's single-flight coalescing guard — HLD Out of scope.

**Depends on:** T2 (and its structural review sign-off)

**HLD references**

- `HLD.md` > Feature areas > Scenario catalog
- `HLD.md` > Approach > Chosen (single-ordering-per-test, four-surface assertion)
- `docs/ADRs/0005-shopping-list-sync-test-seam.md` (what is and isn't a coverage
  target)

**How to verify**

- `flutter test test/features/shopping_list/` runs the full catalog green, and
  the scenario list in the file matches the HLD catalog's coverage areas.

**Risks / unknowns**

- Per-scenario final-state expectations are decided case by case (HLD Open
  questions), especially where divergence is the correct end state.
