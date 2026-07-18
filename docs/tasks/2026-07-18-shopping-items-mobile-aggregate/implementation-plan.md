# Shopping-List Items — Serialised Store Aggregate — Implementation Plan

**Date:** 2026-07-18
**Status:** final

## Required reading

**Docs & standards** (from `docs/INDEX.md`)
- `docs/mobile/standards/architecture.md` — the Repository-Service-View layering
  and file-naming rules the store-service placement bends (raw cache in a
  `*_service.dart`); repos "may hold a cache + notifier", the store inverts that.
- `docs/mobile/standards/state-management.md` — the `ValueNotifier<AsyncValue<T>>`
  service shape the store deliberately deviates from (raw per-list notifiers) and
  the `dispose()` requirement the store must honour.
- `docs/mobile/standards/dependency-injection.md` — `get_it` setup-function and
  constructor-injection rules the new store registration and re-wiring follow.
- `docs/mobile/standards/widget-testing.md` — repository-only mocking, `GetIt.I.reset()`
  lifecycle, and `PreferencesService`-before-`setup*()` ordering the test update obeys.

**Design & ADRs**
- `task-design.md` > Interfaces and method signatures — the exact store/repo/sync
  surfaces to build; `> Pseudo-code` for the locked-public/unlocked-private split
  and the drain guard.
- `task-design.md` > Data flow — the three flows (UI mutation / poll / drain) and
  the "why poll-alongside-drain is safe without `_busy`" argument.
- `docs/ADRs/0004-shopping-list-item-store-aggregate.md` — fixed decisions not to
  reopen: per-list granularity, local-only critical section, `_busy` collapse,
  store is a new class (not the repo relocked).

**Code to mirror**
- `mobile/lib/features/shopping_list/shopping_list_item_repository.dart` — the
  source being split; the `apply*` / `reconcile*` / `_visibleItems` / `_snapshotPayload`
  bodies relocate into the store **verbatim** except for the listId/lock changes.
- `mobile/lib/core/logging/app_log_sink.dart` — the in-repo `Future`-chain serialiser
  (`_writeQueue = _writeQueue.then(...)`), the assumption-#4 fallback if `synchronized`
  is rejected.
- `mobile/lib/features/shopping_list/shopping_list_item_dao.dart` — the DAO surface
  the store delegates to; confirms the outbox is append-only (autoincrement `seq`,
  head read via `ORDER BY seq ASC LIMIT 1`).
- `mobile/lib/features/shopping_list/shopping_list_setup.dart` — the existing
  setup-function + `dispose:` registration pattern the store registration mirrors.

## File inventory

- **CREATE** `mobile/lib/features/shopping_list/shopping_list_item_store_service.dart` — new consistency boundary: cache, per-list notifiers, per-list `Lock`, relocated apply*/reconcile*/open().
- **MODIFY** `mobile/lib/features/shopping_list/shopping_list_item_repository.dart` — strip to HTTP endpoints + shared payload/exception types; drop dao, cache, notifiers, uuid, open(), apply*/reconcile*, store-read passthroughs, `_visibleItems`, `_snapshotPayload`, `_residentListId`.
- **MODIFY** `mobile/lib/features/shopping_list/shopping_list_sync_service.dart` — inject both store + repo; replace `_busy`/`_pending`/`_canReconcile` with `_draining`/`_pending` guard; ungate `_poll`; route reconciles/reads through the store, HTTP through the repo.
- **MODIFY** `mobile/lib/features/shopping_list/shopping_list_detail_service.dart` — depend on store; watch/mutate through it; pass `_openListId!` into apply*; delegate bulk ops; drop the `_busy`-ordering comment in `openShoppingList`.
- **MODIFY** `mobile/lib/features/shopping_list/shopping_list_setup.dart` — register the store; take it as a required param; construct the now-synchronous repo; wire sync/detail to both.
- **MODIFY** `mobile/lib/main.dart` — `await ShoppingListItemStoreService.open()` and pass it into `setupShoppingList`.
- **MODIFY** `mobile/pubspec.yaml` — add `synchronized` to `dependencies`.
- **MODIFY** `mobile/test/support/mocks.dart` — add `MockShoppingListItemStoreService`.
- **MODIFY** `mobile/test/features/recipe/main_screen_recipes_tab_widget_test.dart` — construct + register the store mock, pass `store:` to `setupShoppingList`, move the `listIdsWithOutbox()` stub onto the store mock.

## Step-by-step plan

1. **Add the `synchronized` dependency** — add `synchronized: ^3.4.1` under
   `dependencies:` in `mobile/pubspec.yaml` (place it near the other tekartik/db
   deps, e.g. below `sqflite`), with a one-line comment matching the file's style.
   - Files: `mobile/pubspec.yaml`
   - Verify: `cd mobile && flutter pub get` resolves without conflict; `synchronized`
     appears in `pubspec.lock`.

2. **Create the store service** — add
   `shopping_list_item_store_service.dart` as a standalone new file (not yet wired,
   so it compiles as dead code). Move `openList`, `watch`, `applyCreate/Edit/Checked/Reorder/Delete`,
   `reconcileFromServer`, `reconcileAck`, `reconcileDeleteAck`, `cascadeDiscard`,
   `discardItem`, the store-read passthroughs (`nextOutboxEntry`, `listIdsWithOutbox`,
   `readItem`), `_visibleItems`, `_snapshotPayload`, and `_residentListId` in from the
   repository. Apply the design's transformations:
   - Hold `_dao`, `_cache`, `_notifiers`, `_uuid`, and `final _locks = <String, Lock>{}`.
   - Add `static Future<ShoppingListItemStoreService> open()` (opens the DB via
     `ShoppingListItemDao.openShoppingListDatabase()`, builds the DAO) and a
     `{required ShoppingListItemDao dao}` constructor.
   - Add `Lock _lockFor(String listId) => _locks.putIfAbsent(listId, () => Lock());`.
   - Split each mutation into a locked-public method that `return _lockFor(listId)
     .synchronized(() => _verb(...))` and an unlocked private `_createItem/_editItem/
     _checkItem/_reorderItem/_deleteItem` core holding today's body.
   - Give `applyEdit/applyChecked/applyReorder/applyDelete` an explicit leading
     `String listId` param (drop the `_cache.entries.firstWhere` scan; index
     `_cache[listId]![localId]`).
   - Wrap each `reconcile*` body in `_lockFor(listId).synchronized(...)`; keep the
     resident-or-DB resolution internally but take `listId` for lock selection and
     as a leading param (`reconcileAck(listId, localId, winner, ackedSeq)`,
     `reconcileDeleteAck(listId, localId, ackedSeq)`, `cascadeDiscard(listId, localId, winner)`,
     `discardItem(listId, localId)`). `reconcileFromServer(listId, items)` unchanged
     in shape, body wrapped in the lock.
   - Add `deleteAllChecked(listId)` and `uncheckAll(listId)`: one
     `_lockFor(listId).synchronized(...)` that reads the checked set from
     `_cache[listId]` and loops the **unlocked** `_deleteItem` / `_checkItem` cores.
   - Add `dispose()` disposing every notifier (no `http.Client` here).
   - `readItem`/`nextOutboxEntry`/`listIdsWithOutbox` stay lock-free (plain DAO calls).
   - Files: `mobile/lib/features/shopping_list/shopping_list_item_store_service.dart`
   - Verify: `cd mobile && flutter analyze` reports no errors (an "unused" hint on the
     not-yet-wired class is acceptable at this step).

3. **Reduce the repository to HTTP + rewire sync/detail/setup/main** — one atomic
   change because removing the repo's local methods and its `dao`/`open()` breaks
   every caller at once:
   - `shopping_list_item_repository.dart`: delete `_dao`, `_cache`, `_notifiers`,
     `_uuid`, `open()`, all `apply*`, all `reconcile*`, `nextOutboxEntry`,
     `listIdsWithOutbox`, `readItem`, `_visibleItems`, `_snapshotPayload`,
     `_residentListId`, `openList`, `watch`. Change the constructor to
     `ShoppingListItemRepository({http.Client? client})`. Keep `fetchServerItems`,
     `createItem`, `updateItem`, `deleteItem`, `_getAuthHeaders`, `dispose()` (closes
     client only), and the `OutboxPayload` / `ShoppingListNetworkException` /
     `ItemVersionConflictException` / `ItemDiscardedException` / `DiscardReason` types.
   - `shopping_list_sync_service.dart`: add `required ShoppingListItemStoreService store`
     to the constructor; replace `_busy`/`_pending`/`_canReconcile` with
     `final _draining = <String>{}; final _pending = <String>{};`; rewrite
     `requestDrain` (if `_draining` → `_pending.add`, else `unawaited(_drain)`) and
     `_drain` (add/remove `_draining`, loop on `_pending`); ungate `_poll` (drop the
     `_canReconcile` early-return and `_busy` add/remove/`finally`). Route
     `listIdsWithOutbox`/`nextOutboxEntry`/`readItem`/`reconcileFromServer`/`reconcileAck`/
     `reconcileDeleteAck`/`cascadeDiscard`/`discardItem` to `_store`, passing
     `entry.listId` into the reconcile calls; keep `fetchServerItems`/`createItem`/
     `updateItem`/`deleteItem` on `_itemRepository`.
   - `shopping_list_detail_service.dart`: add `required ShoppingListItemStoreService store`;
     in `openShoppingList` call `store.openList`/`store.watch` and delete the
     `_busy`-ordering comment; pass `_openListId!` (renamed `listId` locals already
     present) into `store.applyEdit/applyChecked/applyDelete/applyReorder`; make
     `deleteAllChecked`/`uncheckAll` delegate to `store.deleteAllChecked(listId)` /
     `store.uncheckAll(listId)` then `_requestDrainForOpenList()`.
   - `shopping_list_setup.dart`: add `required ShoppingListItemStoreService store`
     param; `registerSingleton<ShoppingListItemStoreService>(store, dispose: (s) => s.dispose())`;
     construct `itemRepository ??= ShoppingListItemRepository()` (now synchronous);
     pass `store` into the sync service and detail service constructors.
   - `main.dart`: `setupShoppingList(store: await ShoppingListItemStoreService.open())`;
     drop the `ShoppingListItemRepository.open()` call and its now-unused import if
     nothing else needs it.
   - Files: `shopping_list_item_repository.dart`, `shopping_list_sync_service.dart`,
     `shopping_list_detail_service.dart`, `shopping_list_setup.dart`, `main.dart`
   - Verify: `cd mobile && flutter analyze` is clean; `flutter build apk --debug`
     (or `flutter run` on a device) compiles.

4. **Update test doubles** — add `class MockShoppingListItemStoreService extends Mock
   implements ShoppingListItemStoreService {}` to `test/support/mocks.dart` (with the
   store-service import). In `main_screen_recipes_tab_widget_test.dart`: declare/construct
   a `MockShoppingListItemStoreService`, move the existing
   `when(() => ...listIdsWithOutbox()).thenAnswer((_) async => const [])` stub onto the
   store mock (that call now lives on the store), and pass both `store:` and
   `itemRepository:` into `setupShoppingList`.
   - Files: `mobile/test/support/mocks.dart`,
     `mobile/test/features/recipe/main_screen_recipes_tab_widget_test.dart`
   - Verify: `cd mobile && flutter test test/features/recipe/main_screen_recipes_tab_widget_test.dart`
     passes; then `flutter test` (full suite) passes.

5. **Manual concurrency verification** — run the app and exercise the design's
   hand-stageable scenarios (see Test plan > Manual verification). No automated
   interleave harness this task (per the task-design testing decision).
   - Files: none
   - Verify: each scenario ends with cache == DB == outbox agreement and no lost
     update (reload the list from disk to confirm).

## Test plan

**Unit tests**
- _N/A — the task-design chose manual scenarios only (HLD > Concurrency confidence);
  no automated interleave harness is in scope, and the relocated logic is unchanged
  behaviourally._

**Integration tests**
- _N/A — no backend or DB-integration surface changes; the wire protocol, outbox
  format, and conflict rules are anti-requirements._

**Flutter widget/integration tests**
- `main_screen_recipes_tab_widget_test.dart` — regression only: still builds the main
  screen and drives its existing assertions after `setupShoppingList` gains the store
  mock (proves DI wiring compiles and `start()`'s `store.listIdsWithOutbox()` fan-out
  is stubbed).

**Manual verification** (each forces a mutation to overlap a sync op, then asserts
cache/DB/outbox agreement — reload from disk to confirm nothing was lost):
- **Edit during a pull** — begin editing an item, let a 10s poll land mid-edit; the
  edit survives (dirty-gate holds), the server value does not clobber it.
- **Rapid toggling during active sync** — check/uncheck an item repeatedly while a
  drain and poll run; final visible state matches the last tap and the outbox drains
  to empty.
- **Bulk action overlapping a poll** — trigger `deleteAllChecked` / `uncheckAll` while
  a poll is in flight; the batch is one atomic section (no half-applied bulk).
- **Rapid double-taps** — double-add / double-delete; no duplicate rows, no orphaned
  outbox entries.
- **Offline edits reconciling on reconnect** — edit while offline (airplane mode),
  reconnect; the drain flushes and the item converges to the acked server version.

## Verification checklist

- [ ] `cd mobile && flutter analyze` is clean (no new errors or warnings).
- [ ] `cd mobile && dart format --set-exit-if-changed .` passes (or the touched files
      are formatted).
- [ ] `cd mobile && flutter test` — full suite green.
- [ ] `flutter pub get` resolves with `synchronized` added; `pubspec.lock` updated.
- [ ] App builds and launches; opening a shopping list still loads, mutates, and
      syncs items with instant tap feedback (task-design's "no user-visible change").
- [ ] All five manual concurrency scenarios pass (cache/DB/outbox agree afterward).
- [ ] Assumption #1 (dirty-/version-gating alone replace `_busy`) confirmed via the
      *edit-during-pull* and *rapid-toggle-during-sync* scenarios.
- [ ] Assumption #2 (only detail/sync/main call the relocated methods) re-confirmed:
      no remaining references to the removed repo methods (`grep` the repo).
- [ ] Logs at `INFO` are clean on the happy path (poll/drain fine-level only).

## Risks surfaced during planning

- **Risk:** `main_screen_recipes_tab_widget_test.dart` already stubs
  `listIdsWithOutbox()` on the **item-repository** mock, but `start()` will now call it
  on the **store**. If the stub isn't moved, `start()` throws on an unstubbed mock and
  the widget test fails at `setUp`.
  **Why it matters:** it's the only widget test touching this feature and is easy to
  miss — the failure surfaces as an unrelated main-screen test breaking.
  **Mitigation:** Step 4 explicitly moves the stub onto the store mock; the checklist
  runs that test file first in isolation.

- **Risk:** the store owns a private cache while `docs/mobile/standards/architecture.md`
  says a *repository* "may hold a cache + `ValueNotifier`" and services use
  `ValueNotifier<AsyncValue<T>>` (state-management standard). Placing a raw cache in a
  `*_service.dart` is a deliberate deviation (task-design Assumption #3).
  **Why it matters:** a reviewer applying the standards literally may flag it; left
  undocumented, the "store service" shape becomes an unexplained one-off.
  **Mitigation:** flagged in task-design as a standards suggestion — after merge,
  propose documenting the "store service" shape (or a `*_store.dart` suffix) in the
  naming/state-management standards, per the Standards Evolution note in `CLAUDE.md`.

- **Risk:** two live DB connections during a botched split — if the repository keeps
  `open()`/`dao` while the store also opens the DB, both point at
  `shopping_list_items.db` simultaneously.
  **Why it matters:** duplicate sqflite handles to one file can corrupt reads/writes.
  **Mitigation:** Step 3 removes `dao`/`open()` from the repository in the **same**
  commit the store's `open()` becomes the live path — never both at once.
