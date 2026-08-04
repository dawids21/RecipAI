# Shopping List — Codebase Structure

```
mobile/lib/features/shopping_list/
├── shopping_list.dart                  # Shopping list data model
├── shopping_list_item.dart             # Shopping list item data model
├── shopping_list_detail.dart           # Shopping list detail data model (includes items)
├── local_shopping_list_item.dart       # Local item model with last-acked version, dirty, failed, and pending-delete flags
├── shopping_list_repository.dart       # Shopping list data access layer
├── shopping_list_item_database_factory.dart # Owns item DB creation: opens the on-device DB and holds the single-sourced items + outbox schema (shared with the in-memory test DB)
├── shopping_list_item_dao.dart         # sqflite DAO over an injected Database for the items + append-only outbox tables; OutboxKind enum
├── shopping_list_item_repository.dart  # HTTP item endpoints (fetch/create/update/delete); OutboxPayload + item exception types
├── shopping_list_item_store_service.dart # Local item store aggregate: in-memory cache + per-list ValueNotifier + per-list Lock over the DAO/outbox; serialises every read-modify-write per list (ADR-0004); destructive ops return the pre-state they captured under the lock, and a restore path re-creates a deleted item as a fresh row
├── shopping_list_sync_service.dart     # Background sync: scheduler-driven poll (fetch+reconcile) and a decoupled drain timer draining the outbox, routed through the store (ADR-0005)
├── shopping_list_list_service.dart     # Shopping list list business logic with ValueNotifier
├── shopping_list_detail_service.dart   # Shopping list detail business logic; drives local item add/edit/check/delete/reorder and bulk ops; holds the single-slot pending undo and replays it through the store
├── undoable_action.dart                # Sealed capture of the last destructive action's inverse: DeletedItemsUndo (full pre-state) / UncheckedItemsUndo (affected ids)
├── shopping_list_item_import_service.dart # Bulk-imports reviewed generated items into a chosen (possibly non-open) list via the store, then requests a sync drain
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
