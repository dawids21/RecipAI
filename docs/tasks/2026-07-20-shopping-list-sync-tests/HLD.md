# Shopping list sync tests — High-level design

**Date:** 2026-07-21
**Status:** final
**ADRs:** docs/ADRs/0005-shopping-list-sync-test-seam.md

## Summary

Add a unit-test suite that treats the shopping-list item sync path — sync
service + store service + DAO + a stateful fake backend — as a single unit,
exercised over a real in-memory SQLite database. To make the timer-driven,
fire-and-forget sync service drivable, inject an inert scheduler for its timers,
expose awaitable single-step push/reconcile entry points, and make the drain
kick and the multi-list fan-out return a future the caller can await. Each test
fixes one exact ordering of steps and asserts that ordering's end state;
production keeps its fire-and-forget behaviour by discarding those futures with
`unawaited()`.

## Approach

### Chosen

**Inject a scheduler and expose single-step entry points (ADR-0005).** The unit
under test is the real `ShoppingListSyncService` composed with the real
`ShoppingListItemStoreService`, the real `ShoppingListItemDao` over an in-memory
SQLite database, a stateful fake standing in for
`ShoppingListItemRepository`, and a faked auth token source. Determinism comes
from three production seams that keep behaviour equivalent:

- **An injected scheduler owns all timer creation** (poll, drain, backoff,
  offline). Production uses real timers; tests use an inert scheduler that never
  fires, so no timer runs mid-test and a stalled drain can never spin on a
  backoff/offline kick.
- **Polling is decoupled from draining, and the push of one outbox entry is an
  awaitable test-visible step.** A server pull is pure fetch-and-reconcile with
  no drain kick; a dedicated drain timer (through the scheduler, inert in tests)
  drives periodic draining in production. Tests push one entry at a time — with
  the per-list sync lock acquired inside the push step so real locking still
  runs — and choose the interleaving of pushes and reconciles explicitly.
- **The drain kick and the multi-list fan-out are awaitable.** The per-list
  drain kick returns the future of its drain loop, and the fan-out over
  `listIdsWithOutbox` awaits/collects those per-list futures, so the fan-out
  method itself is awaitable. Production discards these futures with
  `unawaited()`, so it stays fire-and-forget with timing unchanged; a multi-list
  test instead awaits the fan-out to quiescence and asserts the converged end
  state directly, rather than inspecting which lists were scheduled.

Each test seeds the fake backend and the local DB/store, optionally applies
local mutations through the store, runs sync steps in a chosen order, and
asserts the final state across four surfaces: the DB `items` rows, the fake
backend's item set, the in-memory cache / visible-items notifier value, and an
empty `outbox` table (unless the scenario says otherwise). Transient/offline
retry is re-driven by calling the push step again after changing the fake's
state — never by waiting on a scheduled kick.

Push-outcome and ordering scenarios drive the single-entry push step directly,
bypassing the drain loop, in exchange for precise, deterministic control over
step ordering — which is what those scenarios turn on. The multi-list fan-out
scenario instead awaits the real drain loop to empty, so the loop itself is
exercised there; only its mid-drain single-flight coalescing guard (a kick
arriving while a list is already draining) stays untargeted.

### Rejected alternatives

- **Extract a pure sync-engine object** — Restructures the app's most delicate
  concurrency code before any coverage exists; disproportionate to adding tests,
  and the split itself could introduce the very regression no test yet guards.
- **Thin test hooks without scheduler injection** — The push path arms real
  backoff/offline timers on a stall, leaking a live timer that later fires a
  stray background drain and reintroduces the non-determinism the suite must
  eliminate.
- **`fakeAsync` with zero production change** — Making progress requires
  advancing virtual time through backoff and poll cadence — the exact timing
  dependence the requirements forbid — and `sqflite_common_ffi`'s real async I/O
  does not run inside a `fakeAsync` zone.

## Feature areas

### Sync-service test seams

**Key behaviors.**
- All timer creation (poll, drain, backoff, offline) is routed through an
  injected scheduler; production wiring passes a real-timer scheduler.
- A server pull performs fetch-and-reconcile only and does not kick a drain; a
  separate drain timer owns the periodic drain-kick in production.
- Pushing a single outbox entry is an awaitable, test-visible step; the per-list
  sync lock is acquired inside that step so a direct call exercises real locking.
- A fetch-and-reconcile step is awaitable and test-visible.
- The per-list drain kick returns the future of its drain loop, and the fan-out
  over `listIdsWithOutbox` awaits/collects those futures so the fan-out method is
  itself awaitable end-to-end.
- Production behaviour is equivalent to today's: draining stays asynchronous
  (every production call site discards the drain/fan-out future with
  `unawaited()`), the lock is still acquired and released per entry, and
  retry/offline timing is unchanged.

### Inert scheduler (test double)

**Key behaviors.**
- Never fires a timer; holds all scheduled work inert for the duration of a test,
  so no poll/backoff/offline/drain timer runs mid-test and a stalled drain cannot
  spin on a scheduled kick.

### Stateful fake backend

**Key behaviors.**
- Stands in for the item repository and holds authoritative item state per list,
  faithful to the real backend's optimistic-concurrency contract.
- Create returns a server item at the initial version; update bumps the version
  and returns the new winner; delete removes the item.
- A push carrying a stale base version returns the 412 conflict outcome with the
  current winner, and a push against a missing item returns 404-gone — both fall
  out of genuine item state.
- The outcomes with no item-state cause — offline, transient failure, and
  400/403-rejected — are driven by explicit fault flags on the fake, toggled per
  scenario rather than derived from state.
- Exposes a direct state-setter so a test can inject an out-of-band remote change
  (another device/user) that the next pull or the next push's 412-winner
  reflects.

### Faked auth

**Key behaviors.**
- Supplies an id token to the sync service so pushes and pulls carry auth.
- Uses a mocked `AuthRepository` under a real `AuthService`, matching the
  repository-boundary rule in the widget-testing standard; auth behaviour itself
  is not under test.

### In-memory database harness

**Key behaviors.**
- Initializes `sqflite_common_ffi` for the test suite and opens a fresh
  in-memory database per test, injected via the existing `ShoppingListItemDao`
  constructor.
- Applies the same schema production creates (items + outbox), single-sourced so
  DAO behaviour under test matches production.

### Four-surface assertion harness

**Key behaviors.**
- Reads and compares all four surfaces: DB `items` rows, the fake backend's item
  set, the visible-items notifier value / cache, and outbox emptiness.
- States each scenario's expected end state explicitly. At quiescence local and
  backend are identical for convergent scenarios; scenarios that legitimately
  diverge (e.g. ending in a failure state) assert the divergence explicitly.

### Scenario catalog

**Key behaviors.** Roughly 15–18 scenarios, each fixing one ordering and
asserting its own end state:
- **Store-op happy paths:** create, edit, check, reorder, and delete each pushed
  and accepted.
- **Push outcomes** on a representative op: 412 cascade-discard to the winner
  (including a rejected delete that must be un-tombstoned), 404-gone discard,
  400/403-rejected discard, offline stall, and transient-then-retry-succeeds.
- **Ordering edge cases:** a still-dirty local edit not clobbered by a concurrent
  reconcile; a queued multi-entry sequence for one item (create then edit before
  either is pushed) and the acked-seq / dirty-clearing bookkeeping; push-vs-
  reconcile order permutations against one starting divergence, each as its own
  test.
- **Multi-list:** two lists drained independently, and the start/resume fan-out
  over `listIdsWithOutbox` — driven by awaiting the fan-out method to quiescence
  and asserting both lists' converged end state across the four surfaces.

## Out of scope

- The drain loop's mid-drain single-flight coalescing/`_pending` guard as a
  coverage target — a kick arriving while a list is already draining. The
  multi-list fan-out scenario does exercise the drain loop itself (awaited to
  empty), but no scenario deliberately races a second kick against an in-flight
  drain.
- Any change to production sync/reconcile *logic*; the seams change wiring and
  entry points only, keeping behaviour equivalent.

## Open questions

- The exact shape of the awaitable single-step push/reconcile entry points —
  a task-design detail.
- What the drain kick returns when a kick is coalesced into an already-running
  drain (there is no in-flight future to hand back). Harmless for the fan-out
  scenario, whose list IDs are distinct, so left as a task-design detail rather
  than resolved here.
- The precise interface of the injected scheduler and how it is wired in
  `main()` / feature setup.
- The precise final-state expectation for each individual scenario, decided case
  by case (including where legitimate divergence is expected).
- Whether any shared test harness (fake backend, DB setup, assertion helpers)
  lives in one support file from the start or is inlined until a reusable shape
  emerges, per the widget-testing standard's "don't build harnesses early" rule.
