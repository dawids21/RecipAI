# T2: Migrate `ShoppingListDetailService` to own timer and event subscription — Implementation Plan

**Date:** 2026-04-22
**Status:** draft

## Context

T1 landed the sync-service half of the refactor: `ShoppingListSyncService`
now exposes `events(listId)`, `pendingOperations(listId)`, `syncStatus(listId)`,
and a `dispose()`, and emits `ItemSynced` / `SyncConflict` / `SyncFailed`
events from `_processQueue` instead of invoking the old callback bundle.
The service keeps four no-op stubs — `startSyncing`, `stopSyncing`,
`pauseSyncing`, `resumeSyncing` — purely so `ShoppingListDetailService`
keeps compiling; no periodic fetch runs, no events are consumed.

T2 moves the timer, app-lifecycle pause/resume, and event reconciliation
into `ShoppingListDetailService`; renames `getSyncStatusNotifier` →
`syncStatus` end-to-end; and deletes the stubs. After T2 the full flow is
working again.

## Required reading

**Docs & standards** (from `docs/INDEX.md`)
- `docs/mobile/standards/state-management.md` — `ValueNotifier<AsyncValue<T>>`,
  `guardAsync`, boolean re-entry flags, `dispose()` requirement.
- `docs/mobile/standards/architecture.md` — service-to-service access via
  public API only; detail service must talk to sync service through the
  new stream/snapshot surface rather than internal state.
- `docs/mobile/modules/shopping_list/codebase_structure.md` — feature
  layout (flat, no sub-folders) and naming.

**Design & ADRs**
- `design.md` > Flows & state > `startSyncing`, `Periodic fetch`, `Sync event
  handling`, `Pause / resume`, `stopSyncing` — the full contract this task
  implements.
- `design.md` > Interface contracts > `ShoppingListDetailService` — the
  public surface the screen must see unchanged.
- `design.md` > Integration changes (detail service and screen paragraphs).
- `docs/ADRs/0001-sync-service-event-stream.md` — rationale for per-list
  broadcast stream and snapshot-based reconciliation.

**Code to mirror**
- `mobile/lib/features/shopping_list/shopping_list_sync_service.dart` (as
  landed by T1) — shape of `_emit`, `events`, `pendingOperations`, and
  `dispose`; the `submittedItemId` contract (temp id for adds, server id
  for updates) is consumed here.
- Existing `_onItemAdded` / `_onItemUpdated` in
  `shopping_list_detail_service.dart` (lines 330–388) — the reconcile loop
  to port into `_handleItemSynced`, reading `pendingOperations(listId)`
  instead of the list passed by callback.
- Existing `pauseSyncing` / `resumeSyncing` delegation pattern in
  `shopping_list_detail_service.dart` (lines 125–135) — guards against
  `_currentSyncingListId == null`.

## File inventory

- **MODIFY** `mobile/lib/features/shopping_list/shopping_list_detail_service.dart`
  — absorb timer, stream subscription, pause/resume, event handlers;
  rename `getSyncStatusNotifier` → `syncStatus`; expand `dispose`.
- **MODIFY** `mobile/lib/features/shopping_list/shopping_list_sync_service.dart`
  — delete the four no-op lifecycle stubs and drop the now-unused
  `shopping_list_detail.dart` import.
- **MODIFY** `mobile/lib/features/shopping_list/shopping_list_detail_screen.dart`
  — call `syncStatus(detail.id)` instead of `getSyncStatusNotifier(detail.id)`
  on line 579.

Untouched: `shopping_list_operation.dart`, `shopping_list_repository.dart`,
`shopping_list_setup.dart`, all widgets.

## Step-by-step plan

1. **Add timer / subscription fields and rewrite `startSyncing`** on
   `ShoppingListDetailService`.
   - Fields: `Timer? _periodicFetchTimer`,
     `StreamSubscription<SyncEvent>? _syncEventsSubscription`,
     `VoidCallback? _onConflictCallback`,
     `ValueChanged<String>? _onErrorCallback`. `_currentSyncingListId`
     already exists — keep it.
   - `startSyncing({listId, onConflict, onError})` stores `listId` and the
     two callbacks, subscribes to `_syncService.events(listId)` with
     `_handleSyncEvent`, and starts `Timer.periodic(Duration(seconds: 10),
     (_) => _onPeriodicFetch())`. No longer calls `_syncService.startSyncing`.
   - Files: `mobile/lib/features/shopping_list/shopping_list_detail_service.dart`
   - Verify: `cd mobile && flutter analyze` — still errors on missing
     handler methods added in step 2, but no compile failures on the
     new field/signature work itself.

2. **Implement `_onPeriodicFetch`, `_handleSyncEvent`, `_handleItemSynced`,
   `_handleConflict`** on `ShoppingListDetailService`.
   - `_onPeriodicFetch()`: if `_currentSyncingListId == null`, return.
     Let `id = _currentSyncingListId!`. If
     `_syncService.syncStatus(id).value == true` or
     `_syncService.pendingOperations(id).isNotEmpty`, return (skip this
     tick — queue is authoritative). Otherwise
     `final token = await _authService.idToken;` then
     `try { final detail = await _shoppingListRepository.fetchShoppingListDetail(id, token); _shoppingListDetail.value = AsyncData(detail); } catch (_) { /* silent fail — matches old _syncList */ }`.
   - `_handleSyncEvent(SyncEvent event)`: switch over `event` — delegate to
     `_handleItemSynced`, `_handleConflict`, or invoke `_onErrorCallback`
     for `SyncFailed`.
   - `_handleItemSynced(ItemSynced e)`: merges both current
     `_onItemAdded` and `_onItemUpdated` bodies. Guard on
     `_shoppingListDetail.value is AsyncData`. Build `updatedItems` by
     scanning `detail.items` for `item.id == e.submittedItemId` and
     replacing with `e.serverItem`. **If no match, do nothing further**
     — design says the absence is authoritative (user already deleted
     optimistically). Still re-apply pending ops so subsequent queued
     changes remain reflected: take
     `_syncService.pendingOperations(_currentSyncingListId!)` and for
     each op whose `itemId == e.serverItem.id`, run
     `updatedDetail = applyOperation(updatedDetail, op)`. Emit
     `AsyncData(updatedDetail)`.
   - `_handleConflict()`: run the existing re-fetch body (now in
     `startSyncing`'s `onConflict` closure) under `guardAsync`, then
     `_onConflictCallback?.call()`.
   - Files: `mobile/lib/features/shopping_list/shopping_list_detail_service.dart`
   - Verify: `cd mobile && flutter analyze` — passes for the detail
     service file (stubs in sync service still present, screen still
     on old name, so global analyze still flags the screen line —
     resolved in step 4).

3. **Rewrite `stopSyncing` / `pauseSyncing` / `resumeSyncing` and rename
   `getSyncStatusNotifier` → `syncStatus`.**
   - `stopSyncing()`: cancel timer (`_periodicFetchTimer?.cancel()` +
     null out), cancel subscription (`await? _syncEventsSubscription?.cancel()`
     — synchronous void method, fire-and-forget the future or store it;
     match existing `_onItemAdded` style: just call `.cancel()`), null
     out `_currentSyncingListId`, `_onConflictCallback`, `_onErrorCallback`.
   - `pauseSyncing()`: `_periodicFetchTimer?.cancel()` then
     `_periodicFetchTimer = null`. Do **not** touch the subscription —
     queue processing inside sync service continues, and we still want
     to reconcile its events.
   - `resumeSyncing()`: only if `_currentSyncingListId != null`. Restart
     the periodic timer (same `Timer.periodic(10 s, ...)` as
     `startSyncing`) and immediately call `_onPeriodicFetch()` to catch
     up (per design — "triggers an immediate fetch").
   - Rename method `getSyncStatusNotifier(String listId)` →
     `syncStatus(String listId)`. Keep it delegating to
     `_syncService.syncStatus(listId)`.
   - Remove `_onItemAdded` and `_onItemUpdated` (their logic now lives
     in `_handleItemSynced`).
   - Files: `mobile/lib/features/shopping_list/shopping_list_detail_service.dart`
   - Verify: `cd mobile && flutter analyze` on this file — passes.

4. **Update the screen** to use the new name.
   - Line 579: `widget.shoppingListDetailService.getSyncStatusNotifier(detail.id)`
     → `widget.shoppingListDetailService.syncStatus(detail.id)`.
   - Files: `mobile/lib/features/shopping_list/shopping_list_detail_screen.dart`
   - Verify: `cd mobile && flutter analyze` — no references to
     `getSyncStatusNotifier` remain.

5. **Expand `dispose()`** on `ShoppingListDetailService`.
   - Before delegating to `stopSyncing`, also dispose
     `_shoppingListDetail` and `_sharedUsers` `ValueNotifier`s (currently
     missing entirely — the current `dispose()` only calls `stopSyncing`;
     state-management standard requires notifier disposal).
     `stopSyncing()` already cancels timer/subscription.
   - Files: `mobile/lib/features/shopping_list/shopping_list_detail_service.dart`
   - Verify: `cd mobile && flutter analyze`.

6. **Delete sync-service stubs and unused import.**
   - Remove `startSyncing`, `stopSyncing(String listId)`,
     `pauseSyncing(String listId)`, `resumeSyncing(String listId)` from
     `shopping_list_sync_service.dart`.
   - Remove `import 'shopping_list_detail.dart';` (was only referenced
     by the stub's `onSync` parameter type).
   - Files: `mobile/lib/features/shopping_list/shopping_list_sync_service.dart`
   - Verify: `cd mobile && flutter analyze` — passes project-wide.
     `grep -n 'startSyncing\|stopSyncing\|pauseSyncing\|resumeSyncing' mobile/lib/features/shopping_list/shopping_list_sync_service.dart`
     returns no matches. `grep -rn 'getSyncStatusNotifier' mobile/lib`
     returns no matches.

7. **Build and manual-verify the full feature.**
   - `cd mobile && flutter build apk --debug`.
   - Run on device/emulator and execute each step in `tasks.md` T2
     "How to verify" (online CRUD, offline queue + reconnect,
     background/foreground, cross-device conflict snackbar).
   - Verify: each scenario observably matches pre-refactor behaviour.

## Test plan

**Unit tests** — _N/A — the project has no unit tests today (`mobile/test/`
contains only the Flutter starter `widget_test.dart`), and T2 is not the
task to introduce a testing framework. The design classifies this as a
structural refactor whose verification is behavioural, performed
manually per `tasks.md`._

**Integration tests** — _N/A — same reason._

**Flutter widget/integration tests** — _N/A — same reason._

**Manual verification** (the sole verification level)

Exercise each case and confirm behaviour matches pre-refactor:

- Cold-load a shopping list — items render, sync-status icon resolves to
  "check" once the queue is empty.
- Add an item online — appears instantly (optimistic), server id replaces
  temp id without UI flicker, icon settles on "check".
- Add, edit, check, delete the same item rapidly — all operations survive
  a full app restart in the server-confirmed state.
- Airplane mode on → add 3 items, edit one, delete one → airplane mode
  off → all queued ops drain, final list matches server.
- Background the app for ≥10 s mid-sync (after queueing ops) →
  foreground → periodic fetch resumes (observed by sync-status icon
  flipping) and list is consistent.
- Concurrent edit from a second client → conflict snackbar appears on
  this client, list refreshes to server state.
- Force a connection error mid-sync (e.g. kill network then re-enable) —
  retry succeeds, no error surfaced.

## Verification checklist

- [ ] `cd mobile && dart analyze` passes with no warnings on the three
      modified files.
- [ ] `cd mobile && dart format --set-exit-if-changed lib/features/shopping_list`
      passes.
- [ ] `cd mobile && flutter build apk --debug` succeeds.
- [ ] `grep -rn 'getSyncStatusNotifier' mobile/lib` returns no matches.
- [ ] `grep -n 'startSyncing\|stopSyncing\|pauseSyncing\|resumeSyncing\|_SyncCallbacks'
      mobile/lib/features/shopping_list/shopping_list_sync_service.dart`
      returns no matches.
- [ ] `grep -n '_onItemAdded\|_onItemUpdated'
      mobile/lib/features/shopping_list/shopping_list_detail_service.dart`
      returns no matches.
- [ ] All seven manual verification scenarios above pass.
- [ ] `tasks.md` T2 "How to verify" steps 1–7 all pass.
- [ ] `design.md` > Assumptions to verify — none outstanding; nothing to
      defer.

## Risks surfaced during planning

- **Risk:** Current `ShoppingListDetailService.dispose()` does not
  dispose `_shoppingListDetail` or `_sharedUsers` `ValueNotifier`s. The
  state-management standard requires it, and T2 is the natural moment
  to fix it since `dispose()` is already being expanded.
  **Why it matters:** Leaking notifiers across repeated list opens is
  a latent bug; if we expand `dispose()` in T2 without fixing it, we
  silently reaffirm the leak.
  **Mitigation:** Fix in step 5 above (approved by user). This is a
  small scope expansion beyond what `tasks.md` lists explicitly but
  falls within the design's standards-compliance intent.

- **Risk:** `_handleItemSynced` re-applies pending ops whose
  `itemId == serverItem.id` after the in-place replace. For `AddItemOperation`,
  the item starts its life in the queue under a temp id, and
  `_replaceValuesInQueue` rewrites subsequent queued ops to the server id
  only after the add succeeds. An `ItemSynced` event for an add arrives
  *after* that rewrite — confirmed by reading T1's `_processQueue`
  (emit is after `_replaceValuesInQueue`) — so `pendingOperations` will
  already carry the server id and the filter `op.itemId == serverItem.id`
  will match correctly.
  **Why it matters:** If the ordering were the other way around,
  pending ops would still carry the temp id and the filter would skip
  them, dropping optimistic state.
  **Mitigation:** Ordering is correct in today's sync service code;
  this is load-bearing. Add a one-line code comment in
  `_handleItemSynced` explaining the dependency so a future edit to
  `_processQueue` that moves the emit before the rewrite trips a
  reviewer.

- **Risk:** `pauseSyncing` cancels only the local timer but keeps the
  stream subscription alive. If the app is backgrounded while the sync
  queue still drains, `ItemSynced` events will still fire into
  `_handleItemSynced` and mutate `_shoppingListDetail.value` — which is
  exactly what we want (state stays consistent), but it's a behavioural
  difference from today (where the old sync service owned the timer and
  nothing reconciled while paused).
  **Why it matters:** Likely a strict improvement, but worth flagging
  — background reconciliation means the state observed immediately on
  foreground is fresher than before the refactor.
  **Mitigation:** Accept as intended behaviour; manual verification
  step ("background mid-sync, foreground") will catch regressions
  either way.
