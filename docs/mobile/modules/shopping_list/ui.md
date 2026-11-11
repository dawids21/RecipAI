# Shopping List — UI

## Screens and Widgets

- Shopping List List Widget (`shopping_list_list.dart`) - Shopping tab body: every shopping list, with pull-to-refresh;
  tapping a list opens its detail screen.
- Shopping List List FAB (`shopping_list_list_fab.dart`) - Opens the create dialog and creates the list with the name
  it returns.
- Shopping List Create Dialog (`shopping_list_create_dialog.dart`) - Name field with the `used / limit` counter under
  it; Create is disabled at the quota (see `docs/mobile/modules/limits/module.md` → Services and State).
- Shopping List Detail Screen (`shopping_list_detail_screen.dart`) - One list's items, edited in place:
  - *Layout*: unchecked items on top, then the add-item field, then a collapsible "Done" section with the checked
    items. Each section is reorderable by drag handle.
  - *Editing*: add, edit, check/uncheck, delete and reorder apply on the device and render at once, offline too, and
    survive an app restart (see `module.md` → Sync).
  - *Menu*: "Rename List", "Share List" (the sharing dialog — `docs/mobile/modules/sharing/ui.md`), "Delete All
    Checked", "Uncheck All", and "Delete List" for owners only.
  - *Undo*: deleting an item, "Delete All Checked" and "Uncheck All" each show a 5-second snackbar naming the count
    ("1 item deleted", "4 items unchecked") with UNDO, which restores the items' position and checked state. A bulk
    action that affects nothing shows nothing. A new destructive action replaces the snackbar, so only the latest is
    undoable (see `module.md` → Services and State); leaving the screen or letting the snackbar expire commits it.
  - *Sync status*: an indicator next to the title shows syncing, synced, offline or failure; a retry banner appears
    when pushing fails. Others' changes appear without a refresh (see `module.md` → Sync → Poll).
  - *Rejected changes*: a change the server refuses is announced in a snackbar naming the item (outcomes in
    `module.md` → Sync → Push). A refusal because the list is full raises one "This list is full - items weren't
    added" bar instead, not repeated while one is on screen, so adding past the cap does not queue one snackbar per
    item.
  - *Item limit*: a counter above the add row pairs the list's item count, checked items included, with the list's
    cap. It shows this list's cap or nothing, never the previous list's (see `module.md` → Services and State). At the
    cap the add field is disabled and Enter no longer opens a new row, so the chain stops instead of producing a
    refusal per item. A row already open when a poll brings the list to the cap stays open; its commit is refused and
    discarded like any other.
- Shopping List Item Widget (`shopping_list_item_widget.dart`) - One item row, edited inline. Text is parsed into
  quantity, unit and name ("2 kg apples", "500g flour", "bread"). An optional drag handle sits left of the checkbox,
  and an optional subtitle shows secondary text such as the source recipe. When a remote change arrives while the
  row is being edited, the field silently takes the server's value.
- Shopping List Item Add Widget (`shopping_list_item_add_widget.dart`) - The "Add item..." field, parsed like an item
  row. After each submit it clears and keeps focus, for quick consecutive entry. Disabled at the item cap.
- Shopping List Rename Dialog (`shopping_list_rename_dialog.dart`) - Name field, pre-filled; empty names are refused.
- Shopping List Review Widget (`shopping_list_review_widget.dart`) - Review generated items before adding them to a
  list; used by planning's generation wizard and recipe's Add to Shopping List.
  - *Items*: each item is editable inline, reorderable by drag, and selected by a checkbox; unselected items are
    struck through. The source recipe's name shows under each item. "Select All / Deselect All" sits in the header.
  - *Adding*: "Add to Shopping List" asks which list, then adds only the selected items ("Added N item(s) to shopping
    list"); they sync like manual additions (see `module.md` → Services and State). Edits before that stay on the
    review screen only.

## Flow

#### Shopping List Management Flow

1. **Bottom Navigation → Shopping Tab** → Main Screen switches to Shopping tab view
2. **FAB Tap** (on Shopping tab) → Create dialog with name input → Shopping list created → List refreshed
3. **Pull to Refresh** (on Shopping tab) → Shopping lists reloaded from API
4. **Shopping List Tap** → Shopping List Detail Screen (`/shopping-lists/:id` with shopping list ID parameter)
5. **Back Button** (on Shopping List Detail Screen) → Back to Shopping tab on Main Screen
