# T3: Mobile Push Sync — Implementation Plan

**Date:** 2026-07-03
**Status:** draft

## Required reading

**Docs & standards** (from `docs/INDEX.md`)

- `docs/mobile/standards/state-management.md` — `ValueNotifier`/`ValueListenable` read-only exposure, `_isXxxRunning` guards, mandatory `dispose()` on state-owning classes; the sync service's per-list `SyncStatus` notifiers and the detail service's passthroughs follow it.
- `docs/mobile/standards/architecture.md` — Repository-Service-View layering and the (T2-relaxed) rule that a repository may hold state + do HTTP and storage; item-write HTTP living on `ShoppingListItemRepository` relies on this.
- `docs/mobile/standards/dependency-injection.md` — per-feature `setup*()` functions, constructor injection, `registerSingleton` with a `dispose:` callback; the new sync-service registration mirrors the T2 `ShoppingListItemRepository` registration.
- `docs/mobile/standards/logging.md` — `recipai.<feature>.<layer>` logger names, level conventions, never log tokens/bodies; the discard (`log.error`) and drain logging follow it.
- `docs/backend/modules/shopping-lists/api.md` — the three item endpoints this task calls (status codes, `baseVersion` placement, raw-item 412 body). **Confirms two assumptions:** PUT carries `baseVersion` in the JSON **body**, DELETE carries it as a **`?baseVersion=` query param**.

**Design & ADRs**

- `plans/T3-task-design.md` — the design this plan implements; all decisions (per-list sequential drain, snapshot outbox, response classification, view-drained rejection events) are settled there.
- `plans/T2-task-design.md` §3–§6 — the store, outbox entry shape, tombstone rule, last-acked-version / `dirty` contract this task extends.
- `plans/T1-task-design.md` (Endpoints + DTOs) — request/response shapes, the 412 raw-winner body, create returns `version: 0`.
- `../hld.md` §2.2 (single-change flow), §2.4 (failure surface), §3 (layer responsibilities & lifecycle) — behaviour contract; note the deliberate deviation from §2.2's cross-item parallelism (T3 is per-list sequential).

**Code to mirror**

- `mobile/lib/features/shopping_list/shopping_list_repository.dart` — `_getAuthHeaders(idToken)`, `http.Client`, `AppConfig.apiBaseUrl`, `Uri.parse('$_baseUrl/...')`, per-status branching, `Stopwatch`/`_log` pattern to copy for the three new item-write methods.
- `mobile/lib/features/shopping_list/shopping_list_item_repository.dart` — the T2 store: `_cache`/`_notifiers`, `openList`/`watch`/`_visibleItems`, the `_dao.transaction((txn) async {...})` write-through pattern, and the `applyEdit`/`applyChecked`/`applyReorder` outbox-write path to change to full snapshots.
- `mobile/lib/features/shopping_list/shopping_list_item_dao.dart` — `upsertItemTxn`/`appendOutboxTxn` txn-scoped helper style, `OutboxKind.wire`/`fromWire`, `jsonEncode`/`jsonDecode` of `payload`, `LocalShoppingListItem.fromMap`; the new DAO reads/mutations mirror these.
- `mobile/lib/features/shopping_list/shopping_list_detail_service.dart` — constructor injection, `_openListId` handling in `openShoppingList`, the item-listener wiring + `dispose()`; new sync passthroughs and `requestDrain` calls hang off these.
- `mobile/lib/features/shopping_list/shopping_list_detail_screen.dart` — `StatefulWidget` + `WidgetsBindingObserver` lifecycle (`initState`/`dispose`), `ScaffoldMessenger.of(context).showSnackBar` usage (lines 113–124), the `TODO(..., T3)` at line 67 to replace and the `TODO(..., T4)` at line 428 to keep.
- `mobile/lib/features/shopping_list/shopping_list_item.dart` — `ShoppingListItem.fromJson` used to parse the 412 winner and the create/update ack bodies.
- `mobile/lib/features/shopping_list/shopping_list_setup.dart` — the async `setupShoppingList` and the `registerSingleton(..., dispose:)` / `registerLazySingleton` ordering to extend.

## File inventory

All paths under `mobile/lib/features/shopping_list/` unless noted.

- **CREATE** `shopping_list_sync_service.dart` — `ShoppingListSyncService` (per-list drain/retry/backoff/status orchestration) plus `SyncStatus` enum and `RejectionEvent` value type.
- **MODIFY** `shopping_list_item_repository.dart` — add `http.Client` + `AppConfig.apiBaseUrl` and `createItem`/`updateItem`/`deleteItem`; add store mutations `reconcileAck`/`reconcileDeleteAck`/`cascadeDiscard`/`discardItem`; change `applyEdit`/`applyChecked`/`applyReorder` to write full snapshots; add `ItemVersionConflictException`, `ItemDiscardedException` (+ `DiscardReason` enum) and the `OutboxPayload` snapshot type.
- **MODIFY** `shopping_list_item_dao.dart` — add `nextOutboxEntry`, `listIdsWithOutbox`, `readItem`, and txn-scoped `deleteOutboxEntryTxn`/`deleteOutboxForItemTxn`/`countOutboxForItemTxn`/`deleteItemRowTxn`; add the `OutboxEntry` value type.
- **MODIFY** `shopping_list_detail_service.dart` — new `ShoppingListSyncService` dep; call `requestDrain(listId)` after each store mutation and on `openShoppingList`; expose `syncStatus`, `rejections`, `retrySync()` passthroughs.
- **MODIFY** `shopping_list_detail_screen.dart` — persistent bottom failure banner + retry button gated on `syncStatus == failure`; subscribe to `service.rejections` in `initState`, show `SnackBar`, cancel in `dispose`; replace the line-67 `TODO(..., T3)`.
- **MODIFY** `shopping_list_setup.dart` — give `ShoppingListItemRepository` its HTTP deps (unchanged registration), register `ShoppingListSyncService` (singleton, `dispose:`) after it, call `start()`, and pass it into `ShoppingListDetailService`.
- **MODIFY** `shopping_list_repository.dart` — remove the obsolete `TODO(shopping-list-items)` at line 152 (item-write HTTP now lives on the item repository).

## Step-by-step plan

All Flutter commands run from `mobile/`.

1. **DAO: outbox reads + txn-scoped mutations + `OutboxEntry`** — add the `OutboxEntry` value type (`seq`, `itemLocalId`, `listId`, `OutboxKind kind`, decoded `Map<String, dynamic> payload`); `nextOutboxEntry(listId)` (`SELECT ... WHERE list_id=? ORDER BY seq ASC LIMIT 1`, decode payload), `listIdsWithOutbox()` (`SELECT DISTINCT list_id FROM outbox`), `readItem(localId)` (single row → `LocalShoppingListItem?`), and txn helpers `deleteOutboxEntryTxn(txn, seq)`, `deleteOutboxForItemTxn(txn, localId)`, `countOutboxForItemTxn(txn, localId)`, `deleteItemRowTxn(txn, localId)`.
   - Files: `shopping_list_item_dao.dart`
   - Verify: `flutter analyze` clean.

2. **Store: snapshot outbox writes** — change `applyEdit`/`applyChecked`/`applyReorder` so the appended `update` payload is the **full** field set of the resulting item (`name, quantity, unit, checked, position`) instead of the current delta. (`applyCreate` already writes the full set — leave it; it keeps `checked`.)
   - Files: `shopping_list_item_repository.dart`
   - Verify: `flutter analyze` clean; manual: edit an item, confirm the new outbox row's `payload` carries all five fields (via a debug print or DB inspection).

3. **HTTP: item-write methods + push-outcome exceptions** — add `ItemVersionConflictException(ShoppingListItem winner)`, `ItemDiscardedException(DiscardReason reason)` with `enum DiscardReason { gone, rejected }`, and the `OutboxPayload` snapshot type (build from a decoded outbox map). Give the repository an `http.Client` (own by default, optionally injected) + `AppConfig.apiBaseUrl` and add `createItem`/`updateItem`/`deleteItem`, mirroring `ShoppingListRepository`'s header/auth style and mapping statuses per the design's **Response classification** table (201/200 → parse `ShoppingListItem`; 204/404-on-delete → return; **412** → throw conflict with the parsed winner; **404** on create/update → `ItemDiscardedException(gone)`; **400/403** → `ItemDiscardedException(rejected)`; **401/408/429/5xx/network/timeout** → plain `Exception` = transient). PUT sends `baseVersion` in the JSON body; DELETE sends it as `?baseVersion=`.
   - Files: `shopping_list_item_repository.dart`
   - Verify: `flutter analyze` clean.

4. **Store: reconcile mutations (DB + cache coherent)** — add `reconcileAck`/`reconcileDeleteAck`/`cascadeDiscard`/`discardItem`, each wrapping its DB work in one `_dao.transaction`. `reconcileAck`: drop the acked entry, `dirty = countOutboxForItem > 0`, set `serverId=winner.id` + `lastAckedVersion=winner.version`, **fields untouched**. `reconcileDeleteAck`: `deleteItemRowTxn` + `deleteOutboxEntryTxn`. `cascadeDiscard`: overwrite fields from `winner`, `serverId`/`lastAckedVersion` from winner, `pendingDelete=false` (un-tombstone), `dirty=false`, `deleteOutboxForItemTxn`. `discardItem`: `deleteItemRowTxn` + `deleteOutboxForItemTxn`. In every one: if the list is resident (`_cache.containsKey(listId)`) also update `_cache[listId][localId]` (or remove it) and set the notifier to `_visibleItems(listId)`; otherwise DB-only.
   - Files: `shopping_list_item_repository.dart`
   - Verify: `flutter analyze` clean.

5. **Sync service** — create `shopping_list_sync_service.dart`: `enum SyncStatus { syncing, notSyncing, failure }`; `RejectionEvent(listId, itemName, RejectionOutcome outcome)` with `enum RejectionOutcome { conflict, gone, rejected }`; `ShoppingListSyncService with WidgetsBindingObserver`. Per-list state: `_draining`/`_pending` sets, `_retry` map, `_backoffTimers` map, `_status` map of `ValueNotifier<SyncStatus>`, one broadcast `StreamController<RejectionEvent>`. Implement `syncStatusFor` (lazily create `notSyncing`), `rejections`, `requestDrain`, `retry`, `start` (addObserver + fan-out over `listIdsWithOutbox()`), `didChangeAppLifecycleState` (resume → same fan-out), `dispose`, and the internal `_drain`/`_drainPass`/`_pushOne` exactly per the design's pseudo-code. Backoff: `MAX_RETRIES = 5`, one-shot `Timer` at `min(2^(n-1), 30)` seconds re-kicking `requestDrain`.
   - Files: `shopping_list_sync_service.dart`
   - Verify: `flutter analyze` clean.

6. **Detail service passthroughs + drain kicks** — add the `ShoppingListSyncService` dep; call `_syncService.requestDrain(listId)` at the end of `openShoppingList` and after each mutating action (`addItem`, `editItem`, `toggleChecked`, `deleteItem`, `reorderItem`, and once after each of `deleteAllChecked`/`uncheckAll`). Expose `ValueListenable<SyncStatus> get syncStatus` → `_syncService.syncStatusFor(_openListId!)` (`_openListId` is set synchronously at the top of `openShoppingList`, before the first `await`, so it is available by first build), `Stream<RejectionEvent> get rejections` → `_syncService.rejections.where((e) => e.listId == _openListId)`, and `Future<void> retrySync()` → `_syncService.retry(_openListId!)`.
   - Files: `shopping_list_detail_service.dart`
   - Verify: `flutter analyze` clean.

7. **View: failure banner + rejection toast** — replace the line-67 `TODO(..., T3)`. In `initState`, subscribe to `service.rejections`; on each event, if `mounted`, `ScaffoldMessenger.of(context).showSnackBar(...)` with copy chosen from `event.outcome`; store the `StreamSubscription` and cancel it in `dispose`. Add a persistent bottom banner (`Scaffold.bottomNavigationBar` or a bottom `Material` inside `SafeArea`) via a `ValueListenableBuilder` over `service.syncStatus` that renders only when `SyncStatus.failure`, with a **Retry** button → `service.retrySync()`. Keep the line-428 `TODO(..., T4)`.
   - Files: `shopping_list_detail_screen.dart`
   - Verify: `flutter analyze` clean.

8. **DI wiring** — in `setupShoppingList`: after the `ShoppingListItemRepository` registration, register `ShoppingListSyncService` as a singleton with `dispose: (s) => s.dispose()`, then call `getIt<ShoppingListSyncService>().start()`; add `syncService: getIt<ShoppingListSyncService>()` to the `ShoppingListDetailService` registration. Remove the obsolete line-152 `TODO` in `shopping_list_repository.dart`. `main.dart` unchanged.
   - Files: `shopping_list_setup.dart`, `shopping_list_repository.dart`
   - Verify: `flutter analyze` clean; app builds (`flutter run` boots to the shopping-list screen).

## Test plan

**Automated tests**

- _N/A — this task ships with manual verification only, matching T2 (no `sqflite_common_ffi` store harness is configured, and no unit/widget tests are added for T3). The scenarios below exercise the drain, reconcile, 412 cascade, discard, and backoff/failure paths end-to-end against a running backend._

**Manual verification** (maps to `tasks.md` T3 "How to verify")

- Edit an item offline, reconnect → `GET /shopping-lists/{id}` reflects it.
- Queue two offline edits to one item, force a concurrent server change so the first push hits 412 → the item rolls back to the winner, **both** queued entries drop, one rejection toast appears on the open list.
- Stop the server, make an edit → after backoff the persistent bottom banner appears; restart the server, tap **Retry** → the change pushes and the banner clears.
- Delete-all-checked with one item concurrently edited → that item reappears with its own toast while the rest are removed.
- Offline edit to a list you are **not** viewing → on reconnect (start/resume fan-out) it flushes without opening that list.

## Verification checklist

- [ ] `flutter analyze` — clean (no new warnings)
- [ ] `flutter test` — existing tests still pass (no new tests added this task)
- [ ] `tasks.md` > T3 "How to verify" scenarios each demonstrated (manual list above)
- [ ] Design assumptions resolved or deferred: 412 body is a raw `ShoppingListItem` (parsed in `updateItem`/`deleteItem`); create returns id + `version: 0` (`reconcileAck` sets both); T2 snapshot change lands (step 2); per-list sequential UX accepted; 400/403 vs 404 vs transient split holds against T1 (confirmed in `api.md`); dropping a rejection toast for a closed screen accepted; `AuthService.idToken` fetched per push
- [ ] No item-write HTTP left on `ShoppingListRepository` (line-152 TODO removed)
- [ ] `ShoppingListSyncService.dispose()` cancels all timers, removes the observer, closes the stream, disposes status notifiers
- [ ] Logs at `INFO` clean on the happy path; discards logged at `error`; no tokens/bodies logged

## Risks surfaced during planning

- **Risk:** The design's store pseudo-code (`reconcileAck`/`discardItem`) reads and writes the DB but the resident-list **in-memory cache** (`_cache`) is what the notifier renders; writing DB-only while a list is open would leave stale `serverId`/`dirty` in the cache until the next `openList`.
  **Why it matters:** the visible list could show a stale sync state, or a later push could read a stale cached row.
  **Mitigation:** every reconcile mutation must update `_cache[listId][localId]` (or remove it) **and** refresh the notifier when the list is resident, DB-only otherwise — called out explicitly in step 4.

- **Risk:** T2 stores `update` outbox payloads as **deltas** (`applyChecked` writes only `checked`, `applyReorder` only `position`); T3 requires full snapshots. Any outbox entries queued by an already-installed T2 build would decode to partial `OutboxPayload`s.
  **Why it matters:** a partial snapshot pushed as a full PUT would send `null`/missing fields and corrupt the item.
  **Mitigation:** the item DB is developer/test-only at this stage (no production release between T2 and T3), so a clean install is acceptable; step 2 changes the write path so all newly-queued entries are full snapshots. If a migration path is later needed, gate `OutboxPayload.fromMap` to fall back to the live item for absent fields — flag before implementing if T2 has shipped to real users.

- **Risk:** T3 ships with manual verification only (no automated coverage) for a service whose value is in edge cases — 412 cascade, gone/rejected discard, coalesced kicks, per-list backoff.
  **Why it matters:** regressions in these paths won't be caught by CI; the manual scenarios must actually be run each time the sync code changes.
  **Mitigation:** the manual scenarios in the Test plan map 1:1 to `tasks.md` T3 "How to verify" and cover each edge path; run them before merge. Reconsider adding sync-service unit tests (mocked repo/auth) if this code churns.

- **Risk:** `deleteAllChecked`/`uncheckAll` fan out into many single-item outbox entries and a burst of `requestDrain` kicks.
  **Why it matters:** naive per-mutation kicks could spawn redundant drains.
  **Mitigation:** the coalesced `requestDrain` (per-list `_draining`/`_pending` guard) already absorbs the burst; the detail service issues **one** kick after each bulk loop rather than per item.
