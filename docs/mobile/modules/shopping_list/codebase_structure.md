# Shopping List — Codebase Structure

```
mobile/lib/features/shopping_list/
├── shopping_list.dart                  # Shopping list data model
├── shopping_list_item.dart             # Shopping list item data model
├── shopping_list_repository.dart       # Shopping list data access layer
├── shopping_list_list_service.dart     # Shopping list list business logic with ValueNotifier
├── shopping_list_detail_service.dart   # Shopping list detail business logic with optimistic updates
├── shopping_list_sync_service.dart     # Background sync service with operation queue and conflict handling
├── shopping_list_operation.dart        # Operation models for optimistic UI updates (sealed class: Add/Delete/Move/Check/Uncheck/Update)
├── shopping_list_setup.dart            # Dependency injection setup for shopping list module
├── shopping_list_list.dart             # Reusable shopping list body widget
├── shopping_list_list_fab.dart         # Reusable shopping list FAB widget
├── shopping_list_item_widget.dart      # Reusable inline-editable item widget using ItemDisplayData; supports strikethrough, subtitle, optional delete button
├── shopping_list_item_parser.dart      # Regex-based parser for "quantity unit name" text format
├── shopping_list_item_add_widget.dart  # Dedicated widget for adding new items
├── shopping_list_review_item.dart      # Mutable generated item model (name/quantity/unit mutable, source immutable)
├── shopping_list_review_widget.dart    # Review widget with ReorderableListView, inline editing, and checkbox selection
├── shopping_list_review_service.dart   # Service for submitting selected generated items to a shopping list
└── shopping_list_detail_screen.dart    # Shopping list detail screen
```
