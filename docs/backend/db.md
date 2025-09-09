# Database Schema - RecipAI

## Tables

### users

- email: VARCHAR(255) PRIMARY KEY

### recipes

- id: UUID PRIMARY KEY
- name: VARCHAR(255) NOT NULL
- data: JSONB NOT NULL

### user_recipes

- email: VARCHAR(255) NOT NULL (FK -> users.email)
- recipe_id: UUID NOT NULL (FK -> recipes.id)
- PRIMARY KEY (email, recipe_id)

## Relationships

- **users** ↔ **recipes**: Many-to-Many relationship through `user_recipes` join table
    - One user can have many recipes
    - One recipe can belong to multiple users (sharing functionality)
- **user_recipes.email** → **users.email**: Foreign key relationship
- **user_recipes.recipe_id** → **recipes.id**: Foreign key relationship

## Indexes

- Primary key indexes on all tables
- Composite primary key index on `user_recipes(email, recipe_id)`
