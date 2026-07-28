# T1: Add sync-service test seams — Implementation Plan

**Date:** 2026-07-22
**Status:** final

## Required reading

**Docs & standards** (from `docs/INDEX.md`)
- `docs/mobile/standards/dependency-injection.md` — external-dependency-as-nullable-
  setup-parameter rule the `Scheduler` wiring must follow; constructor-injection-only rule.
- `docs/mobile/standards/architecture.md` — Repository-Service-View layering; the seam
  work stays inside the service layer and must not leak `dart:async` timers into views.
- `docs/mobile/standards/logging.md` — keep the existing `recipai.shopping_list.sync`
  logger names/levels unchanged; the refactor must not alter log call sites.

**Design & ADRs**
- `plans/T1-task-design.md` (this task) — component list, changed signatures, pseudo-code,
  decisions, assumptions to verify.
- `docs/ADRs/0005-shopping-list-sync-test-seam.md` — the three seams, behaviour-equivalence
  constraint, and what is / isn't a coverage target (drain-loop coalescing guard is not).
- `HLD.md` > Feature areas > Sync-service test seams — the behaviour-equivalence checklist
  (async draining, per-entry lock acquire/release, retry/offline timing unchanged).

**Code to mirror**
- `mobile/lib/core/preferences_service.dart` — style/shape for a small, dependency-light
  `core/` class (import order, single-responsibility, no `getIt` in body).
- `mobile/lib/features/shopping_list/shopping_list_sync_service.dart` — the file being
  refactored; every existing `Timer.periodic` / `Timer(...)` and `unawaited(...)` idiom is
  the pattern the new code must preserve.
- `mobile/lib/features/shopping_list/shopping_list_setup.dart` — existing
  `itemRepository`/`shoppingListRepository` nullable-param pattern to copy for `scheduler`.

## File inventory

- **CREATE** `mobile/lib/core/scheduler.dart` — `Scheduler`/`ScheduledTimer` interfaces + `RealScheduler` over `dart:async`.
- **MODIFY** `mobile/lib/features/shopping_list/shopping_list_sync_service.dart` — inject scheduler; route all timers through it; add drain timer; promote `PushResult`; add `pushNextEntry`/`fetchAndReconcile`; make `requestDrain`/`fanOutPending` awaitable; decouple poll from drain.
- **MODIFY** `mobile/lib/features/shopping_list/shopping_list_setup.dart` — add `Scheduler? scheduler` param defaulting to `RealScheduler()`, pass to service ctor, wrap `start()` in `unawaited()`.
- **MODIFY** `mobile/lib/features/shopping_list/shopping_list_detail_service.dart` — wrap the 3 `_syncService.requestDrain(...)` call sites (lines 75, 104, 184) in `unawaited()`.
- **MODIFY** `mobile/lib/features/shopping_list/shopping_list_item_import_service.dart` — wrap the single `syncService.requestDrain(listId)` (line 30) in `unawaited()`.

No dependency-manifest change: `synchronized ^3.4.1` and `dart:async` already present. `main.dart` needs no change — it calls `setupShoppingList(store: ...)` and the new `scheduler` param defaults.

## Step-by-step plan

1. **Add the `Scheduler` seam** — Create `mobile/lib/core/scheduler.dart` with
   `abstract interface class ScheduledTimer { void cancel(); }`,
   `abstract interface class Scheduler` (`periodic`/`oneShot` returning `ScheduledTimer`),
   and `class RealScheduler implements Scheduler`. `RealScheduler.periodic` wraps
   `Timer.periodic(duration, (_) => callback())`; `RealScheduler.oneShot` wraps
   `Timer(duration, callback)`; each returns a private `_RealScheduledTimer` holding the
   `Timer` and delegating `cancel()`. Note the callback signatures: `Scheduler` takes
   `void Function()` (no `Timer` arg), so the wrapper adapts `Timer.periodic`'s
   `(Timer)`-arg callback.
   - Files: `mobile/lib/core/scheduler.dart`
   - Verify: `cd mobile && dart analyze lib/core/scheduler.dart` reports no issues.

2. **Refactor the sync service and wire the scheduler** — commit these two files together
   so the tree compiles (the service ctor gains a `required Scheduler`, so setup must pass
   one in the same commit).

   In `shopping_list_sync_service.dart`:
   - Add `import '../../core/scheduler.dart';`.
   - Promote `enum _PushResult` → `enum PushResult` (public); rename all 8 in-file
     references (`_pushHeadEntry` return type + `_drainPass` switch + the `return`s).
   - Add `final Scheduler _scheduler;` field and `required Scheduler scheduler` ctor
     param (`_scheduler = scheduler`).
   - Retype timer maps `Timer` → `ScheduledTimer`: `_backoffTimers`, `_offlineTimers`,
     `_pollTimers`; add `final _drainTimers = <String, ScheduledTimer>{};`.
   - Replace every `Timer.periodic(...)`/`Timer(...)` with `_scheduler.periodic(...)`/
     `_scheduler.oneShot(...)`, dropping the `(_)`/`(Timer)` callback arg (scheduler
     callbacks take no arg): `_armBackoffTimer` (oneShot), `_armOfflineTimer` (periodic),
     `startPolling` (periodic), `didChangeAppLifecycleState` resume (periodic).
   - Make `requestDrain` return `Future<void>`: coalesced branch `return Future.value();`
     else `return _drain(listId);`. Update `retry` to `unawaited(requestDrain(listId));`.
   - Add `@visibleForTesting Future<PushResult> pushNextEntry(String listId) =>
     _syncLockFor(listId).synchronized(() => _pushHeadEntry(listId));` and change
     `_drainPass` to `await pushNextEntry(listId)` (loop no longer takes the lock itself).
   - Add `@visibleForTesting Future<List<ShoppingListItem>> fetchAndReconcile(String listId)`
     holding the exact body currently inside `_poll`'s `synchronized(...)` block
     (auth token → `fetchServerItems` → `reconcileFromServer` → return items).
   - Rewrite `_poll` to `final items = await fetchAndReconcile(listId);` inside the same
     try/catch, and **remove** its `requestDrain(listId)` line (poll no longer kicks a drain).
   - In `startPolling`: after arming the poll timer, arm a drain timer —
     `_drainTimers.remove(listId)?.cancel(); _drainTimers[listId] =
     _scheduler.periodic(_pollInterval, () => unawaited(requestDrain(listId)));`.
   - In `stopPolling` and `dispose`: cancel/clear `_drainTimers` alongside `_pollTimers`.
   - In `didChangeAppLifecycleState` pause branch: also cancel `_drainTimers`; resume
     branch: re-arm the drain timer beside the poll timer for each `listId`.
   - Rename `_fanOutPending` → public `@visibleForTesting Future<void> fanOutPending()`,
     body `await Future.wait([for (final id in await _store.listIdsWithOutbox())
     requestDrain(id)]);`. Update `start()` to `await fanOutPending();` and the resume
     handler to `unawaited(fanOutPending());`.

   In `shopping_list_setup.dart`:
   - Add `import 'package:recipai_mobile/core/scheduler.dart';` and `Scheduler? scheduler`
     to `setupShoppingList`'s params.
   - Pass `scheduler: scheduler ?? RealScheduler()` into the `ShoppingListSyncService(...)`.
   - Change `getIt<ShoppingListSyncService>().start();` → `unawaited(getIt<...>().start());`
     (add `import 'dart:async';`).
   - Files: `mobile/lib/features/shopping_list/shopping_list_sync_service.dart`,
     `mobile/lib/features/shopping_list/shopping_list_setup.dart`
   - Verify: `cd mobile && dart analyze lib/features/shopping_list/` — no issues; the
     `PushResult` promotion and retyped maps resolve cleanly.

3. **Keep production call sites fire-and-forget** — wrap the now-`Future`-returning
   `requestDrain` calls in `unawaited(...)`.
   - `shopping_list_detail_service.dart` lines 75, 104, and inside `_requestDrainForOpenList`
     (line 184) — wrap each; add `import 'dart:async';` if not present.
   - `shopping_list_item_import_service.dart` line 30 — wrap; add `import 'dart:async';`.
   - Files: `mobile/lib/features/shopping_list/shopping_list_detail_service.dart`,
     `mobile/lib/features/shopping_list/shopping_list_item_import_service.dart`
   - Verify: `cd mobile && dart analyze lib/features/shopping_list/` — clean, no
     `unawaited_futures`/discarded-future diagnostics.

4. **Whole-project analyze, format, and smoke** — confirm nothing else references the
   renamed/retyped surface.
   - Files: none (verification only).
   - Verify: `cd mobile && dart format --set-exit-if-changed lib/ && dart analyze` both pass;
     then the manual smoke under Test plan.

## Test plan

**Unit tests**
- _N/A — T1 ships no test doubles or tests; the suite lands in T2/T3 (tasks.md Out of scope)._

**Integration tests**
- _N/A — backend untouched._

**Flutter widget/integration tests**
- _N/A — no widget behaviour changes; the drivable seams are exercised by T2's unit suite._

**Manual verification**
- Build and run the app on a device/emulator. On a shopping list: add, edit, check, and
  delete an item; confirm each converges with the backend and the per-list sync indicator
  behaves exactly as before (syncing → notSyncing; failure banner + retry still work).
- Background then foreground the app; confirm the resume fan-out still flushes pending
  edits and polling/draining resumes on the open list (~10s cadence unchanged).
- Go offline, make an edit (open list *and* a closed list), come back online; confirm both
  flush — the open list via its new drain timer, the closed list via the offline timer.

## Verification checklist

- [ ] `dart format --set-exit-if-changed lib/` passes.
- [ ] `dart analyze` passes with no new warnings (esp. no discarded-future diagnostics).
- [ ] Existing tests still pass: `flutter test`.
- [ ] `tasks.md` > T1 "How to verify" succeeds: manual add/edit/check/delete + background/
      foreground converges.
- [ ] Behaviour equivalence held: draining still async (all production sites `unawaited`),
      per-entry lock acquire/release preserved, retry/offline timing unchanged.
- [ ] Task-design "Assumptions to verify" resolved (see Risks below for the reconfirmed ones).

## Risks surfaced during planning

- **Risk:** The task-design says `ShoppingListDetailService` has **four** `requestDrain(...)`
  call sites; the code has **three** (lines 75, 104, and inside `_requestDrainForOpenList`
  at 184 — the six other mutators route through that one helper).
  **Why it matters:** A reader trusting the count might hunt for a fourth site or wrap a
  helper call by mistake.
  **Mitigation:** Wrap exactly the three direct `_syncService.requestDrain(...)` sites; the
  helper `_requestDrainForOpenList` is wrapped once at its single internal call (line 184).

- **Risk:** `Scheduler`'s callback is `void Function()` while `dart:async`
  `Timer.periodic` passes a `Timer` argument, so `RealScheduler` must adapt the signature.
  **Why it matters:** A direct `Timer.periodic(d, callback)` won't type-check.
  **Mitigation:** In `RealScheduler.periodic`, wrap: `Timer.periodic(d, (_) => callback())`.

- **Risk:** `Scheduler` in `core/` importing from a feature would invert the dependency
  direction, but the interface needs no feature types.
  **Why it matters:** A stray import would couple `core/` to the shopping-list feature.
  **Mitigation:** Keep `scheduler.dart` dependent only on `dart:async`; the sync service
  imports it via a relative `../../core/scheduler.dart` path.

Reconfirmed from task-design (all held, no change needed): `synchronized`'s `Lock()` is
non-reentrant, so moving the lock into `pushNextEntry`/`fetchAndReconcile` and calling them
from the unlocked drain loop/poll is deadlock-free; `main.dart:43` (`setupShoppingList`) is
the sole `start()` caller, so `unawaited(start())` fully preserves non-blocking startup;
`_poll`'s `requestDrain` (line 184, now removed) was the only poll-driven drain kick; and
the three retyped timer maps are assigned only within the sync-service file.
