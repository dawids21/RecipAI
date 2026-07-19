# T4: Mobile Pull Sync (full-list poll + diff) — Implementation Plan

**Date:** 2026-07-05
**Status:** draft

## Required reading

**Docs & standards** (from `docs/INDEX.md`)

- `docs/mobile/standards/state-management.md` — `ValueNotifier`/`ValueListenable` read-only exposure and mandatory `dispose()`; the new per-list `SyncStatus.offline` transitions and the poll `Timer` lifecycle follow it.
- `docs/mobile/standards/architecture.md` — Repository-Service-View layering and the T2-relaxed rule that a repository may hold state + do HTTP + storage; `fetchServerItems` (GET) and `reconcileFromServer` (store diff) both landing on `ShoppingListItemRepository` rely on it.
- `docs/mobile/standards/logging.md` — `recipai.<feature>.<layer>` logger names, never log tokens/bodies; the poll GET and reconcile logging follow the T3 `_log` pattern.
- `docs/backend/modules/shopping-lists/api.md` — `GET /shopping-lists/{id}` returns an `items[]` array of `{id,name,quantity,unit,checked,position,version}` ordered by `position` (**confirms the T4 parse assumption**; `ShoppingListItem.fromJson` matches element-for-element).

**Design & ADRs**

- `plans/T4-task-design.md` — the design this plan implements; the diff rules (§Pseudo-code), push-priority gate (§Serialization), status transitions (§Status), and Option-A active-edit rule (§Decisions) are all settled there.
- `plans/T3-task-design.md` (§Interfaces, §Decisions) — the sync service being extended: the `_draining`/`_pending` push state (T4 renames `_draining`→`_busy` and the poll gate reads it), the existing `SyncStatus` transitions `offline` slots into, and why a dirty item must never be pull-deleted (`_pushOne` reads `readItem(...)!`).
- `plans/T2-task-design.md` §3–§8 — the store, the last-acked-version / `dirty` contract (pull must not advance last-acked for a dirty item), tombstones, and `(position, localId)` ordering for inserted remote items.
- `docs/ADRs/0003-shopping-list-full-refresh-over-delta.md` — the full-pull-and-diff decision and canonical per-item diff rules.

**Code to mirror**

- `mobile/lib/features/shopping_list/shopping_list_sync_service.dart` — the T3 service to extend: `requestDrain`/`_drain`/`_drainPass`/`_pushOne`, `_backoffTimers` timer-map style (mirror for `_pollTimers`), `_setStatus`/`_statusNotifier`, `WidgetsBindingObserver` lifecycle, and `dispose`.
- `mobile/lib/features/shopping_list/shopping_list_item_repository.dart` — the reconcile-mutation pattern (`_residentListId`, one `_dao.transaction`, notifier refresh via `_visibleItems`) to mirror in `reconcileFromServer`; the item-write HTTP style (`_getAuthHeaders`, `Stopwatch`/`_log`, per-status branching) to mirror for `fetchServerItems`; `_uuid` for inserted remote items.
- `mobile/lib/features/shopping_list/shopping_list_repository.dart` — `fetchShoppingListDetail`'s `GET` shape (`Uri.parse('$_baseUrl/shopping-lists/$id')`, `_getAuthHeaders`, `json.decode`) to mirror for `fetchServerItems`.
- `mobile/lib/features/shopping_list/shopping_list_item_dao.dart` — `readItems(listId)`, `transaction`, `upsertItemTxn`, `deleteItemRowTxn` — everything `reconcileFromServer` needs already exists (no DAO change).
- `mobile/lib/features/shopping_list/shopping_list_detail_service.dart` — `openShoppingList` (hydrate + subscribe + `requestDrain`) and `loadShoppingListDetail` (chrome GET) to reorchestrate; `dispose` to add `stopPolling`.
- `mobile/lib/features/shopping_list/shopping_list_item_widget.dart` — `didUpdateWidget`'s `oldWidget.item != widget.item && !_focusNode.hasFocus` branch to replace (Option A); `_focusNode`/`_formatItem`/`_controller`.
- `mobile/lib/features/shopping_list/shopping_list_detail_screen.dart` — the line 482–483 `TODO(..., T4)` to replace with the indicator; `_showRejectionToast` (lines 73–84) as the toast path `onOverwrite` reuses; `_buildSyncFailureBanner` (`ValueListenableBuilder` over `service.syncStatus`) as the indicator's shape; `initState` (lines 51–58) to reorchestrate.

## File inventory

All paths under `mobile/lib/features/shopping_list/`.

- **MODIFY** `shopping_list_item_widget.dart` — add `ItemDisplayData.hasSameContentAs`; add `onOverwrite` callback; rewrite `didUpdateWidget` for Option A (overwrite-while-focused + fire `onOverwrite`).
- **MODIFY** `shopping_list_item_repository.dart` — add `fetchServerItems(listId, idToken)` (GET → `List<ShoppingListItem>`) and `reconcileFromServer(listId, serverItems)` (dirty-aware full-list diff); add `ShoppingListNetworkException` and rethread the four network/timeout catch blocks (GET + create/update/delete) to throw it.
- **MODIFY** `shopping_list_sync_service.dart` — add `offline` to `SyncStatus`; rename `_draining`→`_busy` and acquire it in the poll too; add `_pollTimers` (its keys are the polled lists), `startPolling` (immediate poll + periodic timer), `stopPolling`, `_poll`, `_canReconcile` (`!_busy.contains`); map `ShoppingListNetworkException` to `offline` (swallowed) in poll and drain; poll success clears `offline` only (never a sticky `failure`); pause/re-arm poll timers on app lifecycle; cancel poll timers in `dispose`.
- **MODIFY** `shopping_list_detail_service.dart` — reorchestrate `openShoppingList` (hydrate → subscribe → `startPolling` → `requestDrain` → `await loadShoppingListDetail`); `startPolling` **before** `requestDrain` so the immediate cold-start poll isn't dropped by the drain holding `_busy` (§Serialization "On-open ordering"); `loadShoppingListDetail` becomes **chrome-only** (name/role — no longer seeds items); `dispose` calls `stopPolling`.
- **MODIFY** `shopping_list_detail_screen.dart` — replace the line 482 `TODO(..., T4)` with a `_buildSyncIndicator()` from `service.syncStatus`; wire `onOverwrite: _showOverwriteToast` on both item widgets; `initState` calls `openShoppingList` (which now drives the chrome load) instead of a separate `loadShoppingListDetail`.

_No change to `shopping_list_item_dao.dart` (all reads/mutations `reconcileFromServer` needs already exist) or `shopping_list_setup.dart` (the sync service's constructor gains no new deps)._

## Step-by-step plan

All Flutter commands run from `mobile/`.

1. **Widget: `hasSameContentAs` + Option A active-edit rule** — add `bool hasSameContentAs(ItemDisplayData other)` to `ItemDisplayData` comparing `name`, `quantity`, `unit`, `checked`. Add `final VoidCallback? onOverwrite;` to `ShoppingListItemWidget` (+ constructor param). Rewrite `didUpdateWidget` per §Pseudo-code: `if (!widget.item.hasSameContentAs(oldWidget.item)) { _controller.text = _formatItem(); if (_focusNode.hasFocus) widget.onOverwrite?.call(); }` — the content-diff replaces today's identity `!=`, so the branch no longer fires on every rebuild-while-focused, and a real remote change now overwrites the field (and toasts) even while focused.
   - Files: `shopping_list_item_widget.dart`
   - Verify: `flutter analyze` clean.

2. **Repository: pull GET + full-list diff** — add `class ShoppingListNetworkException implements Exception`. Add `Future<List<ShoppingListItem>> fetchServerItems(String listId, String? idToken)`: `GET $_baseUrl/shopping-lists/$listId` mirroring `ShoppingListRepository.fetchShoppingListDetail` (headers, `Stopwatch`, `_log.info`); on a caught network/timeout throw `ShoppingListNetworkException`; on `200` parse `(json.decode(body)['items'] as List).map(ShoppingListItem.fromJson)`; on any other status throw `Exception` (store left untouched by the caller). Rethread the existing four network catch blocks (`createItem`/`updateItem`/`deleteItem` and the new GET) to `throw ShoppingListNetworkException()` instead of the plain `Exception('Network error…')`. Add `Future<void> reconcileFromServer(String listId, List<ShoppingListItem> serverItems)` implementing §Pseudo-code exactly: resident (`_cache.containsKey(listId)`) → diff the cache; else DB-only via `_dao.readItems(listId)`. In one `_dao.transaction`: (a) per server item insert new (`_uuid.v4()`, `dirty:false`) / adopt clean iff `!local.dirty && s.version > local.lastAckedVersion!` (equal version = identical acked state, adopting is redundant; fields + `lastAckedVersion` + `pendingDelete:false`, **never** touching a dirty item or advancing its last-acked) ; (b) per local item with `serverId != null` absent from the server, hard-delete iff `!local.dirty` (keep dirty-missing and `serverId == null` pending-creates). Update `_cache[listId]` alongside each txn write and set `_notifiers[listId]!.value = _visibleItems(listId)` when resident.
   - Files: `shopping_list_item_repository.dart`
   - Verify: `flutter analyze` clean.

3. **Sync service: poller + offline status + shared `_busy` gate** — add `offline` to `enum SyncStatus`. **Rename `_draining` → `_busy`** (T3 methods may change per your go-ahead): `requestDrain`/`_drain`/`retry` keep their logic, just reading/writing `_busy`. Add `const _pollInterval = Duration(seconds: 10);` and `final _pollTimers = <String, Timer>{};` (its keys are the polled lists — no separate set). `bool _canReconcile(String listId) => !_busy.contains(listId);` (synchronous — no outbox read). `Future<void> _poll(String listId)`: `if (!_canReconcile(listId)) return;` (dropped while a drain holds `_busy`; no drain kick — §Serialization); else `_busy.add(listId); try { final items = await _itemRepository.fetchServerItems(listId, await _authService.idToken); await _itemRepository.reconcileFromServer(listId, items); if (_statusNotifier(listId).value == SyncStatus.offline) _setStatus(listId, SyncStatus.notSyncing); } on ShoppingListNetworkException { _setStatus(listId, SyncStatus.offline); } catch (_) { /* non-2xx: store + status untouched */ } finally { _busy.remove(listId); if (_pending.remove(listId)) requestDrain(listId); }` — the `finally` releases `_busy` and honours any drain deferred during the poll. **Note the conditional status:** clear `offline` only, never a sticky `failure` (a poll can run with a non-empty outbox). `startPolling(listId)`: `_pollTimers.remove(listId)?.cancel(); unawaited(_poll(listId)); _pollTimers[listId] = Timer.periodic(_pollInterval, (_) => unawaited(_poll(listId)));` (immediate poll + periodic). `stopPolling(listId)`: `_pollTimers.remove(listId)?.cancel();`. In `_drainPass`, add `on ShoppingListNetworkException { _setStatus(listId, SyncStatus.offline); return false; }` **before** the generic transient `catch` (network push → offline + swallow, no backoff/failure; retries on the usual signals). In `didChangeAppLifecycleState`: on `paused`/`inactive`, cancel each `_pollTimers` value in place (keys retained as the polled-lists marker); on `resumed` (after the existing `_fanOutPending`) re-arm each `id in _pollTimers.keys.toList()` (`cancel()` + fresh `Timer.periodic`) and `unawaited(_poll(id))` for an immediate refresh. In `dispose`, cancel and clear `_pollTimers`.
   - Files: `shopping_list_sync_service.dart`
   - Verify: `flutter analyze` clean.

4. **Detail service: orchestrate open, stop on dispose** — in `openShoppingList`, after the existing hydrate + subscribe, add `_syncService.startPolling(listId);` (its immediate poll is the cold-start item load) **then** `_syncService.requestDrain(listId);` — `startPolling` **must precede** `requestDrain`: a poll pass is dropped (not deferred) while a drain holds `_busy`, and `_drain` acquires `_busy` synchronously before its first `await`, so the reverse order swallows the immediate poll and the first server refresh only lands on the next 10s tick (§Serialization "On-open ordering"). With this order the immediate poll grabs `_busy` first and the drain defers via `_pending`, running when the poll completes. Then `await loadShoppingListDetail(listId);` (chrome-only GET for name/role). `loadShoppingListDetail` is **not** changed to seed items — its `detail.items` stays unused (the screen already reads `service.items` from the store). In `dispose`, add `if (_openListId != null) _syncService.stopPolling(_openListId!);` (before the notifier disposes).
   - Files: `shopping_list_detail_service.dart`
   - Verify: `flutter analyze` clean.

5. **Screen: indicator + overwrite toast + open reorchestration** — replace the line 482–483 `TODO(..., T4)` with `_buildSyncIndicator()` (a `ValueListenableBuilder` over `service.syncStatus`, mirroring `_buildSyncFailureBanner`): `offline` → "Offline", `failure` → a "!" chip, `syncing` → subtle "Syncing…", `notSyncing` → a synced tick, rendered above the title. Add `void _showOverwriteToast()` (sibling of `_showRejectionToast`) → `if (mounted) ScaffoldMessenger.of(context).showSnackBar(...)` with overwrite copy. Pass `onOverwrite: _showOverwriteToast` to both `ShoppingListItemWidget`s in `_buildSplitItemWidgets` (active + done; not the ephemeral/add rows). In `initState`, remove the standalone `service.loadShoppingListDetail(widget.shoppingListId);` call (now driven by `openShoppingList`), keeping `openShoppingList`, `loadSharedUsers`, and the rejection subscription. Leave the line-474 failure banner (`bottomNavigationBar`) unchanged.
   - Files: `shopping_list_detail_screen.dart`
   - Verify: `flutter analyze` clean; `flutter run` boots to the detail screen and the indicator renders.

## Test plan

**Automated tests**

- _N/A — matches T2/T3: no `sqflite_common_ffi` store harness is configured and this feature ships with manual verification. The scenarios below exercise the poll, diff, push-priority gate, offline status, and active-edit overwrite end-to-end against a running backend._

**Manual verification** (maps to `tasks.md` T4 "How to verify")

- Open the list on device A; change an item from device B (or `curl`) → within ~10s the change appears on A.
- Delete an item elsewhere → it disappears from A's open list on the next poll; a not-yet-pushed local create on A (`serverId == null`) is **not** removed by a pull.
- Edit a field on A while its text field is focused (uncommitted, non-dirty) and change the same item remotely → the field is overwritten with the server value and an overwrite toast shows.
- Edit an item on A while offline (dirty), then change the same item remotely → the poll keeps A's dirty value (dirty-keep) and does **not** advance its last-acked version; once A reconnects and its push acks, a later poll adopts the server value.
- Go offline → the indicator shows "Offline"; restore connectivity → the next poll clears it back to the synced tick.
- Force-kill and reopen the list → hydrated local contents render immediately, then the single on-open GET seeds/reconciles.

## Verification checklist

- [ ] `flutter analyze` — clean (no new warnings)
- [ ] `flutter test` — existing tests still pass (no new tests this task)
- [ ] `tasks.md` > T4 "How to verify" scenarios each demonstrated (manual list above)
- [ ] Design assumptions resolved or deferred: `GET /shopping-lists/{id}` `items[]` parses with `ShoppingListItem.fromJson` (**confirmed** in `api.md`, step 2); single detail screen open at a time; the shared `_busy` gate closes the create-ack race (no outbox-empty check); `await openList` before `startPolling` guarantees residency for the immediate poll; `startPolling` is issued before `requestDrain` so the drain doesn't drop the immediate cold-start poll; a focused item's `State` survives a pull-driven rebuild (check/uncheck-across-sections corner deferred); non-network poll error leaves the store/status untouched (deferred, §Assumptions); heavy-edit poll starvation accepted
- [ ] Pull never advances `lastAckedVersion` for a dirty item and never hard-deletes a dirty missing item (§Pseudo-code branches (a)/(b))
- [ ] `ShoppingListSyncService.dispose()` cancels all poll timers (in addition to backoff timers, observer, stream, status notifiers)
- [ ] Logs at `INFO` clean on the happy path; no tokens/bodies logged in the poll GET

## Risks surfaced during planning

_The design was revised in this planning round (with the user): the poll-tick-nudges-drain contradiction is resolved (no nudge), the outbox-empty gate is replaced by a shared `_busy` mutual-exclusion flag, and the on-open GET is chrome-only (items come from DB-hydrate + poll). The design doc reflects these; the risks below are what remains._

- **Risk:** The design says "a caught network exception → `offline`" but the T3 repository throws an untyped `Exception('Network error…')`, indistinguishable from a non-2xx `Exception('Failed…')`, so the sync service can't map network→offline without a marker.
  **Why it matters:** without distinguishing them, a persistent server 5xx would be misread as offline (or a real network drop wouldn't set offline), breaking the §Status contract.
  **Mitigation:** step 2 introduces `ShoppingListNetworkException` and rethreads the four network/timeout catch blocks to throw it; the sync service catches it specifically in poll and drain. A small addition beyond the design's component list — flagged since it touches the T3 write methods (you approved T3 edits).

- **Risk:** Dropping the outbox-empty gate lets a poll run while the outbox is non-empty, so a poll success could wrongly clear a sticky `failure` if implemented as an unconditional `notSyncing`.
  **Why it matters:** the retry-all banner would vanish while the failed push is still queued, hiding a real failure from the user.
  **Mitigation:** step 3's `_poll` clears `offline` **conditionally** (only when the current status is `offline`), never touching `failure`/`syncing`. Called out explicitly in the step and in §Status of the design.

- **Risk:** With `_busy` shared by poll and push, a `requestDrain` issued *during* a poll defers (`_pending`) and must be re-kicked when the poll finishes, or an explicit user edit's push could stall until the next natural signal.
  **Why it matters:** a user edit made in the ~sub-second window of a poll would otherwise not push promptly.
  **Mitigation:** step 3's `_poll` `finally` releases `_busy` and, if `_pending` held this list, calls `requestDrain` — honouring the deferred drain. (This is *not* the forbidden "poll nudges push": it fires only for an explicitly-requested drain, not on every tick.)

- **Note (accepted, not a blocker):** on open the chrome GET and the immediate poll both hit `GET /shopping-lists/{id}` (the endpoint has no chrome-only variant), fetching items twice within milliseconds. Accepted in exchange for a single uniform item-sync path (§Decisions). Revisit only if the double-fetch shows up as a real cost.
