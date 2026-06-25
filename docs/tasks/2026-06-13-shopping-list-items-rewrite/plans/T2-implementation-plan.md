# T2 — Mobile Local Store + Offline Detail Screen

## Context

The prior shopping-list item-management code was deleted (commit `9c8a27f`), leaving the
detail screen and its widgets in place but with every write path stubbed as
`// TODO(shopping-list-items)`. T2 rebuilds item management as an **entirely offline**
experience: open a list and add / edit / check-uncheck / delete / reorder items
instantly, surviving an app restart — **with no server communication** (push is T3, pull
is T4). T2 also pins down the per-item **last-acked-version / dirty** contract that T3 and
T4 depend on.

The design is fully specified in
`docs/tasks/2026-06-13-shopping-list-items-rewrite/T2-design.md`; this plan translates it
into concrete files and edits. Architecture: **Repository (stateful store) → Service →
View**, per the existing mobile feature pattern.

Key existing pieces to reuse:
- `ShoppingListItemParser.parse()` (`shopping_list_item_parser.dart`) — typed-text → name/quantity/unit.
- `AsyncValue` (`lib/core/async_value.dart`) — `loading/data/error`, `guardAsync`, `when`.
- Item widgets (`shopping_list_item_widget.dart`, `shopping_list_item_add_widget.dart`) already exist; only their callbacks need wiring.
- `uuid: ^4` and `path_provider: ^2.1.5` already in `pubspec.yaml`. **`sqflite` is NOT present — must be added**.

---

## 1. Dependencies

In `mobile/pubspec.yaml`:
- add `sqflite: ^2.3.0` to `dependencies`.

Run `flutter pub get`.

---

## 2. Local model — `LocalShoppingListItem` (new)

`mobile/lib/features/shopping_list/local_shopping_list_item.dart`

Immutable class mirroring the `items` table (design §2), distinct from the API
`ShoppingListItem` so local-only fields have a home:

- `localId` (String, PK), `serverId` (String?), `listId` (String)
- `name` (String), `quantity` (double?), `unit` (String?)
- `checked` (bool), `position` (double)
- `lastAckedVersion` (int?), `dirty` (bool), `failed` (bool), `pendingDelete` (bool)

Provide `copyWith(...)`, `fromMap`/`toMap` (sqflite row ↔ object; bools ↔ 0/1), and a
`compareTo`-style ordering helper for the `(position, localId)` tiebreaker (§7/§8).

---

## 3. DAO — `ShoppingListItemDao` (new, stateless)

`mobile/lib/features/shopping_list/shopping_list_item_dao.dart`

Pure sqflite persistence, no state, no business logic (design §1, §2). Constructed with an
open `Database`.

- **Schema creation** (`onCreate`, version 1): the `items` table and `outbox` table exactly
  as design §2 (columns, types, `local_id` PK, `outbox.seq INTEGER PK AUTOINCREMENT`),
  plus indexes on `items.list_id` and `outbox.item_local_id`.
- Methods:
  - `readItems(listId)` → `List<LocalShoppingListItem>` (all rows incl. tombstones; repo filters).
  - `transaction(fn)` — wrap upsert + outbox append in one txn (§2 note: never half-applied).
  - `upsertItem(item)` — `insert ... ON CONFLICT REPLACE`.
  - `appendOutbox(localId, listId, kind, payload)` — insert outbox row; `payload` is JSON.
  - (Reserved, not implemented in T2: outbox reads/drain, item hard-delete — declared for T3.)
- A static `openShoppingListDatabase()` helper using `path_provider` to resolve the DB path
  and `openDatabase(..., version: 1, onCreate: ...)`.

Outbox `kind` is a small enum/const set: `create | update | delete`. Payloads (§5):
create = full field set; update = only changed fields; delete = empty.

---

## 4. Stateful repository / store — `ShoppingListItemRepository` (new)

`mobile/lib/features/shopping_list/shopping_list_item_repository.dart`

The Option-A store (design §3). Holds in-memory truth + per-list `ValueNotifier`s; every
mutation follows **memory (sync) → write-through (txn) → notify**.

State:
- `Map<String listId, Map<String localId, LocalShoppingListItem>> _cache`
- `Map<String listId, ValueNotifier<List<LocalShoppingListItem>>> _notifiers`

Lifecycle:
- `Future<void> openList(listId)` — if not resident, `dao.readItems(listId)` into cache;
  create the notifier seeded with `_visibleItems(listId)`.
- `ValueListenable<List<LocalShoppingListItem>> watch(listId)` — visible items
  (excludes `pendingDelete`), the notifier created in `openList`.

Mutations (each: mutate `_cache` synchronously → `await dao.transaction(upsert + appendOutbox)`
→ set notifier value to `_visibleItems`). All set `dirty: true`, leave `failed: 0`,
`lastAckedVersion: null` (no acks in T2 — §4):
- `applyCreate(listId, {name, quantity, unit})` — generate `localId` (uuid),
  `position = maxVisiblePosition + 1.0` (or `1.0` if first), `checked: false`; outbox `create`
  with full field set (§7).
- `applyEdit(localId, {name, quantity, unit})` — outbox `update` with the changed fields.
- `applyChecked(localId, bool)` — outbox `update` carrying `checked`.
- `applyReorder(localId, double newPosition)` — outbox `update` carrying `position`.
- `applyDelete(localId)` — **tombstone**: set `pendingDelete: true, dirty: true`, hide from
  `watch`, append `delete` outbox entry; row is **not** removed (§6).

`_visibleItems(listId)` returns `pendingDelete == false` items (unsorted; the service
sorts/sections). Reserved T3/T4 methods (`drainOutbox`, `reconcileOnAck`, `cascadeDiscard`,
`reconcileFromServer`) are **not** declared yet unless trivial — keep T2 focused.

`dispose()` disposes every notifier.

> Concurrency: Dart's single isolate makes each synchronous read-modify-write atomic; sqflite
> serialises writes. No locking (design §0.1).

---

## 5. Detail service — item state & actions

Edit `mobile/lib/features/shopping_list/shopping_list_detail_service.dart`:

- Constructor: add `required ShoppingListItemRepository itemRepository` (replaces the
  `TODO(shopping-list-items): inject` placeholder). Store as `_itemRepository`.
- New notifier exposing **sectioned** UI state derived from the store
  (design §8, §9), per state-management standard:
  `ValueNotifier<AsyncValue<ShoppingListItems>>` where `ShoppingListItems` is a small value
  type `({List<LocalShoppingListItem> active, List<LocalShoppingListItem> done})`. Expose
  read-only `ValueListenable get items`.
- `openShoppingList(listId)`:
  - `await _itemRepository.openList(listId)`,
  - subscribe to `_itemRepository.watch(listId)` (add listener) → on each change recompute
    sections (`active = !checked`, `done = checked`, both sorted by `(position, localId)`)
    and set `_items.value = AsyncValue.data(...)`. Keep the `VoidCallback` listener ref + the
    watched `ValueListenable` so it can be removed in `dispose`.
- Action methods (all delegate to the store, none touch the network; light `_isXxxRunning`
  guards only where wrapping an `await`):
  - `addItem(ItemChanged parsed)` → `applyCreate`
  - `editItem(localId, ItemChanged)` → `applyEdit`
  - `toggleChecked(localId, bool)` → `applyChecked`
  - `deleteItem(localId)` → `applyDelete`
  - `reorderItem(localId, double newPosition)` → `applyReorder` (service receives the computed
    position from the screen, or computes from neighbours — see §6 below; keep position math in
    one place, the service, given it needs the sorted neighbours).
  - `deleteAllChecked()` → `applyDelete` per Done item
  - `uncheckAll()` → `applyChecked(false)` per Done item
- `dispose()`: remove the store listener and dispose `_items` alongside the existing notifiers.

The existing `loadShoppingListDetail` / rename / delete / share stay **unchanged** (list-level
name+role still come from the server fetch; only items move to the local store). T2 does not
make list-name loading offline — that is pre-existing, out-of-scope behaviour.

---

## 6. View wiring — `shopping_list_detail_screen.dart`

- `initState`: after the existing `loadShoppingListDetail`/`loadSharedUsers`, call
  `service.openShoppingList(widget.shoppingListId)` (replaces the `start keeping … in sync` TODO).
- Render items from a `ValueListenableBuilder` over `service.items` **nested inside** the
  existing `data: (detail)` branch (the outer builder still supplies `detail.name`/`role` for
  the app bar + title). Replace `_buildSplitItemWidgets(detail)` so it reads from the
  `ShoppingListItems` (active/done) value instead of `detail.items`; key rows by
  `ValueKey(item.localId)`.
- Wire the stubbed callbacks:
  - add widget `onAdd: (changed) => service.addItem(changed)`
  - per-item `onEdit: (changed) => service.editItem(item.localId, changed)`
  - `onDelete: () => service.deleteItem(item.localId)`
  - `onCheckChanged: (c) => service.toggleChecked(item.localId, c)`
  - reorder handlers compute the target position from the sorted neighbours (§7 midpoint rules:
    between A,B → `(A+B)/2`; top → `(0+first)/2`; bottom → `last+1.0`) and call
    `service.reorderItem(localId, newPosition)`.
  - ephemeral-row `_saveEphemeralItem(result)` → `service.addItem(result)`.
- Bulk actions: add the two PopupMenu entries (`delete all checked`, `uncheck all`) and route
  them to `service.deleteAllChecked()` / `service.uncheckAll()` (replaces those TODOs).
- **Not in T2** (leave as-is / no chrome): the sync-status indicator (T4) and conflict/error
  handling TODOs (T3) — remove only the parts T2 implements; leave T3/T4 TODOs with a note,
  or convert them to `TODO(shopping-list-items, T3/T4)` for clarity.

---

## 7. DI / setup

Edit `mobile/lib/features/shopping_list/shopping_list_setup.dart`:
- Make `setupShoppingList` async; open the DB internally via
  `await ShoppingListItemDao.openShoppingListDatabase()`.
- Construct `ShoppingListItemDao` from the DB, register
  `ShoppingListItemRepository` as a **singleton** (shared state) with a
  `dispose: (r) => r.dispose()` callback.
- Pass `itemRepository: getIt<ShoppingListItemRepository>()` into the
  `ShoppingListDetailService` registration (replaces the two TODOs in this file).

Edit `mobile/lib/main.dart`:
- Call `await setupShoppingList()` (DB is opened internally by the setup function).

The other out-of-feature `TODO(shopping-list-items)` call sites in `planning/` and `recipe/`
that "forward the item-sync dependency" need no change in T2 — they construct the detail
screen via the get_it-registered service, which now carries the repository internally.

---

## 8. Standards update (design §14)

Edit `docs/mobile/standards/architecture.md`, the Repository bullet. Replace:

> **Repository** (`*_repository.dart`): Stateless data access — HTTP calls, local storage.
> Returns raw types. No business logic. No state.

with wording that drops the statelessness requirement while keeping the real rule — a
repository **provides raw data to services and contains no business logic**, and **may hold
local cache / persistence state** (e.g. an in-memory cache + `ValueNotifier` over a local DB).
The cross-layer rules (views reach repositories only via services; repositories depend on
neither services nor views) are unchanged. No change to `preferences-service.md` (already
relaxed by the HLD).

---

## 9. Verification (maps to tasks.md T2 "How to verify")

1. `cd mobile && flutter analyze` — clean.
2. Manual (device/emulator, networking disabled):
   - Add several items, check some, reorder, delete one → every change renders instantly.
   - Force-kill and relaunch still offline, reopen the list → all items + queued outbox entries
     intact, correctly split into active/Done, tombstones stay hidden.

---

## Out of scope (deferred — design §13)

Any server call, outbox drain/reconcile/`serverId` remap, 412 cascade-discard, retry/backoff,
`failed` flag usage, per-item rejection / bulk partial-outcome toasts (all **T3**); background
poller, full-list diff, list-level sync indicator chrome (all **T4**). The schema columns
(`server_id`, `last_acked_version`, `failed`) and contract exist now so T3/T4 extend rather
than reshape the store.
