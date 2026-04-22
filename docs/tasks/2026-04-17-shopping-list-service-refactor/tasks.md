# Shopping List Service Refactor — Tasks

**Date:** 2026-04-18
**Status:** final

## Summary

- **T1:** Refactor `ShoppingListSyncService` to stream-based API
- **T2:** Migrate `ShoppingListDetailService` to own timer and event subscription

## Cross-task notes

T2 strictly depends on T1. Both tasks touch the same feature module; they cannot be parallelised.

T1 introduces no-op stubs for `startSyncing` / `stopSyncing` / `pauseSyncing` / `resumeSyncing` on the sync service so the app continues to compile while the detail service still calls those methods. These stubs are removed in T2.

All other integration files (`shopping_list_operation.dart`, `shopping_list_repository.dart`) are untouched throughout.

---

## T1: Refactor `ShoppingListSyncService` to stream-based API

**User-visible outcome**
A developer (or QA) can exercise the full shopping list feature — add, edit, check, delete items; offline queueing; conflict snackbar; app backgrounding and foregrounding — and observe behavior identical to before the refactor, with no callback coupling between the two services.

**Scope**

- Add fields `_periodicFetchTimer`, `_syncEventsSubscription`, `_currentSyncingListId`, `_onConflictCallback`, `_onErrorCallback` to `ShoppingListDetailService`

A developer can build and run the app without errors and confirm that all shopping list operations (add, edit, check, delete, offline queueing) behave identically to before.

**Scope**

- Add `SyncEvent` sealed class (`ItemSynced`, `SyncConflict`, `SyncFailed`) alongside the service in `shopping_list_sync_service.dart` — per `design.md` > Module & component boundaries
- Add `Map<String, StreamController<SyncEvent>>` and implement `events(listId)` — per `design.md` > Interface contracts > `ShoppingListSyncService`
- Add `pendingOperations(listId)` returning an unmodifiable copy of the queue — per `design.md` > Interface contracts > `ShoppingListSyncService`
- Rename `getSyncStatusNotifier` → `syncStatus` on the sync service
- Replace the six `callbacks?.on*` call sites inside `_processQueue` with `StreamController.add` emissions — per `design.md` > Flows & state > `queueOperation`
- Delete `_SyncCallbacks` class and the `_syncCallbacks` map
- Delete the `_syncTimers` map and `_syncList` method
- Add no-op stubs for `startSyncing`, `stopSyncing`, `pauseSyncing`, `resumeSyncing` on the sync service so the app continues to compile (removed in T2)
- Add `dispose()` that closes all stream controllers and disposes all sync-status notifiers
- Update `shopping_list_setup.dart` to register the sync service with a `dispose:` callback — per `design.md` > Integration changes

**Out of scope**

- Removing the no-op stubs — covered in T2
- All changes to `ShoppingListDetailService` — covered in T2
- Screen rename (`getSyncStatusNotifier` → `syncStatus` on the detail service's delegate) — covered in T2

**Depends on:** none

**Design references**

- `design.md` > Interface contracts > `ShoppingListSyncService`
- `design.md` > Flows & state > `queueOperation`
- `design.md` > Flows & state > `Queue bookkeeping (_replaceValuesInQueue)`
- `design.md` > Flows & state > `Stream controller lifecycle`
- `design.md` > Integration changes (sync service paragraph)
- `docs/mobile/standards/state-management.md` — `dispose()` requirement
- `docs/mobile/standards/dependency-injection.md` — updating `shopping_list_setup.dart`
- `docs/ADRs/0001-sync-service-event-stream.md`

**How to verify**

1. `flutter build apk` (or `flutter run`) completes without errors.
2. Open a shopping list, add an item, go offline, add another item — both appear optimistically. Come back online — both sync without errors or visible glitches.
3. Trigger a conflict (edit the same item from two devices) — conflict snackbar still appears.

---

## T2: Migrate `ShoppingListDetailService` to own timer and event subscription

**User-visible outcome**

A developer (or QA) can exercise the full shopping list feature — add, edit, check, delete items; offline queueing; conflict snackbar; app backgrounding and foregrounding — and observe behavior identical to before the refactor, with no callback coupling between the two services.

**Scope**

- Add fields `_periodicFetchTimer`, `_syncEventsSubscription`, `_currentSyncingListId`, `_onConflictCallback`, `_onErrorCallback` to `ShoppingListDetailService`
- Rewrite `startSyncing` to subscribe to `syncService.events(listId)` and start `Timer.periodic(10 s, _onPeriodicFetch)` — per `design.md` > Flows & state > `startSyncing`
- Implement `_onPeriodicFetch` with the sync-in-progress guard — per `design.md` > Flows & state > `Periodic fetch`
- Implement `_handleSyncEvent`, `_handleItemSynced` (reconcile loop), `_handleConflict` — per `design.md` > Flows & state > `Sync event handling`
- Update `pauseSyncing` / `resumeSyncing` to operate on the local timer — per `design.md` > Flows & state > `Pause / resume`
- Implement `stopSyncing` to cancel timer and subscription — per `design.md` > Flows & state > `stopSyncing`
- Remove `_onItemAdded` / `_onItemUpdated` in their current callback-driven form
- Rename `getSyncStatusNotifier` → `syncStatus` on `ShoppingListDetailService` (delegates to sync service)
- Expand `dispose()` to cancel timer and subscription before delegating
- Remove the no-op stubs added in T1 from `ShoppingListSyncService`
- Update `shopping_list_detail_screen.dart`: call `syncStatus(detail.id)` instead of `getSyncStatusNotifier(detail.id)` — per `design.md` > Integration changes (screen paragraph)

**Out of scope**

- The skip-vs-merge race between periodic fetch and in-flight user operations — explicitly deferred in `design.md` > Out of scope
- Extracting `SyncEvent` into its own file — deferred in `design.md` > Out of scope

**Depends on:** T1

**Design references**

- `design.md` > Flows & state (all sub-sections)
- `design.md` > Interface contracts > `ShoppingListDetailService`
- `design.md` > Integration changes (detail service and screen paragraphs)
- `docs/mobile/standards/state-management.md` — `ValueNotifier<AsyncValue<T>>`, `guardAsync`, boolean re-entry flags, `dispose()` requirement
- `docs/mobile/standards/architecture.md` — service-to-service access via public API only
- `docs/mobile/modules/shopping_list/codebase_structure.md` — feature layout
- `docs/ADRs/0001-sync-service-event-stream.md`

**How to verify**

1. `flutter build apk` completes without errors and no references to `getSyncStatusNotifier` remain in the codebase.
2. Open a shopping list — items load correctly.
3. Add, edit, check, and delete items while online — all changes persist after a full app restart.
4. Go offline, perform several operations, return online — all queue up and sync without errors or UI glitches.
5. Background the app mid-sync, foreground it — periodic fetch resumes and the list is up to date.
6. Trigger a conflict (concurrent edit from a second device) — conflict snackbar appears and the list refreshes with server state.
7. `grep -r 'getSyncStatusNotifier\|_SyncCallbacks\|startSyncing\|stopSyncing\|pauseSyncing\|resumeSyncing' mobile/lib/features/shopping_list/shopping_list_sync_service.dart` returns no matches.
