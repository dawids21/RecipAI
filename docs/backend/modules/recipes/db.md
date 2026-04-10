# Recipes & Collections — Database Schema

## Tables

### recipes

- id: UUID PRIMARY KEY
- name: VARCHAR(255) NOT NULL
- data: JSONB NOT NULL
- recipes_collection_id: UUID NULL (FK -> recipes_collections.id)
- created_at: TIMESTAMP NOT NULL

#### recipes.data JSONB structure:

```json
{
  "ingredients": [
    {
      "name": "string",
      "quantity": "string",
      "unit": "string",
      "comment": "string (optional)"
    }
  ],
  "instructions": [
    {
      "step": "string"
    }
  ],
  "sourceUrl": "string (optional, URL)",
  "servingSize": "integer (optional, positive 1-100, defaults to 1)"
}
```

- `servingSize`: Optional positive integer (1-100) representing number of servings (defaults to 1 if not provided)

### recipe_images

- id: UUID PRIMARY KEY (FK -> recipes.id)
- images: JSONB NOT NULL — Structure: `{"imagesMetadata": [{"id": "uuid", "type": "image/jpeg"}]}`
- version: BIGINT NOT NULL

### recipe_permission

- email: VARCHAR(255) NOT NULL
- recipe_id: UUID NOT NULL (FK -> recipes.id)
- role: VARCHAR(255) NOT NULL CHECK (role IN ('OWNER', 'EDITOR'))
- PRIMARY KEY (email, recipe_id)

### recipes_collections

- id: UUID PRIMARY KEY
- name: VARCHAR(255) NOT NULL
- created_at: TIMESTAMP NOT NULL

### recipes_collection_permission

- email: VARCHAR(255) NOT NULL
- recipes_collection_id: UUID NOT NULL (FK -> recipes_collections.id)
- role: VARCHAR(255) NOT NULL CHECK (role IN ('OWNER', 'EDITOR'))
- PRIMARY KEY (email, recipes_collection_id)

## Relationships

- **recipe_permission** ↔ **recipes**: Many-to-Many through `recipe_permission` join table with role-based access
    - One user (identified by email) can have many recipes with different roles
    - One recipe can belong to multiple users with different access levels
    - **OWNER**: Can view, edit, delete, share, and unshare recipes
    - **EDITOR**: Can view, edit, share, unshare recipes (granted through sharing)
- **recipe_permission.recipe_id** → **recipes.id**: Foreign key relationship
- **recipe_images** → **recipes**: One-to-One relationship
    - One recipe can have one recipe_images record storing metadata about associated images
    - The `images` JSONB field contains an object with an `imagesMetadata` array:
      `{"imagesMetadata": [{"id": "uuid", "type": "image/jpeg"}, {"id": "uuid2", "type": "image/png"}]}`
    - Each image metadata entry contains a UUID and a content type
    - Image order is determined by the array order (client-controlled via CreateRecipeRequest)
    - When a recipe is deleted, its recipe_images record is deleted (CASCADE)
    - Maximum of 2 images per recipe enforced at application level
- **recipe_images.id** → **recipes.id**: Foreign key with ON DELETE CASCADE
- **recipes_collection_permission** ↔ **recipes_collections**: Many-to-Many through `recipes_collection_permission` join table with role-based access
    - One user (identified by email) can have many collections with different roles
    - One collection can belong to multiple users with different access levels
    - **OWNER**: Can view, edit, delete collections
    - **EDITOR**: Can view and edit collections
- **recipes_collection_permission.recipes_collection_id** → **recipes_collections.id**: Foreign key relationship
- **recipes** → **recipes_collections**: Optional Many-to-One relationship
    - One recipe can optionally belong to one collection (via `recipes.recipes_collection_id`)
    - Many recipes can belong to the same collection
    - When a collection is deleted, the foreign key is set to NULL (ON DELETE SET NULL)
    - Recipe permissions and collection permissions remain independent (no automatic syncing)
- **recipes.recipes_collection_id** → **recipes_collections.id**: Foreign key with ON DELETE SET NULL

## Indexes

- Primary key indexes on all tables
- Composite primary key index on `recipe_permission(email, recipe_id)`
- Composite primary key index on `recipes_collection_permission(email, recipes_collection_id)`
- Index on `recipes(created_at)` — for ordering recipes by creation date
- Index on `recipes_collections(created_at)` — for ordering collections by creation date
