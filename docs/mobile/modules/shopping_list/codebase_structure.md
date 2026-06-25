# Shopping List — Codebase Structure

```
mobile/lib/features/shopping_list/
├── shopping_list.dart                  # Shopping list data model
├── shopping_list_item.dart             # Shopping list item data model
├── shopping_list_detail.dart           # Shopping list detail data model (includes items)
├── local_shopping_list_item.dart       # Local item model with last-acked version, dirty, failed, and pending-delete flags
├── shopping_list_repository.dart       # Shopping list data access layer
├── shopping_list_item_dao.dart         # sqflite DAO for the items + append-only outbox tables; OutboxKind enum
├── shopping_list_item_repository.dart  # Local item store: in-memory cache + ValueNotifier over the DB, appends outbox entries
├── shopping_list_list_service.dart     # Shopping list list business logic with ValueNotifier
├── shopping_list_detail_service.dart   # Shopping list detail business logic; drives local item add/edit/check/delete/reorder and bulk ops
├── shopping_list_setup.dart            # Dependency injection setup for shopping list module
├── shopping_list_list.dart             # Reusable shopping list body widget
├── shopping_list_list_fab.dart         # Reusable shopping list FAB widget
├── shopping_list_item_widget.dart      # Reusable inline-editable item widget using ItemDisplayData; supports strikethrough, subtitle, optional delete button
├── shopping_list_item_parser.dart      # Regex-based parser for "quantity unit name" text format
├── shopping_list_item_add_widget.dart  # Dedicated widget for adding new items
├── shopping_list_review_item.dart      # Mutable generated item model (name/quantity/unit mutable, source immutable)
├── shopping_list_review_widget.dart    # Review widget with ReorderableListView, inline editing, and checkbox selection
├── shopping_list_detail_screen.dart    # Shopping list detail screen
├── shopping_list_permission.dart       # Shopping list permission model
├── shopping_list_rename_dialog.dart    # Dialog widget for renaming shopping lists
└── shopping_list_sharing_dialog.dart   # Dialog widget for sharing shopping lists
```
