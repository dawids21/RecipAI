# Planning — Database Schema

## Tables

### meal_plans

- id: UUID PRIMARY KEY
- name: VARCHAR(255) NOT NULL
- color: VARCHAR(7) NOT NULL
- created_at: TIMESTAMP NOT NULL

Access to a meal plan, and any pending invite to one, are recorded in `permissions`'
`resource_permission` and `resource_invite` tables under the `MEAL_PLAN` resource type — see
`docs/backend/modules/permissions/db.md`.

### meal_plan_entries

- id: BIGSERIAL PRIMARY KEY
- plan_id: UUID NOT NULL (FK -> meal_plans.id, ON DELETE CASCADE)
- date: DATE NOT NULL
- recipe_id: UUID NULL
- placeholder_text: VARCHAR(255) NULL
- serving_size: INTEGER NULL
- created_at: TIMESTAMP NOT NULL

## Relationships

- **meal_plan_entries** → **meal_plans**: One-to-Many relationship
    - One meal plan can have many entries
    - When a meal plan is deleted, all its entries are deleted (CASCADE)
- **meal_plan_entries.plan_id** → **meal_plans.id**: Foreign key with ON DELETE CASCADE
- **meal_plan_entries.recipe_id** → **recipes.id**: Optional foreign key with ON DELETE SET NULL
    - When a recipe is deleted, `recipe_id` is set to null and `placeholder_text` is set to the original recipe name (handled at application level by listening to the `RecipeDeleted` event)

## Indexes

- Primary key indexes on all tables
- Index on `meal_plans(created_at)` — for ordering meal plans by creation date
- Composite index on `meal_plan_entries(plan_id, date)` — for querying entries by plan and date
