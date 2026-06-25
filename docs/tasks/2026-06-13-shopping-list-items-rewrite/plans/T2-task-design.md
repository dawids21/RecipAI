# T2 — Mobile Local Store + Offline Detail Screen — Design

**Date:** 2026-06-25
**Status:** draft
**Task:** T2 in `tasks.md`
**Builds on:** `../hld.md` (HLD), `../requirements.md`

T2 delivers an **entirely offline** experience: open a list, add / edit /
check-uncheck / delete / reorder items instantly, everything surviving an app
restart. **No server communication happens in T2** — push is T3, pull is T4. T2's
job is the local store, the outbox, and the detail screen on top of them, plus
**pinning down the last-acked-version / dirty contract** that T3 and T4 depend on
(`tasks.md` cross-task note).

---

## 0. Decisions locked in this design

Settled with the user; the rest of the document assumes them.

1. **In-memory cache + write-through DB (the "Option A" store).** The store keeps
   items in memory as the source of truth, persists every mutation through to the
   DB, and exposes a `ValueNotifier` the UI rebuilds from. The DB is **durability
   only** — read on open / restart, never queried reactively. Reactivity is
   library-agnostic and lives in memory. Both the T2 detail service and the future
   T3 sync service mutate **the one shared store**; Dart's single-isolate event
   loop makes each synchronous read-modify-write atomic, so **no locking is
   required** — only (a) keeping each mutation synchronous up to the notify and
   (b) serialising the DB write-through for consistent durability ordering.

2. **sqflite** backs persistence — native SQLite, relational `items` + `outbox`
   tables, no codegen. Path obtained via `path_provider` (already a dependency).

3. **Stable client `localId` as primary key.** Every item gets a client-generated
   UUID at creation that is its key for life; `serverId` is a separate nullable
   column filled on the first create-ack (T3). The outbox and UI reference the
   stable `localId`, so the T3 remap is a one-field update — no references rewritten.

4. **The stateful store lives inside the item repository.** The in-memory cache +
   `ValueNotifier` sit in `ShoppingListItemRepository`, matching HLD §3's wording.
   The architecture standard will be revised so a repository **need not be
   stateless** — the rule that matters is that a repository **provides raw data to
   services and holds no business logic**, which this repository honours. See §14
   (Standards impact).

Two further design choices follow from the above rather than being independently
open; they are documented where they arise:

- **Local delete is a tombstone, not a hard row removal** (§6) — forced by HLD's
  "base version is read from the item at push time": the row must survive locally
  until its delete is acked.
- **Outbox kinds = create / update / delete, append-only** (§5).

---

## 1. Component overview

The detail screen follows the project's **Repository → Service → View** flow:

```
View          ShoppingListDetailScreen
                 ValueListenableBuilder over the detail service
                    │  user actions (add/edit/check/delete/reorder/bulk)
                    ▼
Service       ShoppingListDetailService
                 owns UI-facing item state (active / Done sections)
                 maps store changes → AsyncValue<...>; routes actions to the store
                    │
                    ▼
Repository    ShoppingListItemRepository   ← stateful (Option A store)
                 in-memory cache  (Map<listId, Map<localId, LocalShoppingListItem>>)
                 ValueNotifier per open list
                 write-through  ───────────────┐
                                                ▼
                              ShoppingListItemDao  (sqflite CRUD, stateless)
                                 items table  +  outbox table
```

- The **DAO** is pure persistence: synchronous-style sqflite reads/writes, no
  state, no business logic. (It is the only stateless part; the repository wraps it.)
- The **repository** is the Option-A store: holds the in-memory truth, exposes a
  `ValueListenable` per open list, applies every mutation to memory + DAO + notifier.
- The **detail service** turns the store's flat item list into the screen's
  active/Done sections and translates user gestures into store calls.
- The existing `ShoppingListRepository` / `ShoppingListDetailService` (list rename,
  share, delete) are untouched except for wiring the new item path in (§9, §12).

---

## 2. Persistence schema (sqflite)

One database, opened once at startup, schema version 1. Two tables.

**`items`**

| Column | Type | Notes |
|---|---|---|
| `local_id` | TEXT PK | client UUID, stable for life (decision §0.3) |
| `server_id` | TEXT NULL | set on first create-ack (T3); null while unsynced |
| `list_id` | TEXT NOT NULL | indexed; groups items per list |
| `name` | TEXT | |
| `quantity` | REAL NULL | |
| `unit` | TEXT NULL | |
| `checked` | INTEGER | 0/1 |
| `position` | REAL | fractional (§7) |
| `last_acked_version` | INTEGER NULL | server-confirmed version; null until first ack (§4) |
| `dirty` | INTEGER | 0/1 — unsynced local changes pending (§4) |
| `failed` | INTEGER | 0/1 — **always 0 in T2**; T3 sets it on persistent push failure |
| `pending_delete` | INTEGER | 0/1 — tombstone; hidden from UI, kept for the queued delete (§6) |

Index on `list_id`.

**`outbox`**

| Column | Type | Notes |
|---|---|---|
| `seq` | INTEGER PK AUTOINCREMENT | global monotonic order; FIFO-per-item = filter by item ordered by `seq` |
| `item_local_id` | TEXT NOT NULL | stable ref to `items.local_id` (indexed) |
| `list_id` | TEXT NOT NULL | |
| `kind` | TEXT | `create` \| `update` \| `delete` |
| `payload` | TEXT | JSON of the new values this entry carries (§5) |

Index on `item_local_id`. **No `base_version` column** — the base is read from the
item at push time in T3 (HLD §0.6 / §2.2).

> Both tables in one DB file means a single mutation (e.g. an edit) can persist the
> item row and append its outbox entry in **one transaction**, so disk never holds
> a half-applied change across a restart.

---

## 3. The stateful item repository (Option A store)

`ShoppingListItemRepository` owns the in-memory truth and the per-list notifiers.

```
class ShoppingListItemRepository {
  final ShoppingListItemDao _dao;
  final Map<String /*listId*/, Map<String /*localId*/, LocalShoppingListItem>> _cache;
  final Map<String /*listId*/, ValueNotifier<List<LocalShoppingListItem>>> _notifiers;

  // lifecycle
  Future<void> openList(String listId)          // hydrate cache from DAO if absent
  ValueListenable<List<LocalShoppingListItem>> watch(listId) // visible items (excludes tombstones)

  // local mutations (T2) — each: mutate memory → persist (txn) → notify
  Future<void> applyCreate(listId, parsedFields)
  Future<void> applyEdit(localId, {name, quantity, unit})
  Future<void> applyChecked(localId, bool checked)
  Future<void> applyReorder(localId, double newPosition)
  Future<void> applyDelete(localId)             // tombstone, see §6

  // ── reserved for later tasks (declared, not implemented in T2) ──
  // drainOutbox / reconcileOnAck / cascadeDiscard / retryAllFailed   (T3)
  // reconcileFromServer(listId, serverItems)                          (T4 pull diff)

  void dispose()  // dispose every notifier
}
```

`LocalShoppingListItem` is the in-memory model: the `items` columns above plus convenience
getters. It is distinct from the API `ShoppingListItem` so the local-only fields
(`localId`, `dirty`, `failed`, `pendingDelete`, nullable `lastAckedVersion`) have a
home.

**Mutation shape (the invariant every `applyXxx` follows):**

```
applyEdit(localId, changes) {
  final item = _cache[listId][localId];
  _cache[listId][localId] = item.copyWith(...changes, dirty: true);   // 1. memory (sync)
  await _dao.transaction(() {                                          // 2. write-through
    _dao.upsertItem(updated);
    _dao.appendOutbox(localId, listId, kind: update, payload: changes);
  });
  _notifiers[listId].value = _visibleItems(listId);                    // 3. notify
}
```

Memory is updated synchronously before any `await`, so the UI reads the new value
instantly and no other mutation can interleave mid-update. The write-through is
awaited (sqflite serialises writes internally, satisfying the ordering requirement
from §0.1).

`watch(listId)` / `_visibleItems` returns items where `pending_delete = 0`, leaving
section grouping and sorting to the detail service (§9).

---

## 4. The last-acked-version / dirty contract (cross-task)

T2 **defines** this contract; T3/T4 honour it. (`tasks.md` cross-task note: "Last-acked-version contract lands in T2.")

- **`lastAckedVersion`** = the server version this device last had **confirmed** for
  the item. It is the **base for the next push** (read at push time in T3). It is
  `null` for an item created offline that has never been acked.
- **It advances only by this device's own server acks** (T3 reconcile). A **pull
  never advances it** (T4 rule) — even when the pull keeps a dirty item's local
  value, `lastAckedVersion` is untouched so the queued entries still push against
  the right base.
- **`dirty`** = the item has local changes not yet confirmed by the server. Set to
  `true` by every local mutation (§3). It is **cleared only when no outbox entries
  remain for that item** (done in T3 on the final ack). T2 never clears it because
  T2 never syncs — so after any offline edit the item stays `dirty` and the outbox
  retains its entries, which is exactly what the restart-survival verification expects.
- **`failed`** is owned entirely by T3 (transient-failure marker). T2 leaves it `0`.

In T2 the only producers of these fields are local creates/edits, so every touched
item ends up `dirty = true`, `failed = 0`, `lastAckedVersion = null` (no acks yet).
The columns and rules exist so T3/T4 have a stable contract to build on.

---

## 5. Outbox entries (append-only, immutable, per-edit)

- Every local mutation **appends** one immutable entry (HLD §0.6 / §2.2). Editing
  the same item again appends **another** — entries are **never merged or rewritten**.
- `seq` (autoincrement) gives a **global monotonic order**; per-item FIFO is just
  the entries for that `local_id` ordered by `seq`.
- **Kinds and payloads:**
  - `create` — the full initial field set (`name`, `quantity`, `unit`, `checked`,
    `position`).
  - `update` — only the fields the action changed (an edit carries name/quantity/unit;
    a check carries `checked`; a reorder carries `position`). Matches T1's per-field
    Update endpoint and HLD's uniform-update gate.
  - `delete` — no field payload (the `local_id` is the whole intent).
- **No base version is stored** — read from the item at push time (T3).
- In T2 entries are only **appended and persisted**; they are never drained. The
  drain/reconcile/cascade-discard logic is T3.

---

## 6. Local delete = tombstone

A local delete sets `pending_delete = 1` and `dirty = true`, hides the item from
`watch()`, and appends a `delete` outbox entry — it does **not** remove the row.

Rationale (this is forced, not a free choice): HLD mandates the push reads the
item's base version **at push time**, so the row — with its `serverId` and
`lastAckedVersion` — must survive locally until the delete is acked. The tombstone
also makes the T3 "delete rejected → item reappears with the winning value" path
trivial: the row is still present, so it is simply un-tombstoned and overwritten.

In T2 the visible effect is complete: a deleted item vanishes from the screen and
stays gone across a restart (it's persisted as a tombstone). Hard removal of the
row happens in T3 once the delete is acked (or it's a never-synced local create,
which T3 just drops).

---

## 7. Item identity & fractional position

**Identity.** `applyCreate` generates a UUID (`uuid` package, already a dependency)
as `local_id`, sets `server_id = null`, `last_acked_version = null`, `dirty = true`.
The UI and outbox reference `local_id` only. T3 fills `server_id` on the create-ack.

**Position** is a `double` (matching the existing `ShoppingListItem.position` and
T1's fractional scheme), so moving one item rewrites only its own row:

- **Insert at end (add):** `position = (max visible position) + 1.0` (or `1.0` for
  the first item).
- **Reorder between neighbours A and B:** `position = (A.position + B.position) / 2`.
- **Move to top:** `(0 + firstPosition) / 2`; **move to bottom:** `lastPosition + 1.0`.

**Position is computed on the client, deliberately.** Instant offline reorder
(req §1.2/§3.1) rules out a server round-trip to assign a position, and the
non-contending-move rule (req §2.4, HLD §0.3) requires that a move rewrites **only
the moved item's row** — so the server must **store** the position it is given and
**not** renumber neighbours (renumbering would touch many rows, bump many versions,
and conflict with items the user never moved). Server-side position calculation is
therefore the wrong place for it; the server's only optional role is rebalancing
when fractions crowd (a T1 risk, irrelevant at 30–40 items).

**Position is ordering data, not a uniqueness key, so clashes are benign.** Two
items created independently — different devices, or a not-yet-synced local item
beside a synced one — can land on the **same** `position` (e.g. both `5.0`). That
only makes their *relative* order ambiguous; the version gate ignores position
equality, so nothing is rejected. The store resolves ties with a **deterministic
tiebreaker — sort by `(position, localId)`** — so every device orders them
identically, and the next reorder spreads them apart with a fresh midpoint.

---

## 8. Active / Done sectioning

The detail service derives two ordered lists from the store's visible items:

- **Active** = `checked == false`, sorted by `(position, localId)` ascending.
- **Done** = `checked == true`, sorted by `(position, localId)` ascending.

The `localId` tiebreaker (§7) keeps ordering deterministic when two items share a
`position`.

Tombstoned items (`pending_delete`) appear in neither. Checking an item flips its
section immediately because the toggle mutates `checked` in memory and the
notifier re-emits (§3). This matches the existing detail screen, which already
renders two `ReorderableListView` sections split by an add-item widget and a "Done"
header (`shopping_list_detail_screen.dart`).

---

## 9. Detail service — UI state & user actions

`ShoppingListDetailService` gains item state sourced from the store. It subscribes
to `repository.watch(listId)` and exposes the sectioned lists as
`ValueListenable<AsyncValue<...>>` per the state-management standard.

```
class ShoppingListDetailService {
  // existing: rename / delete / share / loadShoppingListDetail ...

  Future<void> openShoppingList(String listId) {
    await _itemRepository.openList(listId);     // hydrate from DB (offline-first)
    _subscribe(_itemRepository.watch(listId));  // store changes → re-render
  }

  // user actions — all delegate to the store, none touch the network
  Future<void> addItem(String typedText)        // parse → applyCreate
  Future<void> editItem(localId, fields)        // applyEdit
  Future<void> toggleChecked(localId, checked)  // applyChecked
  Future<void> deleteItem(localId)              // applyDelete (tombstone)
  Future<void> reorderItem(localId, fromIndex, toIndex)  // compute position → applyReorder
  Future<void> deleteAllChecked()               // applyDelete per Done item
  Future<void> uncheckAll()                      // applyChecked(false) per Done item
}
```

- **Add** reuses the existing `shopping_list_item_parser.dart` to split typed text
  into name/quantity/unit, inserts at end (§7), clears the field, keeps focus.
- **Bulk actions** (delete-all-checked, uncheck-all) expand into per-item store
  calls — each appends its own outbox entry. The independent/partial-outcome and
  per-item rejection behaviour is T3; T2 only does the instant local fan-out.
- Concurrency: each action method keeps the `_isXxxRunning` guard pattern only
  where it wraps an `await`; the store mutations themselves are synchronous up to
  the notify, so the guards are light.

---

## 10. View wiring

`ShoppingListDetailScreen` and its item widgets already exist but have their write
operations stubbed (`ui.md`). T2 wires them to the detail service:

- `ValueListenableBuilder` over the service's item state renders the active and
  Done `ReorderableListView` sections.
- `shopping_list_item_add_widget.dart` → `service.addItem`.
- `shopping_list_item_widget.dart` inline edit / checkbox / delete button →
  `editItem` / `toggleChecked` / `deleteItem`.
- Drag handles → `reorderItem`.
- The PopupMenu / bulk affordances → `deleteAllChecked` / `uncheckAll`.

The **list-level sync indicator** and the **failure toast / retry-all** are **not
rendered in T2** (sync indicator is T4, failure toast is T3). The screen shows the
local list and nothing sync-related.

---

## 11. Open-list flow (offline-first)

```
open detail screen
  → service.openShoppingList(listId)
      → repository.openList(listId)
          → if list not resident: dao.readItems(listId) into cache
      → subscribe to repository.watch(listId)
  → view renders last-known local contents immediately (works with no network)
```

No server fetch occurs in T2 (it is out of scope; the cold-start full fetch is
T4, HLD §1.4/§4). On a fresh install with an empty DB the screen simply shows an
empty list ready for offline adds.

---

## 12. Dependency injection / setup

In `shopping_list_setup.dart`:

- Open the sqflite database once (or inject an already-open `Database`, mirroring
  how `PreferencesService` is initialised in `main.dart`) and construct
  `ShoppingListItemDao`.
- Register `ShoppingListItemRepository` as a **singleton** (it holds shared
  state), constructed with the DAO, with a `dispose` callback for its notifiers.
- Inject `ShoppingListItemRepository` into `ShoppingListDetailService` (replacing
  the `TODO(shopping-list-items)` placeholders in the service and setup file).

Widget tests are out of scope for T2.

---

## 13. What T2 deliberately defers

| Concern | Owner |
|---|---|
| Any server call (push / pull / cold-start fetch) | T3 / T4 |
| Outbox drain, FIFO ordering, reconcile-on-ack, `serverId` remap | T3 |
| 412 cascade-discard, transient retry/backoff, `failed` flag, failure toast + retry-all | T3 |
| Per-item rejection toasts; bulk partial-outcome reporting | T3 |
| Background poller, full-list diff, active-edit overwrite | T4 |
| List-level sync / offline indicator chrome | T4 |

The schema columns (`server_id`, `last_acked_version`, `failed`) and the reserved
repository methods exist now so these tasks extend rather than reshape the store.

---

## 14. Standards impact

- **Architecture standard (repositories).** This design puts in-memory state and a
  `ValueNotifier` inside `ShoppingListItemRepository` (decision §0.4).
  `mobile/standards/architecture.md` must be updated **during implementation** so it
  no longer requires repositories to be **stateless**; the rule it should state
  instead is that a repository **provides raw data to services and contains no
  business logic** (a repository may hold local cache/persistence state). The
  cross-layer rules (views reach repositories only through services; repositories
  depend on neither) are unchanged.
- **Preferences/persistence standard.** Already relaxed by the HLD to permit a
  local database for offline-first state (`preferences-service.md` now allows this);
  no further change needed.

---

## 15. Verification (maps to `tasks.md` T2 "How to verify")

- **Offline edits render instantly + append outbox:** each `applyXxx` updates the
  in-memory cache and notifier synchronously (instant UI) and appends an outbox row
  in the same transaction (§3, §5).
- **Survive force-kill + relaunch offline:** every mutation is write-through to
  sqflite before the action completes; `openList` rehydrates the cache from disk;
  items sort back into active/Done by `checked` + `position`, tombstones stay hidden,
  and outbox entries are intact and `seq`-ordered (§2, §6, §8, §11).
