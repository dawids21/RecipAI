# T3 — Mobile Push Sync — Task Design

**Date:** 2026-07-02
**Status:** draft
**Task:** T3 in `tasks.md`
**Builds on:** `../hld.md` (HLD §2, §3), `plans/T2-task-design.md` (local store / outbox contract), `plans/T1-task-design.md` (write endpoints)

## Summary

T3 stands up an **app-level sync service** that drains T2's append-only outbox to
the item write endpoints **per list — each list drained strictly sequentially,
one entry at a time, oldest `seq` first, but different lists draining
independently** — using each item's **last-acked server version read at push
time** as the base. It reconciles accepts (adopt the returned version, drop the
entry, clear `dirty` when the item's queue empties), handles **412 by
cascade-discarding** the item's whole queue and rolling back to the winning value,
**folds the remaining 4xx into a gone / rejected / transient taxonomy**
(§Response classification), and **retries transient failures with backoff** before
entering a **per-list `failure`** status that shows a persistent bottom
**"retry"** banner on that list's screen. Rejection outcomes are emitted as
**events** the detail screen surfaces itself — **a toast appears only while that
list's screen is open**, and is dropped otherwise (the sync service holds no UI
handle). Bulk actions were already fanned out into per-item outbox entries by T2,
so they need no special push path. No polling, connectivity detection, or offline
indicator — those are T4.

Three shape changes fall out of the review of this design and are folded in:
**outbox update/create entries store the full item snapshot** (not deltas),
**push is per-list sequential** (lists drain concurrently; no cross-item
parallelism within a list; no per-item `failed`), and **non-412 4xx responses are
handled explicitly** so a permanent error can never wedge a list's queue.

## Components and responsibilities

All in `mobile/lib/features/shopping_list/`.

- **`ShoppingListSyncService`** (CREATE, `shopping_list_sync_service.dart`) — the
  app-level singleton that owns push. Responsibilities: the **event-kicked,
  per-list sequential drain** (coalesced with a re-check so a kick arriving
  mid-drain is never lost), a **per-list retry counter + backoff `Timer`**, a
  **per-list `SyncStatus`** notifier, a **broadcast `Stream<RejectionEvent>`** the
  view drains, and per-list **retry**. It calls `ShoppingListItemRepository` for
  both HTTP and store reconcile. Implements `WidgetsBindingObserver` to re-kick
  every list with pending entries on app resume. **UI-agnostic** — no
  `scaffoldMessengerKey`, no persistence or HTTP of its own; pure orchestration.
  Deps: `ShoppingListItemRepository`, `AuthService`.
- **`ShoppingListItemRepository`** (MODIFY, `shopping_list_item_repository.dart`) —
  becomes the single item data-access point, **local + remote**:
  - Gains an `http.Client` + `AppConfig.apiBaseUrl` and the three **HTTP** methods
    (`createItem` / `updateItem` / `deleteItem`), mirroring `ShoppingListRepository`'s
    header/auth style; each takes `idToken` and returns the parsed authoritative
    item, or maps the server status to a push outcome (§Response classification):
    `ItemVersionConflictException` on 412, `ItemDiscardedException` on gone/rejected
    4xx.
  - Implements the reserved T3 **store mutations** (`reconcileAck`,
    `reconcileDeleteAck`, `cascadeDiscard`, `discardItem`), each keeping **DB (one
    DAO transaction) and the in-memory cache + notifier coherent** — updating the
    notifier only when the list is resident (open), writing DB-only otherwise.
  - **Changes the outbox-write path**: `applyEdit` / `applyChecked` / `applyReorder`
    now append the **full field snapshot** (`name, quantity, unit, checked,
    position`), not a delta (see decision below).
- **`ShoppingListItemDao`** (MODIFY, `shopping_list_item_dao.dart`) — add
  `nextOutboxEntry(listId)` (lowest `seq` **for that list**, decoded),
  `listIdsWithOutbox()` (`SELECT DISTINCT list_id FROM outbox`, for the
  start/resume fan-out — the outbox lives only in the DB, so the cold cache can't
  supply it), `deleteOutboxEntry(seq)`, `deleteOutboxForItem(localId)`,
  `readItem(localId)`, `deleteItemRow(localId)`, and an **`OutboxEntry`** value
  type (seq + item id + list id + kind + decoded payload).
- **`ItemVersionConflictException`** (CREATE, in `shopping_list_item_repository.dart`)
  — carries the winning `ShoppingListItem` across the 412 boundary.
- **`ItemDiscardedException`** (CREATE, same file) — a **non-conflict,
  non-transient** push outcome with no winner to roll back to. Carries a `reason`
  (`gone` — 404 on create/update; `rejected` — 400/403) used for the toast copy +
  log; both reasons drive the same `discardItem` store path. (404-on-**delete** is
  still folded into success, not a discard — §Response classification.)
- **`RejectionEvent`** (CREATE, `shopping_list_sync_service.dart`) — the
  view-drained event: `listId`, `itemName`, and the outcome (`conflict` | `gone` |
  `rejected`) so the screen can choose toast copy.
- **`SyncStatus`** (CREATE, `shopping_list_sync_service.dart`) — `enum { syncing,
  notSyncing, failure }`, tracked **per list** (one notifier per list, created when
  the list is first loaded, default `notSyncing`). T4 extends the sync surface
  (e.g. `offline`) later.
- **`ShoppingListDetailScreen`** (MODIFY, `shopping_list_detail_screen.dart`) —
  render the **persistent bottom failure banner** with a **retry** button when
  **this list's** `syncStatus == failure` (replacing the `TODO(..., T3)` at line
  67), and, while mounted, **subscribe to the detail service's rejection stream**
  and show a `SnackBar` via its own `ScaffoldMessenger.of(context)` (a closed
  screen has no subscriber, so the event is simply dropped). No sync/offline
  indicator — that stays a T4 `TODO` (line 428).
- **`ShoppingListDetailService`** (MODIFY, `shopping_list_detail_service.dart`) —
  after each store mutation and on `openShoppingList`, call
  `syncService.requestDrain(listId)`; expose `ValueListenable<SyncStatus> get
  syncStatus` (→ `syncService.syncStatusFor(listId)`), `Stream<RejectionEvent> get
  rejections` (→ `syncService.rejections` filtered to this list's `listId`), and
  `Future<void> retrySync()` (→ `syncService.retry(listId)`) passthroughs. New dep:
  `ShoppingListSyncService`.
- **`shopping_list_setup.dart`** (MODIFY) — register `ShoppingListSyncService`
  (singleton, disposed) with its deps; call `start()` (initial fan-out drain +
  observer). No `scaffoldMessengerKey` wiring — the service is UI-agnostic, so
  `main.dart` needs no change for this feature.
- **`ShoppingListRepository`** (UNCHANGED) — item-write HTTP no longer lives here;
  the `TODO(shopping-list-items)` at line 152 is obsolete and removed.

## Interfaces and method signatures

### HTTP (on ShoppingListItemRepository) — maps to T1 endpoints

```dart
// POST /shopping-lists/{listId}/items  -> 201 ShoppingListItem (version 0)
Future<ShoppingListItem> createItem(String listId, OutboxPayload snapshot, String? idToken);

// PUT /shopping-lists/{listId}/items/{itemId}  -> 200 | 412 | 404 | 400/403
Future<ShoppingListItem> updateItem(String listId, String itemId,
    {required int baseVersion, required OutboxPayload snapshot}, String? idToken);

// DELETE /shopping-lists/{listId}/items/{itemId}?baseVersion=n -> 204 | 404 | 412 | 400/403
Future<void> deleteItem(String listId, String itemId, int baseVersion, String? idToken);
```

`snapshot` = the outbox entry's stored full field set (`name, quantity, unit,
checked, position`); `createItem` omits `checked` (T1 defaults it false).

#### Response classification

Each method maps the server's status to one of four push outcomes; the drain acts
on them (§Data flow, §Pseudo-code):

| Status | Outcome | Signalled as |
|---|---|---|
| 201 / 200 / 204 | **ack** | returns the parsed item (create/update) / normally (delete) |
| **412** | **conflict** — roll back to winner | throw `ItemVersionConflictException(winner)` |
| **404** on create/update | **gone** — a delete already won, row removed server-side | throw `ItemDiscardedException(gone)` |
| **404** on delete | **ack** — absence *is* the delete's goal | returns normally |
| **400 / 403** | **rejected** — validation error / lost editor access; can never succeed | throw `ItemDiscardedException(rejected)` |
| **401 / 408 / 429 / 5xx**, network, timeout | **transient** | generic throw the sync service retries |

401 is deliberately **transient**, not `rejected`: `AuthService` refreshes
internally, so a 401 reaching us may still clear on a later session and must not
discard the user's edit. 400/403 are permanent, so their entries are **discarded**
rather than retried — otherwise one would wedge that list's sequential queue
forever.

### Store mutations (on ShoppingListItemRepository) — DB + cache coherent

```dart
Future<OutboxEntry?> nextOutboxEntry(String listId);       // dao: lowest seq for the list, or null
Future<List<String>> listIdsWithOutbox();                  // dao: distinct lists with pending entries
Future<LocalShoppingListItem?> readItem(String localId);   // dao: serverId + lastAckedVersion for push

// accept (create/update): adopt serverId + version, drop the acked entry,
// clear dirty iff no entries remain for the item. Does NOT overwrite fields.
Future<void> reconcileAck(String localId, ShoppingListItem winner, int ackedSeq);

// 204 / 404 delete: hard-remove the row, drop the delete entry.
Future<void> reconcileDeleteAck(String localId, int ackedSeq);

// 412: overwrite local item with winner (un-tombstone), adopt serverId+version,
// drop EVERY outbox entry for the item, clear dirty.
Future<void> cascadeDiscard(String localId, ShoppingListItem winner);

// gone (404 create/update) / rejected (400/403): hard-remove the row and drop
// EVERY outbox entry for the item. No winner to roll back to.
Future<void> discardItem(String localId);
```

### Sync service (orchestration)

```dart
class ShoppingListSyncService with WidgetsBindingObserver {
  ShoppingListSyncService({ required ShoppingListItemRepository itemRepository,
      required AuthService authService });

  ValueListenable<SyncStatus> syncStatusFor(String listId); // lazily created (notSyncing) on first load
  Stream<RejectionEvent> get rejections;                    // broadcast; view-drained, dropped if unheard
  void requestDrain(String listId);   // coalesced per-list kick (append / openList / resume / backoff / retry)
  Future<void> retry(String listId);  // reset that list's retry counter, requestDrain (from its failure banner)
  void start();                       // addObserver + drain every list with pending entries
  void dispose();                     // cancel timers, removeObserver, dispose notifiers + stream
  // internal per-list: _drain(listId), _drainPass(listId), _pushOne(OutboxEntry)
}
```

## Data flow

**Accepted update (representative):**

1. A local edit (T2) appends an outbox entry carrying the **full snapshot**; the
   detail service calls `requestDrain(listId)`.
2. `_drain(listId)` loops: `nextOutboxEntry(listId)` → the oldest entry **for that
   list**. `readItem` supplies the item's live `serverId` + `lastAckedVersion`.
3. `updateItem` sends the **entry's snapshot** with `baseVersion =
   item.lastAckedVersion`.
4. 200 → `reconcileAck`: adopt `winner.version` as `lastAckedVersion` (+ `serverId`
   from `winner.id`), drop this entry, clear `dirty` iff the item has no more
   entries. **Fields are not overwritten** — later queued edits already advanced
   them. Cache+notifier update if the list is open.
5. The loop takes the next entry. When `nextOutboxEntry(listId)` returns null —
   and no kick arrived mid-drain (coalescing re-check) — the list's status →
   `notSyncing`.

**Start / resume fan-out:** `start()` and app-resume call `listIdsWithOutbox()`
and `requestDrain(listId)` for each, so offline edits to a list the user isn't
currently viewing still flush (HLD §3). Different lists drain **concurrently**;
each list is internally sequential.

**Create-then-edit / create-then-delete (not-yet-acked item):** the create has the
lowest `seq` for the list, so per-list sequential order processes it first; its ack
sets `serverId` + `lastAckedVersion` before any later same-item entry is read.
Create-then-delete pushes **both** (POST then DELETE) — no local collapse.

**412 (reject) → cascade-discard:** `updateItem`/`deleteItem` throws
`ItemVersionConflictException(winner)` → `cascadeDiscard(localId, winner)`
overwrites the local item with the winning value (un-tombstoning a rejected
delete so it reappears), adopts the winner's version, drops **every** queued entry
for that item; emit a `RejectionEvent(conflict)`. **Draining continues** to the
next entry.

**Gone / rejected (non-412 4xx) → discard:** a 404 on create/update (a delete
already won, row gone) or a 400/403 throws `ItemDiscardedException(reason)` →
`discardItem(localId)` hard-removes the row and drops **every** queued entry for
that item; emit a `RejectionEvent(gone|rejected)` and log at error level.
**Draining continues** — a permanent error is retired, never allowed to wedge the
queue.

**Rejection delivery:** every `RejectionEvent` goes onto the broadcast stream. The
detail screen for that `listId`, if open, renders a `SnackBar`; if no screen is
open the event has no subscriber and is dropped (the store already rolled the item
back, so reopening the list shows the correct value).

**Delete 204/404:** both succeed → `reconcileDeleteAck` (hard-remove row + drop
entry).

**Transient failure (network / 401 / 408 / 429 / 5xx / timeout):** the head entry
blocks **that list's** sequential queue only. Bump the **list's** retry counter and
arm a one-shot backoff `Timer` that re-kicks `requestDrain(listId)`; after the cap
the **list's** status becomes **`failure`** and its detail screen shows the
persistent bottom banner. **Retry** (button → `retry(listId)`, app resume, or a
fresh edit) resets the counter and re-drains from the head. Any successful push
resets it. Other lists are unaffected.

## Pseudo-code

```
start() / onResume():                            # fan out over all pending lists
  for listId in await repo.listIdsWithOutbox(): requestDrain(listId)

requestDrain(listId):                            # coalesced, per list
  if listId in _draining:
    _pending.add(listId)                         # re-checked before _drain exits
    return
  _drain(listId)                                 # fire-and-forget

_drain(listId):
  _draining.add(listId); _setStatus(listId, syncing)
  try:
    do:
      _pending.remove(listId)
      if not await _drainPass(listId):           # false => stalled on transient
        return                                    # backoff/failure already set
    while listId in _pending                      # a kick arrived mid-drain -> loop again
    _setStatus(listId, notSyncing)                # queue drained
  finally:
    _draining.remove(listId)

_drainPass(listId) -> bool:                       # true = drained empty, false = stalled
  while (entry = await repo.nextOutboxEntry(listId)) != null:
    try:
      await _pushOne(entry)                        # sequential, one HTTP call
      _retry[listId] = 0                           # progress resets backoff
    on ItemVersionConflictException(winner):
      await repo.cascadeDiscard(entry.itemLocalId, winner)
      _emit(RejectionEvent(listId, winner.name, conflict))
    on ItemDiscardedException(reason):             # gone (404) / rejected (400,403)
      await repo.discardItem(entry.itemLocalId)
      log.error(reason, entry)
      _emit(RejectionEvent(listId, entry.name, reason))
    on _ (transient):                              # network / 401 / 408 / 429 / 5xx
      if ++_retry[listId] <= MAX_RETRIES:
        _armBackoffTimer(listId, _retry[listId])   # Timer -> requestDrain(listId)
      else:
        _setStatus(listId, failure)                # persistent banner on this list
      return false                                 # head blocks this list; stop the pass
  return true

_pushOne(entry):
  item = await repo.readItem(entry.itemLocalId)    # serverId + lastAckedVersion
  token = await authService.idToken
  switch entry.kind:
    create: w = await repo.createItem(entry.listId, entry.payload, token)
            await repo.reconcileAck(entry.itemLocalId, w, entry.seq)
    update: w = await repo.updateItem(entry.listId, item.serverId,
                    baseVersion: item.lastAckedVersion, snapshot: entry.payload, token)
            await repo.reconcileAck(entry.itemLocalId, w, entry.seq)
    delete: await repo.deleteItem(entry.listId, item.serverId, item.lastAckedVersion, token)
            await repo.reconcileDeleteAck(entry.itemLocalId, entry.seq)   # 204|404

reconcileAck(localId, winner, ackedSeq):          # store, one txn
  item = read(localId)
  deleteOutboxEntry(ackedSeq)
  remaining = outboxForItem(localId)              # after the delete
  write item.copyWith(serverId: winner.id, lastAckedVersion: winner.version,
                      dirty: remaining.nonEmpty)  # NB: fields NOT overwritten
  if listResident(item.listId): refreshNotifier(item.listId)

discardItem(localId):                             # store, one txn (gone / rejected)
  item = read(localId)
  deleteItemRow(localId); deleteOutboxForItem(localId)
  if listResident(item.listId): refreshNotifier(item.listId)
```

## Decisions made

- **Outbox update/create entries store the full item snapshot, not deltas**
  *(settled with user)* — building the PUT from the *current* item would leak
  values from **later** queued edits into an earlier push (the double-edit bug).
  A self-contained snapshot per entry pushes exactly the state as-of that edit.
  Requires changing T2's `applyEdit`/`applyChecked`/`applyReorder` to snapshot all
  mutable fields. The base version is still read **live** at push time (HLD §0.6),
  never frozen into the entry.
- **Per-list sequential push; lists drain independently** *(revised)* — each list
  drains **one entry at a time in `seq` order** (preserving per-item FIFO within
  the list), but **different lists drain concurrently**, keyed by `listId`. A
  per-list `_draining` guard replaces the in-flight set. This scopes head-of-line
  blocking (a stalled or permanently-failing entry) to its **own** list instead of
  freezing sync for every list, and is why the drain, retry counter, backoff timer,
  and `SyncStatus` are all per-list. Still a deliberate deviation from HLD §2.2's
  cross-*item* parallelism (no parallelism *within* a list), acceptable at 30–40
  items per list.
- **Coalesced drain kicks are never lost** *(added in review)* — a
  `requestDrain(listId)` arriving while that list is draining sets a `_pending`
  marker that `_drain` re-checks before it clears `_draining` and reports
  `notSyncing`, so an edit committed just as the previous pass finishes is still
  flushed (closes a lost-wakeup race).
- **Per-list `SyncStatus`; no per-item `failed`** *(revised)* — status is tracked
  per list (notifier created when the list is first loaded, default `notSyncing`),
  so a list's failure banner reflects **that list's** queue, not an unrelated
  list's. A transient failure stalls only the affected list's queue, so per-item
  failure still has no meaning and the T2 `items.failed` column stays unused.
  `failure` drives the per-list persistent banner (matches HLD §2.4's "no per-item
  marker"); `retry(listId)` resumes that list's stalled queue.
- **Item-write HTTP lives on `ShoppingListItemRepository`** *(settled with user)* —
  it becomes the one item data-access point (local + remote), so the sync service
  depends on a single repository; `ShoppingListRepository` is untouched.
- **Accept adopts version only, never overwrites local fields** — later queued
  edits have already advanced local state; 412 is the only path that overwrites
  fields (roll back to winner).
- **`deleteItem` folds 404 into success; create/update 404 is a discard**
  *(refined)* — a delete's goal is "item absent server-side," which a 404 already
  satisfies, so 204 and 404 both lead to `reconcileDeleteAck`. A 404 on
  **create/update**, by contrast, means a delete already won and removed the row
  (first-action-wins, req §2.6) — so it is an `ItemDiscardedException(gone)` that
  drops the item locally, not a success. (This reintroduces a gone signal for
  create/update that an earlier draft had dropped.)
- **Non-412 4xx handled explicitly — gone / rejected / transient** *(added in
  review)* — see §Response classification. The invariant: a **permanent** error
  (404 gone, 400/403 rejected) **retires its entry** via `discardItem` so it can
  never wedge the list's sequential queue; only genuinely **transient** classes
  (network, 401, 408, 429, 5xx) retry. Without this, one poison entry — e.g. an
  edit queued against a list the user was unshared from — would block that list's
  queue forever.
- **Rejection outcomes are view-drained events, scoped to the open screen**
  *(added in review; supersedes the earlier `scaffoldMessengerKey` plan)* — the
  sync service emits `RejectionEvent`s on a broadcast `Stream` and holds **no UI
  handle**; the detail screen, while mounted, subscribes and renders the `SnackBar`
  via its own `ScaffoldMessenger.of(context)`. A closed screen has no subscriber,
  so the event is dropped — the toast appears **only** on the offending item's own
  open list, with no `setActiveList` bookkeeping or global messenger key. This is
  the layering HLD §3 prescribes ("sync service emits notifications; the view
  surfaces toasts") and is symmetric with the view-rendered failure banner.
- **The push still reads the item row at push time — via a plain DAO `readItem`,
  not a cache-vs-DB branch.** The read itself is required: the push needs the
  item's live `serverId` (PUT/DELETE target, only set after the create-ack) and
  `lastAckedVersion` (the base version, which advances as earlier same-item
  entries ack) — neither can be frozen into the outbox entry. What's *not* needed
  is the cache fallback: the item row and its outbox entry commit in one
  transaction, so the DB is always current. Cache coherence therefore stays only
  on the reconcile *writes*, which update the notifier for a resident (open) list
  and write DB-only otherwise.
- **Event-kicked drain, no periodic poll** *(settled with user)* — triggers:
  outbox append, `openList`/app-start, app-resume, backoff timer, retry button.
  App-start/resume fan out over `listIdsWithOutbox()`; the rest kick a specific
  list. T4's poller/reconnect will call the same `requestDrain(listId)`.
- **Both sync surfaces are view-rendered** — the per-list persistent failure banner
  from `syncStatusFor(listId)` (state) and rejection toasts from the broadcast
  `rejections` stream (events). The sync service exposes state/events; the screen
  owns all rendering (see the rejection-events decision above). Replaces the
  earlier hybrid plan that pushed toasts through a global `scaffoldMessengerKey`.
- **Create-then-delete pushes both in FIFO** *(settled with user)* — POST then
  DELETE, no local collapse; the create's ack establishes the id/version the
  delete builds on (resolves the `tasks.md` T3 risk).

## Assumptions to verify

- **Assumption:** T1's 412 body is the **raw** winning `ShoppingListItem` JSON
  (id/name/quantity/unit/checked/position/version), parseable by
  `ShoppingListItem.fromJson`.
  **If wrong:** the 412 branch can't build `winner`; adjust parsing / cascade input.
- **Assumption:** create returns the item `id` and `version: 0` in its 201 body
  (per T1's assumption), so `reconcileAck` can set both `serverId` and
  `lastAckedVersion`.
  **If wrong:** the first same-item update/delete has no valid base; the FIFO
  ordering guarantee breaks.
- **Assumption:** changing T2's already-shipped outbox-write path to store full
  snapshots is in scope for T3 and no other consumer depends on the delta shape.
  **If wrong:** the snapshot change needs coordinating back into T2 / re-verifying
  the T2 restart tests.
- **Assumption:** per-list sequential push (a transient error stalling **one
  list's** queue until retry, other lists unaffected) is acceptable UX at 30–40
  items per list.
  **If wrong:** reintroduce per-item isolation within a list (in-flight set +
  per-item failure).
- **Assumption:** T1 returns **400/403** (not 5xx) for validation / lost-editor
  errors, and **404** for update/create against a deleted item/list, so the
  gone/rejected/transient split holds.
  **If wrong:** re-map the offending status in §Response classification.
- **Assumption:** dropping a rejection toast when the item's list screen is closed
  (the store has already rolled back) is acceptable — no queued/deferred
  notification is needed.
  **If wrong:** persist unseen rejections and surface them on next open.
- **Assumption:** `AuthService.idToken` (a `Future<String?>`) is safe to fetch per
  push and handles refresh internally.
  **If wrong:** fetch once per drain pass or cache/refresh in the loop.

## Required reading for implementation planning

- `plans/T2-task-design.md` §3–§6 — the store, outbox entry shape (to change to
  full snapshots), tombstone rule, and the last-acked-version / dirty contract.
- `plans/T1-task-design.md` (Endpoints + DTOs + Decisions) — request/response
  shapes, the 412 raw-winner body, DELETE `baseVersion` query param, create
  version 0.
- `mobile/lib/features/shopping_list/shopping_list_item_repository.dart` and
  `shopping_list_item_dao.dart` — the T2 store/DAO to extend (cache+notifier
  coherence, transaction helpers, `OutboxKind`, `applyXxx` write path).
- `mobile/lib/features/shopping_list/shopping_list_repository.dart` — HTTP/auth
  header pattern to mirror for the new item-write methods.
- `mobile/lib/features/shopping_list/shopping_list_detail_screen.dart` — where the
  failure banner mounts (the `TODO(..., T3)` at line 67), how services are wired,
  and the `StatefulWidget` lifecycle to hang the rejection-stream subscription +
  `ScaffoldMessenger.of(context)` snackbar off (subscribe in `initState`, cancel in
  `dispose`).
- `docs/mobile/standards/{state-management,architecture,dependency-injection}.md`
  — `ValueNotifier` state, the repository-may-hold-state / HTTP-and-storage rule,
  singleton registration/injection.
- `../hld.md` §2.2 (single-change flow), §2.4 (failure surface), §3 (layer
  responsibilities & lifecycle) — the behavior contract, noting the deliberate
  sequential-push deviation from §2.2's parallelism.
