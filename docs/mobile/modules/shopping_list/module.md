# Shopping List Module

Shopping lists: create, rename, share and delete lists, and an offline-first item editor with smart text parsing,
drag-and-drop reordering, Active and Done sections, bulk actions and a single-step undo. Items live in an on-device
store, render instantly, survive restarts, and sync to the server in the background. Also provides the review widget
and import service that planning and recipe use to add generated items to a list.

## Boundary

- **Backend endpoints**: all of `/shopping-lists` — lists, `balance`, `{id}/limits` (the item quota), items, and the
  sharing endpoints (`docs/backend/modules/shopping-lists/api.md`).
- **Uses**: `auth`; `limits` (list counter and quota; item counter against the per-list item quota); `sharing` (list
  sharing dialog); `recipe` (`Ingredient`, to build review items from a recipe).
- **Used by**: `planning` (shopping list generation) and `recipe` (Add to Shopping List), through the review widget,
  `ShoppingListListService` and `ShoppingListItemImportService`; `invites` (reloads the list list on accept); `core`
  (Shopping tab, detail route, the item `Database` opened in `main()`).

## File Map

```
mobile/lib/features/shopping_list/
├── shopping_list.dart                       # ShoppingList model
├── shopping_list_item.dart                  # Item as the server returns it
├── local_shopping_list_item.dart            # Item as the device stores it, with its sync state
├── undoable_action.dart                     # The last destructive action's inverse (see Services and State)
├── shopping_list_review_item.dart           # ShoppingListGeneratedItem: an editable item to review before import
├── shopping_list_repository.dart            # List endpoints, item quota and sharing
├── shopping_list_item_repository.dart       # Item endpoints; OutboxPayload and the push failure exceptions
├── shopping_list_item_database_factory.dart # Opens the on-device DB; the single-sourced schema, also used in tests
├── shopping_list_item_dao.dart              # sqflite access to the items and outbox tables
├── shopping_list_item_store_service.dart    # Local item store and outbox (see Sync)
├── shopping_list_sync_service.dart          # Background push and poll (see Sync)
├── shopping_list_list_service.dart          # Lists and list balance
├── shopping_list_detail_service.dart        # The open list (see Services and State)
├── shopping_list_item_import_service.dart   # Writes reviewed items into any list
├── shopping_list_setup.dart
├── shopping_list_list.dart                  # Shopping tab body: the lists
├── shopping_list_list_fab.dart              # Opens the create dialog and creates the list
├── shopping_list_create_dialog.dart         # Name input; limited (see limits)
├── shopping_list_rename_dialog.dart
├── shopping_list_sharing_dialog.dart        # Binds ShoppingListDetailService to SharingDialog
├── shopping_list_detail_screen.dart         # The list's items, menu, sync status, undo and rejection toasts
├── shopping_list_item_widget.dart           # One inline-editable item row
├── shopping_list_item_add_widget.dart       # The add-item field; disabled at the item cap
├── shopping_list_item_parser.dart           # Parses "quantity unit name" text
└── shopping_list_review_widget.dart         # Review, edit and select items, pick a list, import
```

```
mobile/test/features/shopping_list/
└── shopping_list_sync_service_test.dart     # Outbox drain and poll sync
```

## Services and State

- **`ShoppingListItemStoreService`** — app-lifetime singleton and the only writer of item state: an in-memory cache
  and one listenable per list over the on-device database, plus the outbox (see Sync).
- **`ShoppingListSyncService`** — app-lifetime singleton, started at setup. Pushes outboxes and polls the open list
  (see Sync); exposes a per-list sync status and a stream of rejected changes, which the detail screen renders.
- **`ShoppingListDetailService`** — lazy singleton reset when the detail screen is disposed. Holds the open list's
  items (mirrored from the store), its item quota, its permissions and the user's role, and the pending undo. Opening
  a list starts its poll and kicks a drain; every local change kicks a drain; disposing stops the poll.
  - *Role*: the user is treated as an editor until the permissions load confirms they own the list, so owner-only
    actions never show by mistake.
  - *Item quota*: opening a list first clears the previous quota and drops a quota response for a list that is no
    longer open, so the item count is never compared with another list's cap.
  - *Undo*: one slot, replaced by each destructive action and cleared before it is replayed, so a late or repeated
    tap does nothing. Undo replays as an ordinary local change through the store (see Sync → Write Path): deleted
    items are re-created as new items, unchecked items are checked again.
- **`ShoppingListListService`** — session-scoped lazy singleton (see `docs/mobile/modules/core/module.md` → Services
  and State) holding the lists and the list balance.
- **`ShoppingListItemImportService`** — stateless lazy singleton. Writes items into any list through the store,
  whether or not that list is open, then kicks a drain, so imported items behave like manual additions.

## Sync

Items are offline-first: every change lands in the on-device store and renders at once, and is pushed to the server
in the background. The reasons are in ADR-0003 (full-list pull), ADR-0004 (store aggregate) and ADR-0005 (injected
`Scheduler`).

### Write Path

Every local change runs under the list's store lock: it updates the cache and the list's listenable, and writes the
item together with one outbox entry in a single transaction, so a local change and a server reconcile never
interleave. The outbox is append-only, one queue per list:

- **create** — add, import, and undo of a delete (re-created as a new item); carries the item's fields.
- **update** — edit, check/uncheck, reorder, and bulk uncheck; carries a full snapshot of the item's fields.
- **delete** — the item is hidden at once and removed once the server acknowledges.

An item with queued entries is *dirty*. Its server id and version are read when an entry is pushed, not stored in the
entry, so changes queued before the create is acknowledged still reach the right server item.

### Push

A drain pushes one list's queue oldest-first, one entry at a time; different lists drain independently. At most one
drain runs per list — a request arriving mid-drain makes the running drain loop again. Drains are requested after each
local change, on opening a list, every 10s while it is open, at start-up and on resume (every list with queued
entries), and by the retry timers below. Each push, and each poll, holds the list's sync lock, so a poll never
reconciles between a push and its acknowledgement.

An accepted push adopts the server id and version and drops the entry; the item stays dirty while other entries
remain. A delete answered 204 or 404 removes the item. Other outcomes:

| Outcome                   | What happens                                                                                                                                   | User sees                            |
|---------------------------|------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------|
| No network                | Queue stalls at the entry; retried every 10s, never marked failed                                                                              | Offline indicator                    |
| Other unexpected error    | Queue stalls; retried with exponential backoff (capped at 30s); after 5 retries the list is marked failed and waits for the next drain request | Failure indicator and retry banner   |
| 412 on update/delete      | Item rolled back to the server's version; all its queued entries dropped                                                                       | "changed elsewhere and rolled back"  |
| 404 on create/update      | Item and its queued entries removed                                                                                                            | "no longer exists"                   |
| 400/403                   | Item and its queued entries removed                                                                                                            | "could not be synced"                |
| 429 on create (list full) | Item and its queued entries removed                                                                                                            | One "list is full" bar (see `ui.md`) |

Rejected changes do not stop the drain; it continues with the next entry. Retry from the banner resets the retry count.
A rejection for a list whose screen is not open is shown to nobody.

### Poll

While a list is open, its full item list is fetched on open and every 10s and diffed into the store: new server items
are added, clean items take the server's fields when its version is newer, and clean items missing on the server are
removed. Dirty items are never touched — their own push resolves them. A failed poll leaves the store unchanged.
Polls and periodic drains stop while the app is in the background and resume with an immediate poll.

### Guarantees

- Changes to one list reach the server in the order they were made; lists are not ordered against each other.
- The items and the outbox live in the on-device database, so unpushed changes survive a restart and drain at
  start-up.
- Sign-out does not clear them (see Gotchas).

## Gotchas

- Signing out clears nothing in the item store or the outbox: items stay on the device, and queued changes are
  pushed with whichever account signs in next.
