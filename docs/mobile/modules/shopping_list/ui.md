# Shopping List — UI

## Screens and Widgets

- Shopping List List Widget (`shopping_list_list.dart`) - Reusable body widget displaying all shopping lists with
  pull-to-refresh and navigation to detail screen on tap.
- Shopping List List FAB (`shopping_list_list_fab.dart`) - FloatingActionButton widget opening the create dialog and
  performing the create with the name it returns.
- Shopping List Create Dialog (`shopping_list_create_dialog.dart`) - Stateful dialog widget for creating shopping lists
  with TextField input and proper TextEditingController lifecycle management. Loads the list count on open, shows the
  `used / limit` counter under the field, and disables Create at the cap (see `docs/mobile/modules/limits/ui.md`).
- Shopping List Detail Screen (`shopping_list_detail_screen.dart`) - Displays individual shopping list with inline
  item widgets. Add, edit, check/uncheck, delete, and drag-and-drop reorder all apply to the local item store and
  render instantly while offline, surviving an app restart. Items are organized into two sections: active items
  (unchecked) at the top and "Done" section at the bottom for checked items, separated by the add item widget and a
  "Done" header. Each section uses ReorderableListView with custom drag handles. The Done section uses AnimatedSize for
  smooth expand/collapse transitions. Features PopupMenuButton with actions: "Rename List", "Share List", "Delete All
  Checked", "Uncheck All" (both expand into per-item local operations), and (for owners) "Delete List". Deleting an
  item, "Delete All Checked" and "Uncheck All" each show a 5-second snackbar naming the number of affected items
  ("1 item deleted", "4 items unchecked") with an UNDO action; undo restores items to their original position and
  checked state. Nothing is shown when a bulk action affects zero items, a second destructive action replaces the
  first snackbar so only the most recent action is undoable, and leaving the screen or letting the snackbar expire
  commits the action. A list-level sync
  indicator next to the title reflects the current sync state (syncing / synced / offline / failure); a background
  poll refreshes the list from the server every 10s while it is open, diffing others' changes into the local store,
  and a retry banner surfaces on push failure. A queued change the server permanently refuses is dropped and
  announced in a snackbar naming the item ("changed elsewhere and rolled back", "no longer exists", "could not be
  synced"); a refusal because the list is full instead raises a single "This list is full - items weren't added"
  bar, not repeated while one is already on screen, so adding past the cap does not queue up one snackbar per
  rejected item. An item counter sits above the add row, pairing the list's flat item count — checked items
  included — with the cap fetched for this list when it was opened. The detail service is a lazy singleton reused
  across visits, so opening a list clears the cap before fetching the new one and drops a response that lands after
  the user has moved on: the counter is either this list's cap or nothing, never the previous list's. At the cap
  both add surfaces close: the add widget's field is disabled and Enter-to-insert declines to open a new row, so
  the chain stops rather than producing a refusal per item. A row already open when an incoming poll reaches the
  cap is left where it is; its commit is refused and discarded by the existing path.
- Shopping List Item Widget (`shopping_list_item_widget.dart`) - Reusable inline-editable widget for shopping list
  items. Smart text parsing (supports "2 kg apples", "500g flour", "bread" formats), automatic quantity/unit extraction
  using regex, TextField-based editing with focus management. Optional drag handle for reordering (using
  ReorderableDragStartListener), positioned on the left before the checkbox. Optional `subtitle` parameter shows
  secondary text (e.g. source recipe name) below the item text. When a remote change arrives while the item's field
  is focused, the field is silently overwritten with the server value (no toast).
- Shopping List Item Add Widget (`shopping_list_item_add_widget.dart`) - Dedicated widget for adding new shopping list
  items with plus icon, "Add item..." hint text, smart text parsing (same as ShoppingListItemWidget), and automatic
  field clearing with focus retention after submission for quick consecutive entry. An `enabled` flag disables the
  field, which the detail screen uses to close the surface at the item cap.
- Shopping List Rename Dialog (`shopping_list_rename_dialog.dart`) - Stateful dialog widget for renaming shopping lists
  with TextField input, proper TextEditingController lifecycle management, and validation to prevent empty names.
- Shopping List Review Widget (`shopping_list_review_widget.dart`) - Review widget with ReorderableListView, inline
  editing, and checkbox selection for generated items. Allows users to select which generated items to add to a
  shopping list; selected items are queued to the chosen list via `ShoppingListItemImportService` and synced.

## Flow

#### Shopping List Management Flow

1. **Bottom Navigation → Shopping Tab** → MainScreen switches to Shopping tab view
2. **FAB Tap** (on Shopping tab) → Create dialog with name input → Shopping list created → List refreshed
3. **Pull to Refresh** (on Shopping tab) → Shopping lists reloaded from API
4. **Shopping List Tap** → Shopping List Detail Screen (`/shopping-lists/:id` with shopping list ID parameter)
5. **Back Button** (on Shopping List Detail Screen) → Back to Shopping tab on Main Screen
