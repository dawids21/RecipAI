# T1: Refactor `ShoppingListSyncService` to stream-based API

## Context

Task T1 of `docs/tasks/2026-04-17-shopping-list-service-refactor/` begins a two-step refactor of the shopping list feature. Today `ShoppingListSyncService` and `ShoppingListDetailService` are coupled through a `_SyncCallbacks` bundle: the sync service owns a periodic timer, fetches full lists, and invokes callbacks back into the detail service. Per `design.md` and ADR-0001, the seam moves to a per-list broadcast `Stream<SyncEvent>`; sync service shrinks to queue processing, and the detail service will (in T2) own the timer and reconciliation.

T1 lands the **sync-service half** only. It introduces `SyncEvent`, the stream, `pendingOperations`, `syncStatus`, `dispose()`, and replaces the six callback call sites inside `_processQueue` with `StreamController.add` emissions. To keep the app compiling until T2 rewrites the detail service, the old lifecycle methods (`startSyncing`/`stopSyncing`/`pauseSyncing`/`resumeSyncing`) are replaced with no-op stubs. No behavior changes in this task — the detail service's existing callback-driven reconciliation continues to run because `_syncCallbacks` remains populated via… wait: the stubs are no-ops, so callbacks won't be registered. See "Behavior during T1" below.

## Behavior during T1 (important)

Between T1 landing and T2 landing, the detail service still calls the no-op `startSyncing` stub with its existing callback arguments. Because the stub does nothing:

- No periodic fetch runs (no timer scheduled anywhere).
- `_syncCallbacks[listId]` is never populated, so the existing `callbacks?.on*` sites inside `_processQueue` — which are being **replaced** by `StreamController.add` in T1 — no longer exist; events go to the stream, which currently has no subscriber.
- Optimistic updates from `processOperation` still work (that path does not go through sync-service callbacks).
- Conflict snackbar and `onError` no longer fire until T2 wires the subscription.

This matches the tasks.md note that T1 and T2 together form one behavioral change; the stubs exist only so the app compiles. The "how to verify" step for T1 in `tasks.md` is `flutter build apk` completing — not a full end-to-end behavior check, which is T2's verification scope.

## Files to modify

- `mobile/lib/features/shopping_list/shopping_list_sync_service.dart` — main refactor.
- `mobile/lib/features/shopping_list/shopping_list_setup.dart` — add `dispose:` callback to the `ShoppingListSyncService` registration.

Untouched in T1: `shopping_list_detail_service.dart`, `shopping_list_detail_screen.dart`, `shopping_list_operation.dart`, `shopping_list_repository.dart`.

## Implementation

### 1. `shopping_list_sync_service.dart`

**Add at top of file** (alongside existing imports, keep `shopping_list_item.dart`, drop `shopping_list_detail.dart` since `onSync` is gone):

```dart
sealed class SyncEvent {}

final class ItemSynced extends SyncEvent {
  final String submittedItemId;
  final ShoppingListItem serverItem;
  ItemSynced(this.submittedItemId, this.serverItem);
}

final class SyncConflict extends SyncEvent {}

final class SyncFailed extends SyncEvent {
  final String message;
  SyncFailed(this.message);
}
```

**Delete**: `_SyncCallbacks` class, `_syncCallbacks` map, `_syncTimers` map, `_syncList` method.

**Rename `getSyncStatusNotifier` → `syncStatus`**, return type `ValueListenable<bool>`:

```dart
ValueListenable<bool> syncStatus(String listId) =>
    _syncStatusNotifiers.putIfAbsent(listId, () => ValueNotifier(false));
```

Keep an internal helper for the mutable side (used by `_updateSyncStatus`), or inline the `putIfAbsent` there — simplest: have `_updateSyncStatus` use the same `putIfAbsent` pattern on `_syncStatusNotifiers` directly.

**Add stream controllers map + `events(listId)`**:

```dart
final Map<String, StreamController<SyncEvent>> _eventControllers = {};

Stream<SyncEvent> events(String listId) => _eventControllers
    .putIfAbsent(listId, () => StreamController<SyncEvent>.broadcast())
    .stream;
```

Use `.broadcast()` — multiple subscribers are not required today but the design/ADR specifies broadcast and it matches the per-list lifecycle (stays open across subscribe/unsubscribe cycles).

**Add `pendingOperations(listId)`** returning an unmodifiable snapshot:

```dart
List<ShoppingListOperation> pendingOperations(String listId) =>
    List.unmodifiable(_operationQueues[listId] ?? const []);
```

**Replace the six callback call sites in `_processQueue`** with stream emissions:

- After each successful `Add`/`Move`/`Check`/`Uncheck`/`Update` repository call and `_replaceValuesInQueue`, replace `callbacks?.onItemAdded.call(...)` / `callbacks?.onItemUpdated.call(...)` with:

  ```dart
  _emit(listId, ItemSynced(add.itemId, response)); // add uses temp id
  _emit(listId, ItemSynced(move.itemId, response)); // update ops use server id
  // …same shape for check/uncheck/update
  ```

  Note `move.itemId` (etc.) here equals `response.id` already — per the design, `submittedItemId == serverItem.id` for updates. For adds it's the temp id (pre-rewrite). The `pendingOperations` list argument that used to be computed inline disappears entirely — detail service will pull it via `pendingOperations(listId)` when it handles the event in T2.

- `ShoppingListItemApiConflictException` catch — replace the existing `await callbacks?.onConflict.call()` with:

  ```dart
  _emit(listId, SyncConflict());
  ```

  Also implement the design's **drop-behind-412** policy (currently only drops the head op): after removing the head, drop all subsequent queued ops with the same `itemId`. The failing op's `itemId` must be captured before `removeAt(0)`. Confirm in design.md line 241–243: "On 412, drop the current op and all subsequent ops with the same `itemId`, then emit `SyncConflict`." Today's code only drops the head — this is one existing behavior change T1 introduces. **Flag to user to confirm** before implementing.

- `ShoppingListItemApiException` catch — replace `callbacks?.onError.call(...)` with:

  ```dart
  _emit(listId, SyncFailed('Failed to process operation: ${e.message}'));
  ```

- Other exceptions (connection) — unchanged: 3-second delay, retry (op retained).

Helper:

```dart
void _emit(String listId, SyncEvent event) {
  final controller = _eventControllers.putIfAbsent(
    listId,
    () => StreamController<SyncEvent>.broadcast(),
  );
  controller.add(event);
}
```

**Add no-op stubs** so T1 compiles with detail service's existing call sites:

```dart
void startSyncing({
  required String listId,
  required Function(String, ShoppingListItem, List<ShoppingListOperation>) onItemAdded,
  required Function(String, ShoppingListItem, List<ShoppingListOperation>) onItemUpdated,
  required Function(ShoppingListDetail) onSync,
  required VoidCallback onConflict,
  required ValueChanged<String> onError,
}) {}

void stopSyncing(String listId) {}
void pauseSyncing(String listId) {}
void resumeSyncing(String listId) {}
```

Signatures must match the existing call sites in `shopping_list_detail_service.dart:98-115,120,127,133` exactly, so the file still compiles. `onSync` still takes `ShoppingListDetail`, so the `shopping_list_detail.dart` import stays for this stub; it is removed in T2 along with the stubs themselves.

**Add `dispose()`**:

```dart
void dispose() {
  for (final controller in _eventControllers.values) {
    controller.close();
  }
  _eventControllers.clear();
  for (final notifier in _syncStatusNotifiers.values) {
    notifier.dispose();
  }
  _syncStatusNotifiers.clear();
}
```

### 2. `shopping_list_setup.dart`

Change the `ShoppingListSyncService` registration from `registerSingleton` to `registerSingleton` **with a `dispose:` callback**. `registerSingleton` supports `dispose:` — verify, and if not, switch to `registerLazySingleton` (per the DI standard, `dispose:` hooks fire on `resetLazySingleton`/`reset`). The sync service is global (singleton lifetime), so `registerSingleton(... dispose: (s) => s.dispose())` is correct if supported; otherwise keep it as a singleton and rely on process exit (acceptable for app-long state). Design.md line 336–339 says "register the sync service with a `dispose:` callback" — confirm by checking other `registerSingleton` call sites in the codebase for the dispose pattern.

Concretely:

```dart
getIt.registerSingleton<ShoppingListSyncService>(
  ShoppingListSyncService(
    repository: getIt<ShoppingListRepository>(),
    authService: getIt<AuthService>(),
  ),
  dispose: (service) => service.dispose(),
);
```

## Resolved decisions

1. **Drop-behind-412**: implemented in T1. In the `ShoppingListItemApiConflictException` branch, capture `operation.itemId` before removing the head, then `removeWhere((op) => op.itemId == failedItemId)` on the queue, then emit `SyncConflict`.
2. **DI registration**: `registerSingleton<ShoppingListSyncService>(..., dispose: (s) => s.dispose())` in `shopping_list_setup.dart`.

## Verification

Per `tasks.md` T1 verification:

1. `cd mobile && flutter analyze` — no errors.
2. `cd mobile && flutter build apk` (debug profile is sufficient) — builds successfully.
3. Spot-check that `_SyncCallbacks`, `_syncCallbacks`, `_syncTimers`, and `_syncList` are gone from `shopping_list_sync_service.dart` (grep).
4. Spot-check that `events`, `syncStatus`, `pendingOperations`, `dispose`, and the three `SyncEvent` subtypes exist in `shopping_list_sync_service.dart`.
5. Full end-to-end behavior verification (add/edit/check/delete, offline queue, conflict snackbar, background/foreground) is **T2's verification scope**, not T1's — the stubs intentionally neutralize periodic fetch and event delivery until T2 wires the subscription. Document this in the PR body.
