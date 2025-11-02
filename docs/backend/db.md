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

### shopping_list_items

- id: UUID PRIMARY KEY
- list_id: UUID NOT NULL (FK -> shopping_lists.id)
- name: VARCHAR(255) NOT NULL
- quantity: NUMERIC(12,3) NULL
- unit: VARCHAR(64) NULL
- checked: BOOLEAN NOT NULL DEFAULT FALSE
- position: INT NOT NULL
- version: BIGINT NOT NULL

## Relationships

- **user_recipes** ↔ **recipes**: Many-to-Many relationship through `user_recipes` join table with role-based access
    - One user (identified by email) can have many recipes with different roles
    - One recipe can belong to multiple users with different access levels (sharing functionality)
    - **OWNER**: Can view, edit, delete, share, and unshare recipes
    - **EDITOR**: Can view, edit, share, unshare recipes (granted through sharing)
- **user_recipes.recipe_id** → **recipes.id**: Foreign key relationship
- **shopping_list_items** → **shopping_lists**: One-to-Many relationship
    - One shopping list can have many items
    - Items are ordered by the `position` field
    - When a shopping list is deleted, all its items are deleted (CASCADE)
- **shopping_list_items.list_id** → **shopping_lists.id**: Foreign key relationship with ON DELETE CASCADE

## Indexes

- Primary key indexes on all tables
- Composite primary key index on `user_recipes(email, recipe_id)`
