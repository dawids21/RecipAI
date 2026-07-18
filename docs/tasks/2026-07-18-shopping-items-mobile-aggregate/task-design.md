# Shopping-List Items — Serialised Store Aggregate — Task Design

**Date:** 2026-07-18
**Status:** final

## Summary

Split today's `ShoppingListItemRepository` into a thin HTTP repository and a new
`ShoppingListItemStoreService` that owns the local aggregate (in-memory cache,
per-list `ValueNotifier`s, DAO/outbox coordination). Every local read-modify-write
runs inside a **per-list `synchronized` `Lock`**, so a UI mutation can no longer
interleave with a reconcile's transaction `await` and clobber the cache write-back.
The sync service's `_busy`/`_pending` gate collapses to a bare single-flight-drain
guard; poll and drain reconciles now run through the store, serialised per list by
its lock, and stay correct via the **unchanged** dirty-/version-gating.

## Components and responsibilities

- **`ShoppingListItemStoreService`** (CREATE, `mobile/lib/features/shopping_list/shopping_list_item_store_service.dart`)
  — the consistency boundary. Owns `_dao`, `_cache`
  (`Map<listId, Map<localId, LocalShoppingListItem>>`), `_notifiers`
  (`Map<listId, ValueNotifier<List<LocalShoppingListItem>>>`), and a
  `Map<listId, Lock>`. Hosts every local mutation and reconcile relocated from the
  repository, each serialised per list. Hosts the relocated async `open()` factory
  (opens the DB, builds the DAO). Service-layer per the decision below, but holds a
  raw cache + per-list notifiers rather than `ValueNotifier<AsyncValue<T>>`.
- **`ShoppingListItemRepository`** (MODIFY, `.../shopping_list_item_repository.dart`)
  — reduced to the item HTTP endpoints (`fetchServerItems`, `createItem`,
  `updateItem`, `deleteItem`) plus the shared `OutboxPayload` and exception types.
  Loses `_dao`, `_cache`, `_notifiers`, `_uuid`, the relocated `apply*`/`reconcile*`
  methods, the store-read passthroughs, and `open()`. Keeps its own `http.Client`.
- **`ShoppingListSyncService`** (MODIFY, `.../shopping_list_sync_service.dart`)
  — depends on **both** the store service (reconciles + store reads) and the HTTP
  repository (network calls). `_busy`/`_pending`/`_canReconcile` are replaced by a
  `_draining`/`_pending` single-flight-drain guard; `_poll` is no longer gated.
- **`ShoppingListDetailService`** (MODIFY, `.../shopping_list_detail_service.dart`)
  — watches and mutates through the store service; `deleteAllChecked`/`uncheckAll`
  delegate to the store's atomic batch methods; the obsolete `_busy`-ordering
  comment in `openShoppingList` is removed.
- **`setupShoppingList`** (MODIFY, `.../shopping_list_setup.dart`) — registers the
  new store service, wires the repository, sync service, and detail service to it.
- **`main.dart`** (MODIFY, `mobile/lib/main.dart:43`) — awaits
  `ShoppingListItemStoreService.open()` and passes it into `setupShoppingList`.
- **`test/support/mocks.dart`** (MODIFY) — add `MockShoppingListItemStoreService`.
- **`pubspec.yaml`** (MODIFY) — add the `synchronized` dependency.

## Interfaces and method signatures

### `ShoppingListItemStoreService` (new)

```
class ShoppingListItemStoreService:
    static Future<ShoppingListItemStoreService> open()      # opens DB, builds DAO
    ShoppingListItemStoreService({required ShoppingListItemDao dao})

    # ── lifecycle / reads (lock-free) ──
    Future<void> openList(String listId)                     # locked: hydrate + notifier
    ValueListenable<List<LocalShoppingListItem>> watch(String listId)   # sync getter
    Future<OutboxEntry?> nextOutboxEntry(String listId)      # lock-free (head-stable)
    Future<List<String>> listIdsWithOutbox()                 # lock-free
    Future<LocalShoppingListItem?> readItem(String localId)  # lock-free DB read

    # ── UI mutations (locked-public → unlocked-private) ──
    Future<void> applyCreate(String listId, {name, quantity, unit, afterLocalId})
    Future<void> applyEdit(String listId, String localId, {name, quantity, unit})
    Future<void> applyChecked(String listId, String localId, bool checked)
    Future<void> applyReorder(String listId, String localId, double newPosition)
    Future<void> applyDelete(String listId, String localId)

    # ── bulk (single lock acquisition, one atomic section) ──
    Future<void> deleteAllChecked(String listId)
    Future<void> uncheckAll(String listId)

    # ── reconcile a pull / push outcome (locked) ──
    Future<void> reconcileFromServer(String listId, List<ShoppingListItem> items)
    Future<void> reconcileAck(String listId, String localId, ShoppingListItem winner, int ackedSeq)
    Future<void> reconcileDeleteAck(String listId, String localId, int ackedSeq)
    Future<void> cascadeDiscard(String listId, String localId, ShoppingListItem winner)
    Future<void> discardItem(String listId, String localId)

    void dispose()                                           # disposes notifiers

    # ── private unlocked cores (run only inside _lockFor(listId)) ──
    Lock _lockFor(String listId)                             # putIfAbsent per list
    void _createItem(...); void _editItem(...); void _checkItem(...)
    void _reorderItem(...); void _deleteItem(...)            # cache+DB+outbox+notifier
```

Signature change vs. today: `apply*` and `reconcile*` now take `listId`
explicitly (the callers — detail service via `_openListId`, sync service via
`entry.listId` — already have it), so the lock can be selected without the
cache-scan `firstWhere` the old repository used. `reconcile*` still resolves
whether the item is *resident* internally (a list may be closed), but uses the
passed `listId` for lock selection.

### `ShoppingListItemRepository` (reduced)

```
class ShoppingListItemRepository:
    ShoppingListItemRepository({http.Client? client})       # no DAO, no open()
    Future<List<ShoppingListItem>> fetchServerItems(String listId, String? idToken)
    Future<ShoppingListItem> createItem(String listId, OutboxPayload s, String? idToken)
    Future<ShoppingListItem> updateItem(String listId, String itemId, {baseVersion, snapshot, idToken})
    Future<void> deleteItem(String listId, String itemId, int baseVersion, String? idToken)
    void dispose()                                          # closes http.Client
```

`OutboxPayload`, `ShoppingListNetworkException`, `ItemVersionConflictException`,
`ItemDiscardedException`, `DiscardReason` stay in this file (the HTTP methods use
them; the sync service imports them from here).

### `ShoppingListSyncService` (gate reworked)

```
final _draining = <String>{};   # at most one drain loop per list
final _pending  = <String>{};   # a kick arriving mid-drain, coalesced

void requestDrain(String listId)          # if _draining: _pending.add; else _drain
Future<void> _poll(String listId)         # ungated; fetch (repo) → reconcile (store)
Future<void> _drain(String listId)        # guarded by _draining; loops on _pending
```

`_canReconcile` and the `_busy` set are deleted. Constructor gains
`required ShoppingListItemStoreService store`.

## Data flow

**UI mutation (e.g. check):**
1. View → `DetailService.toggleChecked(localId, checked)`.
2. → `store.applyChecked(_openListId!, localId, checked)`.
3. `applyChecked` acquires `_lockFor(listId)`, runs `_checkItem`: update cache
   entry, bump notifier, `await` one DAO transaction (upsert row + append outbox),
   releases the lock. The whole cache+DB+outbox+notifier write-back is inside the
   lock — no reconcile can splice into the `await`.
4. `DetailService` calls `syncService.requestDrain(listId)`.

**Poll (pull):**
1. `_poll(listId)` — **no gate** — `token = auth.idToken`.
2. `repo.fetchServerItems(listId, token)` — **HTTP, outside any lock**.
3. `store.reconcileFromServer(listId, items)` — acquires `_lockFor(listId)`,
   runs the version-/dirty-gated diff in one transaction, writes cache + notifier,
   releases.

**Drain (push):** guarded so only one loop runs per list:
1. `store.nextOutboxEntry(listId)` (lock-free head read) → `entry`.
2. `store.readItem(entry.itemLocalId)` (lock-free) → serverId/version.
3. `repo.createItem|updateItem|deleteItem(...)` — **HTTP, outside the lock**.
4. On the outcome: `store.reconcileAck | reconcileDeleteAck | cascadeDiscard |
   discardItem(listId, localId, …)` — **locked** per list.

**Why poll-alongside-drain is safe without `_busy`** (load-bearing): an item with
a pending push is `dirty`, so a poll's `reconcileFromServer` skips it (dirty-gate);
once the ack lands (`reconcileAck`, locked) it advances `lastAckedVersion`, and a
subsequently-processed stale server value is rejected by the version-gate
(`s.version >= local.lastAckedVersion`). Reconciles never overlap because they
share the per-list lock; between them, only lock-free HTTP runs.

## Pseudo-code

**Per-list lock + locked-public / unlocked-private split**

```
Lock _lockFor(listId) => _locks.putIfAbsent(listId, () => Lock())

# public: locks, delegates to the unlocked core
applyChecked(listId, localId, checked):
    return _lockFor(listId).synchronized(() => _checkItem(listId, localId, checked))

# private: NO lock — only ever called while _lockFor(listId) is held
_checkItem(listId, localId, checked):
    item    = _cache[listId][localId]
    updated = item.copyWith(checked: checked, dirty: true)
    _cache[listId][localId] = updated
    _notifiers[listId].value = _visibleItems(listId)          # instant feedback
    await _dao.transaction(txn):
        upsertItemTxn(txn, updated)
        appendOutboxTxn(txn, localId, listId, update, snapshot(updated))

# bulk: ONE lock acquisition for the whole batch, loops the unlocked core
uncheckAll(listId):
    return _lockFor(listId).synchronized(() async:
        for item in _cache[listId].values where item.checked:
            await _checkItem(listId, item.localId, false))       # not applyChecked
```

`reconcile*` methods take the lock and inline their body (nothing composes them,
so they need no unlocked twin). The cache write-back that today sits *after* the
transaction now sits *inside* the same locked section as the pre-read, closing the
race the ADR describes.

**Sync-service drain guard (replaces `_busy`/`_pending`/`_canReconcile`)**

```
requestDrain(listId):
    if _draining.contains(listId): _pending.add(listId); return
    unawaited(_drain(listId))

_drain(listId):
    _draining.add(listId); setStatus(syncing)
    try:
        do:
            _pending.remove(listId)
            drainedEmpty = await _drainPass(listId)   # unchanged body
            if !drainedEmpty: return                   # stalled: backoff/offline set
        while _pending.contains(listId)
        setStatus(notSyncing)
    finally:
        _draining.remove(listId)

_poll(listId):                # no _canReconcile guard anymore
    try: fetch (repo) ; reconcileFromServer (store) ; clear offline status
    catch network → offline ; catch other → warn, store untouched
```

## Decisions made

- **Serialiser = `synchronized` package, `Map<listId, Lock>`.** Per-list
  granularity (unrelated lists stay concurrent, per HLD/ADR); tekartik author,
  same family as sqflite/path_provider already in use.
- **Composition = locked-public / unlocked-private split (non-reentrant lock).**
  Public `apply*` lock and delegate to unlocked `_verb` cores; bulk `deleteAllChecked`/
  `uncheckAll` take the lock **once** and loop the cores, making each bulk op one
  atomic section. Naming per the author's preference: locked `applyDelete` →
  unlocked `_deleteItem` (no `Unlocked` suffix).
- **Bulk ops move into the store and read the checked set under the lock.** The
  "which items are checked" read becomes part of the atomic section, instead of the
  detail service snapshotting `_items.value` and looping public calls.
- **`apply*`/`reconcile*` take `listId` explicitly.** Callers already have it;
  removes the old cache-scan `firstWhere` and gives the lock its key directly.
- **`_busy` fully removed, not just its store half.** Dirty-/version-gating (kept
  verbatim) covers pull-vs-in-flight-push; only a single-flight-drain guard remains
  so two drains can't double-push the same outbox head.
- **Outbox head + `readItem` reads stay lock-free.** The outbox is append-only
  (appends take higher `seq`; the head is deleted only by the single active drain's
  locked reconcile), and sqflite serialises the row read — so neither races a
  mutation incoherently.
- **`open()` relocates to the store service.** It owns the DAO now; the HTTP
  repository becomes synchronously constructible.
- **Store is service-layer (`*_service.dart`).** Chosen to honour the requirements'
  "new dedicated service" wording and avoid a new filename suffix, accepting that it
  holds a raw cache + notifiers rather than `AsyncValue<T>` app state.
- **Testing = manual scenarios only** (see HLD *Concurrency confidence*); no
  automated interleave harness this task, matching the acceptance criteria.

## Assumptions to verify

- **Assumption:** dirty-gating + version-gating alone keep a poll's reconcile from
  regressing an in-flight push, so removing `_busy` entirely is safe.
  **If wrong:** a poll landing mid-push could adopt a stale server value; would need
  a narrow logical gate re-added. Verify via the *edit-during-pull* and
  *rapid-toggle-during-sync* manual scenarios.
- **Assumption:** the only callers of the relocated methods and of
  `ShoppingListItemRepository.open()` are the detail service, sync service, and
  `main.dart` (grep-confirmed; tests use mocks).
  **If wrong:** an un-migrated caller breaks compilation or bypasses the store.
- **Assumption:** a service that owns a raw cache + per-list notifiers is an
  acceptable deviation from the state-management standard's `ValueNotifier<AsyncValue<T>>`
  service shape.
  **If wrong:** revisit as a repository-layer `*_store.dart` and add that suffix to
  the naming standard. *(Standards suggestion: document the "store service" shape.)*
- **Assumption:** adding the `synchronized` dependency is acceptable and it behaves
  as documented in a single-isolate Flutter app.
  **If wrong:** fall back to the in-repo `Future`-chain serialiser
  (`core/logging/app_log_sink.dart`), which forces the same private-core discipline.

## Required reading for implementation planning

- `mobile/lib/features/shopping_list/shopping_list_item_repository.dart` — the
  source being split; exact `apply*`/`reconcile*` bodies to relocate verbatim.
- `mobile/lib/features/shopping_list/shopping_list_sync_service.dart` — the
  `_busy`/`_pending`/`_poll`/`_drain` code the guard rework touches.
- `mobile/lib/features/shopping_list/shopping_list_detail_service.dart` — mutation
  call sites and the bulk ops to delegate.
- `mobile/lib/features/shopping_list/shopping_list_item_dao.dart` — DAO surface the
  store delegates to; confirms outbox append-only.
- `mobile/lib/core/logging/app_log_sink.dart` — the fallback `Future`-chain
  serialiser pattern (assumption #4).
- `mobile/lib/features/shopping_list/shopping_list_setup.dart` & `mobile/lib/main.dart`
  — DI + `open()` wiring to update.
- `docs/ADRs/0004-shopping-list-item-store-aggregate.md` — the fixed decisions
  (per-list, local-only section, `_busy` collapse) not to reopen.
- `docs/mobile/standards/architecture.md` & `docs/mobile/standards/state-management.md`
  — the layering/service-shape rules the store-service choice bends.
