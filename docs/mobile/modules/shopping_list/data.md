# Shopping List — On-Device Data

sqflite database `shopping_list_items.db` in the app documents directory, schema version 1.

## Tables

### items

- local_id: TEXT PRIMARY KEY — device-generated id; stable for the item's life on the device
- server_id: TEXT NULL — the backend item id; null until the item's create is acknowledged
- list_id: TEXT NOT NULL — the backend shopping list id
- name: TEXT NOT NULL
- quantity: REAL NULL
- unit: TEXT NULL
- checked: INTEGER NOT NULL DEFAULT 0 — boolean
- position: REAL NOT NULL — sort key, as on the backend
- last_acked_version: INTEGER NULL — the backend `version` last acknowledged for the item; null until created on
  the backend
- dirty: INTEGER NOT NULL DEFAULT 0 — boolean; the item has outbox entries not yet acknowledged
- failed: INTEGER NOT NULL DEFAULT 0 — boolean; always written as 0
- pending_delete: INTEGER NOT NULL DEFAULT 0 — boolean; deleted on the device, hidden, and waiting for the backend
  delete

### outbox

- seq: INTEGER PRIMARY KEY AUTOINCREMENT — push order
- item_local_id: TEXT NOT NULL — the item the entry changes
- list_id: TEXT NOT NULL — the item's list
- kind: TEXT NOT NULL — `create`, `update` or `delete`
- payload: TEXT NOT NULL — JSON; for `create` and `update` the item's full mutable field set (`name`, `quantity`,
  `unit`, `checked`, `position`), for `delete` `{}`

## Relationships

- **outbox.item_local_id** → **items.local_id**: Many-to-One, no foreign key
- **items.list_id**, **outbox.list_id** → backend `shopping_lists.id` (`docs/backend/modules/shopping-lists/db.md`);
  no local lists table
- **items.server_id** → backend `shopping_list_items.id`

## Indexes

- Primary key indexes on both tables
- Index on `items(list_id)`
- Index on `outbox(item_local_id)`
