# Shopping Lists — Database Schema

## Tables

### shopping_lists

- id: UUID PRIMARY KEY
- name: VARCHAR(255) NOT NULL
- created_at: TIMESTAMP NOT NULL

### shopping_list_permission

- email: VARCHAR(255) NOT NULL
- shopping_list_id: UUID NOT NULL (FK -> shopping_lists.id)
- role: VARCHAR(255) NOT NULL CHECK (role IN ('OWNER', 'EDITOR'))
- PRIMARY KEY (email, shopping_list_id)

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

- **shopping_list_permission** ↔ **shopping_lists**: Many-to-Many through `shopping_list_permission` join table with role-based access
    - One user (identified by email) can have many shopping lists with different roles
    - One shopping list can belong to multiple users with different access levels
- **shopping_list_permission.shopping_list_id** → **shopping_lists.id**: Foreign key relationship
- **shopping_list_items** → **shopping_lists**: One-to-Many relationship
    - One shopping list can have many items
    - Items are ordered by `position` ascending, ties broken by `id` ascending
    - When a shopping list is deleted, all its items are deleted (CASCADE)
- **shopping_list_items.shopping_list_id** → **shopping_lists.id**: Foreign key with ON DELETE CASCADE

## Indexes

- Primary key indexes on all tables
- Composite primary key index on `shopping_list_permission(email, shopping_list_id)`
- Index on `shopping_lists(created_at)` — for ordering shopping lists by creation date
