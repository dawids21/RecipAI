# Shopping List — UI

## Screens and Widgets

- Shopping List List Widget (`shopping_list_list.dart`) - Reusable body widget displaying all shopping lists with
  pull-to-refresh and navigation to detail screen on tap.
- Shopping List List FAB (`shopping_list_list_fab.dart`) - FloatingActionButton widget for creating new shopping lists
  with dialog.
- Shopping List Detail Screen (`shopping_list_detail_screen.dart`) - Displays individual shopping list with inline
  item widgets. Add, edit, check/uncheck, delete, and drag-and-drop reorder all apply to the local item store and
  render instantly while offline, surviving an app restart. Items are organized into two sections: active items
  (unchecked) at the top and "Done" section at the bottom for checked items, separated by the add item widget and a
  "Done" header. Each section uses ReorderableListView with custom drag handles. The Done section uses AnimatedSize for
  smooth expand/collapse transitions. Features PopupMenuButton with actions: "Rename List", "Share List", "Delete All
  Checked", "Uncheck All" (both expand into per-item local operations), and (for owners) "Delete List". A list-level sync
  indicator next to the title reflects the current sync state (syncing / synced / offline / failure); a background
  poll refreshes the list from the server every 10s while it is open, diffing others' changes into the local store,
  and a retry banner surfaces on push failure.
- Shopping List Item Widget (`shopping_list_item_widget.dart`) - Reusable inline-editable widget for shopping list
  items. Smart text parsing (supports "2 kg apples", "500g flour", "bread" formats), automatic quantity/unit extraction
  using regex, TextField-based editing with focus management. Optional drag handle for reordering (using
  ReorderableDragStartListener), positioned on the left before the checkbox. Optional `subtitle` parameter shows
  secondary text (e.g. source recipe name) below the item text. When a remote change arrives while the item's field
  is focused, the field is overwritten with the server value and an "updated elsewhere" toast is shown
  (`onOverwrite`).
- Shopping List Item Add Widget (`shopping_list_item_add_widget.dart`) - Dedicated widget for adding new shopping list
  items with plus icon, "Add item..." hint text, smart text parsing (same as ShoppingListItemWidget), and automatic
  field clearing with focus retention after submission for quick consecutive entry.
- Shopping List Rename Dialog (`shopping_list_rename_dialog.dart`) - Stateful dialog widget for renaming shopping lists
  with TextField input, proper TextEditingController lifecycle management, and validation to prevent empty names.
- Shopping List Review Widget (`shopping_list_review_widget.dart`) - Review widget with ReorderableListView, inline
  editing, and checkbox selection for generated items. Allows users to select which generated items to add to a
  shopping list. The actual queuing of items to the sync service is stubbed pending the shopping-list-items rewrite.

## Flow

#### Shopping List Management Flow

1. **Bottom Navigation → Shopping Tab** → MainScreen switches to Shopping tab view
2. **FAB Tap** (on Shopping tab) → Create dialog with name input → Shopping list created → List refreshed
3. **Pull to Refresh** (on Shopping tab) → Shopping lists reloaded from API
4. **Shopping List Tap** → Shopping List Detail Screen (`/shopping-lists/:id` with shopping list ID parameter)
5. **Back Button** (on Shopping List Detail Screen) → Back to Shopping tab on Main Screen
