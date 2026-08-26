# Shopping Lists — Database Schema

## Tables

### shopping_lists

- id: UUID PRIMARY KEY
- name: VARCHAR(255) NOT NULL
- created_at: TIMESTAMP NOT NULL

### shopping_list_items

- id: UUID PRIMARY KEY
- shopping_list_id: UUID NOT NULL (FK -> shopping_lists.id)
- name: VARCHAR(255) NOT NULL
- quantity: NUMERIC(12,3) NULL
- unit: VARCHAR(64) NULL
- checked: BOOLEAN NOT NULL DEFAULT FALSE
- position: NUMERIC(21,12) NOT NULL — a non-unique sort key; ties are broken by `id` at the read path
- version: BIGINT NOT NULL — optimistic-locking version, the conflict gate for item writes

## Relationships

- **shopping_list_items** → **shopping_lists**: One-to-Many relationship
    - One shopping list can have many items
    - Items are ordered by `position` ascending, ties broken by `id` ascending
    - When a shopping list is deleted, all its items are deleted (CASCADE)
- **shopping_list_items.shopping_list_id** → **shopping_lists.id**: Foreign key with ON DELETE CASCADE

Who may access a list, and any pending invite to one, live in `resource_permission` and
`resource_invite` — see `docs/backend/modules/permissions/db.md`
(`resource_type = 'SHOPPING_LIST'`, `resource_id = shopping_lists.id`). The `shopping_list_permission`
table is present in the database, copied into `resource_permission` by `permissions`' `V20__`
migration, but is unread and unwritten; it is dropped once collections and meal plans finish migrating
(`docs/tasks/2026-08-26-share-invites/tasks.md`).

## Indexes

- Primary key indexes on both tables
- Index on `shopping_lists(created_at)` — for ordering shopping lists by creation date
