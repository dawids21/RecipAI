# Database Schema - RecipAI

## Tables

### recipes

- id: UUID PRIMARY KEY
- name: VARCHAR(255) NOT NULL
- data: JSONB NOT NULL

### user_recipes

- email: VARCHAR(255) NOT NULL
- recipe_id: UUID NOT NULL (FK -> recipes.id)
- role: VARCHAR(255) NOT NULL CHECK (role IN ('OWNER', 'EDITOR'))
- PRIMARY KEY (email, recipe_id)

### shopping_lists

- id: UUID PRIMARY KEY
- name: VARCHAR(255) NOT NULL
- version: BIGINT NOT NULL

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
- position: INT NOT NULL

### shopping_list_item_checkbox

- shopping_list_item_id: UUID PRIMARY KEY (FK -> shopping_list_items.id)
- checked: BOOLEAN NOT NULL DEFAULT FALSE
- version: BIGINT NOT NULL

## Relationships

- **user_recipes** ↔ **recipes**: Many-to-Many relationship through `user_recipes` join table with role-based access
    - One user (identified by email) can have many recipes with different roles
    - One recipe can belong to multiple users with different access levels (sharing functionality)
    - **OWNER**: Can view, edit, delete, share, and unshare recipes
    - **EDITOR**: Can view, edit, share, unshare recipes (granted through sharing)
- **user_recipes.recipe_id** → **recipes.id**: Foreign key relationship
- **shopping_list_permission** ↔ **shopping_lists**: Many-to-Many relationship through `shopping_list_permission` join
  table with role-based access
    - One user (identified by email) can have many shopping lists with different roles
    - One shopping list can belong to multiple users with different access levels
- **shopping_list_permission.shopping_list_id** → **shopping_lists.id**: Foreign key relationship
- **shopping_list_items** → **shopping_lists**: One-to-Many relationship
    - One shopping list can have many items
    - Items are ordered by the `position` field
    - When a shopping list is deleted, all its items are deleted (CASCADE)
- **shopping_list_items.shopping_list_id** → **shopping_lists.id**: Foreign key relationship with ON DELETE CASCADE
- **shopping_list_item_checkbox** → **shopping_list_items**: One-to-One relationship
    - Each shopping list item can have one checkbox state record
    - The checkbox state is stored separately from the item to allow independent optimistic locking
    - When an item is deleted, its checkbox is also deleted (CASCADE)

## Indexes

- Primary key indexes on all tables
- Composite primary key index on `user_recipes(email, recipe_id)`
- Composite primary key index on `shopping_list_permission(email, shopping_list_id)`
