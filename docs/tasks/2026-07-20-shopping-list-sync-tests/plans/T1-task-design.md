# T1: Add sync-service test seams — Task Design

**Date:** 2026-07-22
**Status:** draft

## Summary

Refactor `ShoppingListSyncService` in place to add three test seams — an injected
`Scheduler` for all timer creation, poll/drain decoupling with awaitable
single-step `pushNextEntry` / `fetchAndReconcile` entry points, and an awaitable
`requestDrain` / `fanOutPending` — while keeping production behaviour equivalent
(async draining, per-entry lock acquire/release, retry/offline timing unchanged).
No test doubles or tests land here (T2); this ships the drivable production shape.

## Components and responsibilities

- **`Scheduler` / `ScheduledTimer`** (CREATE, `mobile/lib/core/scheduler.dart`) —
  `Scheduler` is the abstraction that owns timer creation: `periodic` and
  `oneShot`, each returning a `ScheduledTimer` handle whose only capability is
  `cancel()`. `RealScheduler` implements it over `dart:async` `Timer`. The inert
  scheduler is **not** built here — it is a T2 test double implementing this
  interface.
- **`ShoppingListSyncService`** (MODIFY, `.../shopping_list_sync_service.dart`) —
  takes the `Scheduler` as a constructor dependency; routes every timer through
  it; decouples poll from drain and adds a per-list drain timer; exposes the two
  `@visibleForTesting` step methods and a public `PushResult`; makes
  `requestDrain` and `fanOutPending` return awaitable futures. Concurrency
  contract (per-list sync lock, single-flight `_draining`/`_pending`) unchanged.
- **`setupShoppingList`** (MODIFY, `.../shopping_list_setup.dart`) — gains a
  nullable `Scheduler? scheduler` param defaulting to `RealScheduler()`, passed
  into the service constructor (per the DI standard's "external dependency as
  setup-function parameter" rule); wraps its `start()` call in `unawaited()`, as
  `start()` now awaits the fan-out internally (see below).
- **`ShoppingListDetailService`** (MODIFY, `.../shopping_list_detail_service.dart`) —
  its four `requestDrain(...)` call sites wrap the now-returned future in
  `unawaited()` to stay fire-and-forget.
- **`ShoppingListItemImportService`** (MODIFY, `.../shopping_list_item_import_service.dart`) —
  its single `requestDrain(...)` call site wrapped in `unawaited()`.

## Interfaces and method signatures

New scheduler seam:

```
abstract interface class Scheduler {
  ScheduledTimer periodic(Duration duration, void Function() callback);
  ScheduledTimer oneShot(Duration duration, void Function() callback);
}

abstract interface class ScheduledTimer {
  void cancel();
}

class RealScheduler implements Scheduler { /* wraps Timer.periodic / Timer */ }
```

Changed sync-service surface:

```
// Construction
ShoppingListSyncService({
  required ShoppingListItemRepository itemRepository,
  required ShoppingListItemStoreService store,
  required AuthService authService,
  required Scheduler scheduler,          // NEW
});

// Test-visible single steps (lock acquired INSIDE each)
@visibleForTesting
Future<PushResult> pushNextEntry(String listId);        // one head outbox entry
@visibleForTesting
Future<List<ShoppingListItem>> fetchAndReconcile(String listId);  // pure pull

enum PushResult { empty, pushed, stalled }               // was private _PushResult

// Awaitable draining
Future<void> requestDrain(String listId);                // was void
@visibleForTesting
Future<void> fanOutPending();                            // was private _fanOutPending
Future<void> start();                                    // unchanged: still awaits fanOutPending() internally

// Timer maps retyped Timer -> ScheduledTimer; NEW _drainTimers
final _drainTimers = <String, ScheduledTimer>{};
```

`_pushHeadEntry` keeps its body but returns the now-public `PushResult`;
`_drainPass` calls `pushNextEntry` instead of acquiring the lock itself.

## Data flow

**Production drain (equivalent to today).** A store mutation or timer tick calls
`requestDrain(listId)` → `_drain` → `_drainPass` loops `pushNextEntry(listId)`,
each call acquiring the sync lock, pushing one entry, reconciling, releasing —
until `empty` (`true`) or `stalled` (`false`). Callers discard the returned
future with `unawaited()`, so it stays fire-and-forget.

**Production poll (now drain-free).** The poll timer fires `_poll(listId)` →
`fetchAndReconcile(listId)` (lock → fetch → `reconcileFromServer` → return items)
wrapped in `_poll`'s try/catch for logging and offline swallowing. `_poll` no
longer kicks a drain. The **separate per-list drain timer** (armed in
`startPolling` alongside the poll timer, same `_pollInterval`) owns the periodic
drain-kick that the poll used to trigger.

**Multi-list fan-out.** `fanOutPending` reads `listIdsWithOutbox` and collects
each list's `requestDrain` future via `Future.wait`, so its own future resolves
at quiescence. `start()` **awaits** `fanOutPending()` internally, so the
fire-and-forget boundary sits at `start()`'s caller: `setupShoppingList` invokes
`unawaited(start())`. The resume handler calls `unawaited(fanOutPending())`
directly. A T3 multi-list test awaits `fanOutPending()` directly to drive the
fan-out to quiescence.

**Timer lifecycle.** `startPolling` arms poll + drain timers via the scheduler;
`stopPolling` and `dispose` cancel both; `didChangeAppLifecycleState` cancels
both on pause/inactive and re-arms both on resume (plus the existing fan-out).

## Pseudo-code

Single-entry push step (lock moved inside; drain loop no longer locks):

```
@visibleForTesting
pushNextEntry(listId):
    return syncLockFor(listId).synchronized(() => _pushHeadEntry(listId))

_drainPass(listId):
    while true:
        switch await pushNextEntry(listId):     # lock acquired/released each call
            empty:   return true
            stalled: return false
            pushed:  continue
```

Awaitable coalesced kick (completed future when coalesced — decision below):

```
requestDrain(listId) -> Future<void>:
    if _draining.contains(listId):
        _pending.add(listId)
        return Future.value()        # in-flight loop not awaited here
    return _drain(listId)            # caller unawaits in production

fanOutPending():
    ids = await store.listIdsWithOutbox()
    await Future.wait([requestDrain(id) for id in ids])
```

Poll decoupled from drain:

```
@visibleForTesting
fetchAndReconcile(listId) -> Future<List<ShoppingListItem>>:
    return syncLockFor(listId).synchronized(() async:
        token = await auth.idToken
        items = await repo.fetchServerItems(listId, token)
        await store.reconcileFromServer(listId, items)
        return items)

_poll(listId):
    try:    items = await fetchAndReconcile(listId); log ok
    on ShoppingListNetworkException: log offline
    catch e: log 'poll failed, store untouched'
    # NOTE: no requestDrain here anymore

startPolling(listId):
    cancel+re-arm pollTimers[listId]  = scheduler.periodic(_pollInterval, () => unawaited(_poll(listId)))
    cancel+re-arm drainTimers[listId] = scheduler.periodic(_pollInterval, () => unawaited(requestDrain(listId)))
    unawaited(_poll(listId))          # immediate cold-start load (unchanged)
```

## Decisions made

- **Scheduler returns a custom `ScheduledTimer` handle, not `dart:async` `Timer`.**
  Narrower surface for the T2 inert double to implement; the timer-map fields are
  retyped `Timer` → `ScheduledTimer` (contained, mechanical).
- **The push step returns a public `PushResult` enum.** `_PushResult` is promoted
  to public `PushResult`; `pushNextEntry` returns it so tests can assert the
  step's own verdict (empty/pushed/stalled) in addition to the four surfaces.
- **A per-list drain timer mirrors the poll timer's lifecycle.** Armed in
  `startPolling`, canceled in `stopPolling`/pause, re-armed on resume, at the same
  `_pollInterval` — reproducing today's "each open list re-drains ~every 10s"
  behaviour now that the poll no longer kicks a drain. Closed-list offline flush
  stays owned by the existing offline timer.
- **A coalesced `requestDrain` returns `Future.value()`.** No in-flight future is
  tracked; the fan-out uses distinct list IDs so it never coalesces, and the
  mid-drain single-flight guard is out of scope (ADR-0005).
- **The lock moves from `_drainPass` into `pushNextEntry` / `fetchAndReconcile`.**
  The `synchronized` `Lock` is non-reentrant, so the drain loop must hold no lock
  when calling `pushNextEntry`; per-entry acquire/release is preserved exactly.
- **New discarded-future call sites use `unawaited()`.** Matches this file's
  existing idiom (`flutter_lints` does not force `unawaited_futures`); keeps the
  `unawaited(start())` in setup, the resume `fanOutPending()`, `retry`, and the
  detail/import service `requestDrain` sites fire-and-forget.
- **`start()` keeps awaiting `fanOutPending()`; its caller unawaits `start()`.**
  Rather than pushing `unawaited()` inside `start()`, the fire-and-forget boundary
  moves up one level — `setupShoppingList` calls `unawaited(start())` — so `start`
  stays a straightforwardly awaitable method and only one call site opts out.
- **`Scheduler` lives in `core/`** (`mobile/lib/core/scheduler.dart`) — a
  timer-scheduling abstraction is not sync-specific and is reusable across
  features, so it belongs with the other cross-cutting `core/` utilities rather
  than inside the shopping-list feature.

## Assumptions to verify

- **Assumption:** the `synchronized` `Lock` is non-reentrant, so moving the lock
  into `pushNextEntry`/`fetchAndReconcile` and calling them from the (unlocked)
  drain loop / poll does not deadlock and preserves per-entry release.
  **If wrong:** the loop could double-acquire and deadlock; the lock would have to
  stay in `_drainPass` with the step methods taking a "lock already held" variant.
- **Assumption:** `_poll` was the *only* place a drain was kicked off the back of
  polling, so replacing it with a per-list drain timer fully covers the removed
  kick. **If wrong:** some list could stop auto-draining; another kick source
  would need re-wiring.
- **Assumption:** `setupShoppingList` is the only production caller of `start()`,
  so wrapping that one call in `unawaited(start())` fully preserves the current
  non-blocking startup. **If wrong:** another caller that `await`s `start()` would
  now block until the first fan-out drains to quiescence.
- **Assumption:** the timer-map fields (`_pollTimers`, `_backoffTimers`,
  `_offlineTimers`) are only ever assigned from `Timer`/`Timer.periodic` within
  this file, so retyping them to `ScheduledTimer` is self-contained.
  **If wrong:** an external assignment would need updating too.

## Required reading for implementation planning

- `mobile/lib/features/shopping_list/shopping_list_sync_service.dart` — the file
  being refactored; all three seams land here.
- `mobile/lib/features/shopping_list/shopping_list_setup.dart` — scheduler wiring
  as a nullable setup-function parameter.
- `mobile/lib/features/shopping_list/shopping_list_detail_service.dart` and
  `.../shopping_list_item_import_service.dart` — the `requestDrain` call sites to
  wrap in `unawaited()`.
- `docs/ADRs/0005-shopping-list-sync-test-seam.md` — the seam decisions and what
  is / isn't a coverage target.
- `HLD.md` > Feature areas > Sync-service test seams — behaviour-equivalence
  requirements this task must hold.
- `docs/mobile/standards/dependency-injection.md` — external-dependency-as-setup-
  parameter pattern for the scheduler.
