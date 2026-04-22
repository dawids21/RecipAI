# Shopping List Service Refactor — Design

**Date:** 2026-04-18
**Status:** final
**ADRs:** [docs/ADRs/0001-sync-service-event-stream.md](../../ADRs/0001-sync-service-event-stream.md)

## Overview

Split `ShoppingListSyncService` and `ShoppingListDetailService` along a
single clean seam: sync service owns the operation queue and emits a
`Stream<SyncEvent>` describing what happened; detail service owns all
`ShoppingListDetail` state, the 10 s periodic-fetch timer, app-lifecycle
pause/resume, and reconciliation of sync events into its
`ValueNotifier<AsyncValue<ShoppingListDetail>>`. The callback bundle
disappears.

## Required reading for implementation

- `docs/mobile/standards/architecture.md` — three-layer rules that both
  services must continue to follow (service-to-service access via public
  API only, views talk only to services).
- `docs/mobile/standards/state-management.md` — the
  `ValueNotifier<AsyncValue<T>>` + `guardAsync` pattern, boolean re-entry
  flags, and the `dispose()` requirement that both services must satisfy.
- `docs/mobile/standards/dependency-injection.md` — rules for updating
  `shopping_list_setup.dart` when service constructors change.
- `docs/mobile/modules/shopping_list/codebase_structure.md` and `ui.md` —
  feature layout and the flows the refactor must preserve.

## Approach

The refactor is structural only. No user-visible behaviour changes and no
changes to the operation model, the `applyOperation` reducer, or the
repository layer.

- **`ShoppingListSyncService`** shrinks to queue processing. It no longer
  holds callbacks, no longer schedules a periodic timer, and no longer
  fetches the full list. Its outward surface is: `queueOperation`,
  `syncStatus(listId)`, `pendingOperations(listId)` (snapshot), and
  `events(listId)` (broadcast `Stream<SyncEvent>`). Retry on connection
  error and drop-behind-412 stay put.
- **`ShoppingListDetailService`** grows to own the periodic fetch loop
  (`Timer.periodic`, 10 s), pause/resume, and reconciliation of sync
  events. It subscribes to `syncService.events(listId)` when the screen
  starts syncing and cancels on dispose. `startSyncing` keeps its current
  signature from the screen's perspective.
- **Communication** is the broadcast `Stream<SyncEvent>` specified in
  [ADR-0001](../../ADRs/0001-sync-service-event-stream.md). Detail service
  reconciles by reading the current `pendingOperations(listId)` snapshot at
  the moment an `ItemSynced` event arrives, so sync service no longer
  embeds pending-op lists inside callback arguments.
- **Screen** continues to call only into detail service; no changes to its
  public method names or parameters.

## Module & component boundaries

No new files or classes beyond a small `SyncEvent` sealed type. Location:

- `mobile/lib/features/shopping_list/shopping_list_sync_service.dart` —
  house the `SyncEvent` sealed class alongside the service (same file, as
  it is an intrinsic part of the service's public API). The private
  `_SyncCallbacks` class is deleted.
- `mobile/lib/features/shopping_list/shopping_list_detail_service.dart` —
  absorbs timer, pause/resume, and event-stream subscription.

Responsibilities after refactor:

| Concern                                    | Owner                       |
|--------------------------------------------|-----------------------------|
| Operation queue (enqueue, process, retry)  | `ShoppingListSyncService`   |
| Drop-behind-412 policy                     | `ShoppingListSyncService`   |
| Sync-status `ValueNotifier<bool>`          | `ShoppingListSyncService`   |
| Periodic fetch timer + app-lifecycle hooks | `ShoppingListDetailService` |
| `AsyncValue<ShoppingListDetail>` state     | `ShoppingListDetailService` |
| Optimistic updates (`applyOperation`)      | `ShoppingListDetailService` |
| Conflict re-fetch                          | `ShoppingListDetailService` |
| Rename, delete, sharing, shared users      | `ShoppingListDetailService` |

## Data model changes

_No data model changes._

## Interface contracts

### `SyncEvent` (new, sealed)

```dart
sealed class SyncEvent {}

final class ItemSynced extends SyncEvent {
  final String submittedItemId; // temp id for adds, server id for updates
  final ShoppingListItem serverItem;
}

final class SyncConflict extends SyncEvent {}

final class SyncFailed extends SyncEvent {
  final String message;
}
```

`Delete` success emits no event (matches current behaviour — no callback
fires on delete today).

### `ShoppingListSyncService` (refactored)

```dart
class ShoppingListSyncService {
  ShoppingListSyncService({
    required ShoppingListRepository repository,
    required AuthService authService,
  });

  void queueOperation(String listId, ShoppingListOperation operation);

  ValueListenable<bool> syncStatus(String listId);

  List<ShoppingListOperation> pendingOperations(String listId);

  Stream<SyncEvent> events(String listId); // broadcast, per-list

  void dispose();
}
```

Gone: `startSyncing`, `stopSyncing`, `pauseSyncing`, `resumeSyncing`,
`getSyncStatusNotifier` (renamed to `syncStatus`), `_SyncCallbacks`, the
periodic timer map, and the `_syncList` method.

### `ShoppingListDetailService` (refactored)

```dart
class ShoppingListDetailService {
  ShoppingListDetailService({
    required ShoppingListRepository shoppingListRepository,
    required AuthService authService,
    required ShoppingListListService shoppingListListService,
    required ShoppingListSyncService syncService,
  });

  ValueListenable<AsyncValue<ShoppingListDetail>> get shoppingListDetail;
  ValueListenable<AsyncValue<List<SharedUser>>> get sharedUsers;

  Future<void> loadShoppingListDetail(String id);
  Future<void> renameShoppingList(String id, String newName);
  Future<void> deleteShoppingList(String id);
  Future<void> loadSharedUsers(String id);
  Future<void> shareShoppingList(String email);
  Future<void> unshareShoppingList(String email);

  void startSyncing({
    required String listId,
    VoidCallback? onConflict,
    ValueChanged<String>? onError,
  });
  void stopSyncing();
  void pauseSyncing();
  void resumeSyncing();

  ValueListenable<bool> syncStatus(String listId); // delegates to sync service

  void processOperation(ShoppingListOperation operation);
  void deleteAllCheckedItems();
  void uncheckAllItems();

  void dispose();
}
```

The screen continues to see the same method set it uses today. The rename
`getSyncStatusNotifier` → `syncStatus` matches the sync service's name and
is the single externally visible change the screen will observe.

## Flows & state

### startSyncing (detail service)

1. Record `listId`.
2. Subscribe to `syncService.events(listId)`; store the subscription.
3. Start a `Timer.periodic(Duration(seconds: 10), _onPeriodicFetch)`.
4. Store `onConflict` / `onError` callbacks from the screen.

### Periodic fetch (detail service, _onPeriodicFetch)

1. If `syncService.syncStatus(listId).value == true` OR
   `syncService.pendingOperations(listId).isNotEmpty`, skip this tick —
   the queue is authoritative until it drains.
2. Otherwise, fetch full detail from repository. On success, replace
   `_shoppingListDetail.value` with `AsyncData(detail)`. On failure, swallow
   (matches current silent-fail behaviour of `_syncList`).

### Sync event handling (detail service)

Handle each event on the subscription:

- **`ItemSynced(submittedItemId, serverItem)`** —
  1. If current state is not `AsyncData`, ignore.
  2. If an item with `id == submittedItemId` exists in the current detail's
     `items` list, replace it in place with `serverItem` (adds: temp id →
     server id; updates: version bump). **If no such item exists, do
     nothing** — a later optimistic op (typically a delete) has already
     removed it, and that absence is the authoritative state. Never
     fall back to inserting `serverItem`.
  3. Read `syncService.pendingOperations(listId)` and re-apply each
     operation whose `itemId == serverItem.id` via `applyOperation` to
     restore subsequent optimistic state. Because `applyOperation` for
     delete/update/check/uncheck/move is idempotent against a missing
     item, this step is safe when the item was concurrently deleted.
     This mirrors the current `_onItemAdded` / `_onItemUpdated` reconcile
     loop.
  4. Emit the new `AsyncData(updatedDetail)`.

  Example — user edits then immediately deletes item X. Queue is
  `[Update X, Delete X]`; state has already applied both optimistically,
  so X is gone. When `Update X` syncs, the replace step finds nothing
  and is a no-op; re-applying the pending `Delete X` is also a no-op
  filter; state stays correct. Sync service then processes the queued
  `Delete X` against the server and converges.
- **`SyncConflict`** — re-fetch the full list with `guardAsync`, replace
  state, invoke `onConflict` callback.
- **`SyncFailed(message)`** — invoke `onError(message)`.

### Pause / resume (detail service)

- `pauseSyncing()` cancels the periodic timer. Queue processing inside
  sync service is untouched.
- `resumeSyncing()` restarts the timer and triggers an immediate
  `_onPeriodicFetch` to catch up.

### stopSyncing (detail service)

Cancel the timer, cancel the stream subscription, clear the stored
`listId` and callbacks.

### queueOperation (sync service)

Identical to today's logic. When an operation completes successfully,
emit `ItemSynced` on the per-list `StreamController` (except for deletes,
which emit nothing — matches current behaviour where no callback fires on
delete). On 412, drop the current op and all subsequent ops with the same
`itemId`, then emit `SyncConflict`. On other API errors, drop the op and
emit `SyncFailed`. On other exceptions (connection), retain the op and
retry after 3 s.

### Queue bookkeeping (sync service, `_replaceValuesInQueue`)

Preserved from today, unchanged in responsibility. Inside `_processQueue`,
immediately after a successful repository call and **before** removing the
head op from the queue:

- Iterate remaining queued ops for that `listId`.
- For every op whose `itemId` matches the just-processed op's `itemId`,
  rewrite it with the server `item.id` (matters for adds: temp id → real
  id) and server `item.version` (matters for update/check/uncheck/move:
  stale version → fresh version).

Delete is excluded from this rewrite: after a successful delete, no
further op for that item can succeed on the server — the drop-behind-412
rule will clean them up if they get there.

Detail service never participates in this rewrite. It only reads
`pendingOperations(listId)` when reconciling an `ItemSynced` event, and
by the time it reads, the queue already reflects the latest server
id/version — so re-applying those pending ops on top of `serverItem` is
consistent.

### Delete flow (end-to-end)

1. User taps delete. Screen calls
   `detail.processOperation(DeleteItemOperation(itemId, version))`.
2. Detail service applies the optimistic update — the item is removed
   from state immediately.
3. Detail service calls `sync.queueOperation(listId, op)`.
4. Sync service appends and runs `_processQueue`, calling
   `repository.deleteItem(listId, itemId, version, token)`.
5. Outcomes:
   - Success: op removed from queue head; **no `SyncEvent` emitted**;
     state already reflects the deletion.
   - 412: op + all subsequent queued ops for the same `itemId` dropped;
     `SyncConflict` emitted; detail service re-fetches and shows the
     conflict snackbar.
   - Other API error: op dropped; `SyncFailed(message)` emitted; detail
     service invokes `onError`. The optimistic deletion stays until the
     next periodic fetch (or conflict re-fetch) reconciles it — same as
     today.
   - Connection error: op retained; retry after 3 s.

### Stream controller lifecycle (sync service)

- `events(listId)` lazily creates a broadcast `StreamController<SyncEvent>`
  on first call and returns its `stream`.
- When the controller has no listeners and the queue for that list is
  empty, the controller can stay (cheap) — close it in `dispose()`.
- `dispose()` closes every controller and every sync-status notifier.

## Integration changes

- **`mobile/lib/features/shopping_list/shopping_list_sync_service.dart`** —
  Delete `_SyncCallbacks`, `_syncCallbacks`, `_syncTimers`, `startSyncing`,
  `stopSyncing`, `pauseSyncing`, `resumeSyncing`, and `_syncList`. Rename
  `getSyncStatusNotifier` to `syncStatus`. Add a
  `Map<String, StreamController<SyncEvent>>` and the `events(listId)`
  accessor. Replace the six `callbacks?.onItemAdded` / `onItemUpdated` /
  `onConflict` / `onError` call sites inside `_processQueue` with
  `StreamController.add` calls emitting the appropriate `SyncEvent`. Add
  a `pendingOperations(listId)` accessor that returns an unmodifiable copy
  of `_operationQueues[listId]`. Add `dispose()` that disposes all
  notifiers and closes all controllers.

- **`mobile/lib/features/shopping_list/shopping_list_detail_service.dart`** —
  Add fields for `Timer? _periodicFetchTimer`, `StreamSubscription<SyncEvent>?
  _syncEventsSubscription`, `String? _currentSyncingListId`,
  `VoidCallback? _onConflictCallback`, `ValueChanged<String>? _onErrorCallback`.
  Rewrite `startSyncing` to start the timer and subscribe to the stream
  (no callbacks passed to sync service). Add `_onPeriodicFetch`,
  `_handleSyncEvent`, `_handleItemSynced`, `_handleConflict`. Remove
  `_onItemAdded` / `_onItemUpdated` in their current form — their logic
  moves into `_handleItemSynced` but driven by `pendingOperations(listId)`
  rather than a list passed via callback. Update `pauseSyncing` /
  `resumeSyncing` to operate on the local timer. Rename
  `getSyncStatusNotifier` to `syncStatus` and delegate. Expand `dispose()`
  to cancel the timer and the subscription in addition to calling
  `stopSyncing`.

- **`mobile/lib/features/shopping_list/shopping_list_detail_screen.dart`** —
  One name change only: call `syncStatus(detail.id)` instead of
  `getSyncStatusNotifier(detail.id)` inside the `ValueListenableBuilder` at
  the top of the body. No other changes.

- **`mobile/lib/features/shopping_list/shopping_list_operation.dart`** —
  _No changes._

- **`mobile/lib/features/shopping_list/shopping_list_repository.dart`** —
  _No changes._

- **`mobile/lib/features/shopping_list/shopping_list_setup.dart`** —
  Register the sync service with a `dispose:` callback (since it now owns
  stream controllers and notifiers that must be closed). Constructor
  signatures are unchanged, so wiring is otherwise identical.

## Resolved questions

- **Q:** What communication mechanism should sync service use to signal
  operation results, conflicts, and errors back to detail service?
  **A:** A per-list broadcast `Stream<SyncEvent>` exposed by sync service
  and consumed by detail service. See
  [ADR-0001](../../ADRs/0001-sync-service-event-stream.md).

## Assumptions to verify

_No outstanding assumptions._

## Out of scope (design-level)

- The skip-vs-merge race between periodic fetch and in-flight user
  operations (see assumption above) — the refactor preserves current
  behaviour; improving it is a separate task.
- Extracting `SyncEvent` into its own file. Keeping it beside the service
  it belongs to is the simpler choice; split it out only if a second
  consumer appears.
