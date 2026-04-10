# Planning — Database Schema

## Tables

### meal_plans

- id: UUID PRIMARY KEY
- name: VARCHAR(255) NOT NULL
- color: VARCHAR(7) NOT NULL
- created_at: TIMESTAMP NOT NULL

### meal_plan_permissions

- email: VARCHAR(255) NOT NULL
- plan_id: UUID NOT NULL (FK -> meal_plans.id)
- role: VARCHAR(255) NOT NULL CHECK (role IN ('OWNER', 'EDITOR'))
- PRIMARY KEY (email, plan_id)

### meal_plan_entries

- id: BIGSERIAL PRIMARY KEY
- plan_id: UUID NOT NULL (FK -> meal_plans.id, ON DELETE CASCADE)
- date: DATE NOT NULL
- recipe_id: UUID NULL
- placeholder_text: VARCHAR(255) NULL
- serving_size: INTEGER NULL
- created_at: TIMESTAMP NOT NULL

## Relationships

- **meal_plan_permissions** ↔ **meal_plans**: Many-to-Many through `meal_plan_permissions` join table with role-based access
    - One user (identified by email) can have many meal plans with different roles
    - One meal plan can belong to multiple users with different access levels
    - **OWNER**: Can view, edit, delete meal plans
    - **EDITOR**: Can view and edit meal plans
- **meal_plan_permissions.plan_id** → **meal_plans.id**: Foreign key relationship
- **meal_plan_entries** → **meal_plans**: One-to-Many relationship
    - One meal plan can have many entries
    - When a meal plan is deleted, all its entries are deleted (CASCADE)
- **meal_plan_entries.plan_id** → **meal_plans.id**: Foreign key with ON DELETE CASCADE
- **meal_plan_entries.recipe_id** → **recipes.id**: Optional foreign key with ON DELETE SET NULL
    - When a recipe is deleted, `recipe_id` is set to null and `placeholder_text` is set to the original recipe name (handled at application level by listening to the `RecipeDeleted` event)

## Indexes

- Primary key indexes on all tables
- Composite primary key index on `meal_plan_permissions(email, plan_id)`
- Index on `meal_plans(created_at)` — for ordering meal plans by creation date
- Composite index on `meal_plan_entries(plan_id, date)` — for querying entries by plan and date
