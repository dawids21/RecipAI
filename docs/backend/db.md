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

## Relationships

- **user_recipes** ↔ **recipes**: Many-to-Many relationship through `user_recipes` join table with role-based access
    - One user (identified by email) can have many recipes with different roles
    - One recipe can belong to multiple users with different access levels (sharing functionality)
    - **OWNER**: Can view, edit, delete, share, and unshare recipes
    - **EDITOR**: Can view, edit, share, unshare recipes (granted through sharing)
- **user_recipes.recipe_id** → **recipes.id**: Foreign key relationship

## Indexes

- Primary key indexes on all tables
- Composite primary key index on `user_recipes(email, recipe_id)`
