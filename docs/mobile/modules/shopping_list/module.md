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
├── shopping_list_item_store_service.dart    # Local item store (see Services and State)
├── shopping_list_sync_service.dart          # Background push and poll (see Services and State)
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
  and one listenable per list over the on-device database. Every local change runs under that list's lock and
  appends an entry to an append-only outbox in the same step, so a local edit and a server reconcile never interleave
  (ADR-0004).
- **`ShoppingListSyncService`** — app-lifetime singleton, started at setup. It pushes each list's outbox to the
  server in order, one entry at a time, and drains every list with pending entries at start-up and on resume, so
  offline edits to a list that is not open still flush. While a list is open it polls the full list every 10s and
  merges others' changes into the store without touching items that have unpushed local changes (ADR-0003). It
  exposes a per-list sync status and a stream of rejected changes; the detail screen renders both. Timers come from
  the injected `Scheduler` (ADR-0005).
- **`ShoppingListDetailService`** — lazy singleton reset when the detail screen is disposed. Holds the open list's
  items (mirrored from the store), its item quota, its permissions and the user's role, and the pending undo. Opening
  a list starts its poll and kicks a drain; disposing stops the poll.
  - *Role*: the user is treated as an editor until the permissions load confirms they own the list, so owner-only
    actions never show by mistake.
  - *Item quota*: opening a list first clears the previous quota and drops a quota response for a list that is no
    longer open, so the item count is never compared with another list's cap.
  - *Undo*: one slot, replaced by each destructive action and cleared before it is replayed, so a late or repeated
    tap does nothing. Undo replays as an ordinary local change through the store: deleted items are re-created as new
    items, unchecked items are checked again.
- **`ShoppingListListService`** — session-scoped lazy singleton (see `docs/mobile/modules/core/module.md` → Services
  and State) holding the lists and the list balance.
- **`ShoppingListItemImportService`** — stateless lazy singleton. Writes items into any list through the store,
  whether or not that list is open, then kicks a drain, so imported items behave like manual additions.

## Gotchas

- Signing out clears nothing in the item store or the outbox: items stay on the device, and queued changes are
  pushed with whichever account signs in next.
