# Recipes & Collections API

Creating a recipe or a collection consumes one unit of the owner's `RECIPE` or `RECIPES_COLLECTION`
budget, reserved *before* anything is written and keyed by the `email` claim of the JWT. Deleting one
returns the unit. Both are stock caps: a refusal does not resolve itself by waiting, and only creation
is blocked — reading, editing and sharing keep working while the owner is over the cap. Sharing never
charges the recipient, and editing a shared record spends nothing; a recipe an EDITOR creates in someone
else's collection is charged to the EDITOR, who owns it. See `docs/backend/modules/limits/` for how the
caps are configured and changed.

## Refusal Response

A create past the cap returns **429 Too Many Requests** with an RFC 7807 `ProblemDetail`:

```json
{
  "type": "about:blank",
  "title": "Limit Exceeded",
  "status": 429,
  "detail": "Limit for RECIPE reached (5 of 5 used)",
  "resource": "RECIPE",
  "kind": "STOCK",
  "limit": 5,
  "used": 5
}
```

Neither `retryAfterSeconds` nor the `Retry-After` header is present, because a stock cap never
restarts — the owner has to delete something or have the cap raised.

## Recipes

### GET /recipes
- Description: Get recipes as list with basic info, with optional filtering by collection or unassigned status. Results are ordered by creation date (oldest first).
- Authenticated: true
- Query parameters:
    - `collectionId` (UUID, optional): Filter recipes by collection ID
    - `unassigned` (boolean, optional): Filter recipes not assigned to any collection (use `true` to enable)
    - Note: `collectionId` and `unassigned` are mutually exclusive and cannot be specified together
- Behavior:
    - No parameters: Returns all recipes accessible by the user (either through direct permission or collection permission)
    - With `collectionId`: Returns only recipes in the specified collection (requires user to have access to the collection)
    - With `unassigned=true`: Returns recipes that the user has direct permission to access and are either: (1) not assigned to any collection, or (2) assigned to a collection the user does not have access to
    - All results are ordered by creation date in ascending order (oldest first)
- Example response:
  ```json
  [
    {
      "id": "uuid",
      "name": "Pizza",
      "thumbnailUrl": "https://s3.amazonaws.com/..."
    },
    {
      "id": "uuid",
      "name": "Spaghetti",
      "thumbnailUrl": null
    }
  ]
  ```
- Success: 200 OK
- Errors: 400 Bad Request (if both collectionId and unassigned are specified), 403 Forbidden (if user lacks access to specified collection), 404 Not Found (if collection doesn't exist)

### GET /recipes/{uuid}
- Description: Get recipe by UUID
- Authenticated: true
- Example response:
  ```json
  {
    "id": "uuid",
    "name": "Pizza",
    "data": {
      "ingredients": [
        {
          "name": "flour",
          "quantity": 300,
          "unit": "g"
        },
        {
          "name": "tomato sauce",
          "quantity": 200,
          "unit": "ml"
        },
        {
          "name": "salt",
          "comment": "to taste"
        }
      ],
      "instructions": [
        {
          "step": "Make dough"
        },
        {
          "step": "Add sauce and toppings"
        }
      ],
      "sourceUrl": "https://example.com/recipe/pizza",
      "servingSize": 4
    },
    "role": "OWNER",
    "collectionId": "550e8400-e29b-41d4-a716-446655440000",
    "collectionName": "Italian Recipes",
    "images": [
      {
        "id": "image-uuid-1",
        "url": "https://s3.amazonaws.com/recipes/uuid/image-uuid-1.jpg",
        "thumbnailUrl": "https://s3.amazonaws.com/recipes/uuid/image-uuid-1-thumb.jpg"
      },
      {
        "id": "image-uuid-2",
        "url": "https://s3.amazonaws.com/recipes/uuid/image-uuid-2.jpg",
        "thumbnailUrl": "https://s3.amazonaws.com/recipes/uuid/image-uuid-2-thumb.jpg"
      }
    ]
  }
  ```
- Success: 200 OK
- Errors: 403 Forbidden (if user lacks access to recipe), 404 Not Found
- Note: `role` field indicates user's access level: "OWNER" (can view, edit, delete, share, unshare, and change collection assignment) or "EDITOR" (can view and edit only, cannot change collection assignment — attempts to change it are silently ignored). Users with access to a collection automatically receive EDITOR access to all recipes in that collection. `collectionId` and `collectionName` fields are null when recipe is not assigned to a collection. When the user does not have access to the assigned collection, `collectionId` is still returned but `collectionName` is null. The `sourceUrl` field in `data` is optional and contains the URL of the original recipe source. The `servingSize` field in `data` is optional (defaults to 1 if not provided) and must be a positive integer (1-100) if specified. The `images` array contains presigned S3 URLs valid for a limited time (configured by server). Maximum of 2 images per recipe.

### POST /recipes (JSON)
- Description: Add new recipe with JSON data
- Authenticated: true
- Content-Type: application/json
- Request body:
  ```json
  {
    "name": "Pizza",
    "recipesCollectionId": "550e8400-e29b-41d4-a716-446655440000",
    "data": {
      "ingredients": [
        {"name": "flour", "quantity": 300, "unit": "g"},
        {"name": "tomato sauce", "quantity": 200, "unit": "ml"},
        {"name": "salt", "comment": "to taste"}
      ],
      "instructions": [
        {"step": "Make dough"},
        {"step": "Add sauce and toppings"}
      ],
      "sourceUrl": "https://example.com/recipe/pizza",
      "servingSize": 4
    },
    "images": []
  }
  ```
- Example response: Same structure as GET /recipes/{uuid}
- Success: 201 Created
- Errors: 400 Bad request, 403 Forbidden (if user lacks access to specified collection), 404 Not Found (if collection doesn't exist), 429 Too many requests (recipe cap reached)
- Note: `recipesCollectionId`, `sourceUrl`, and `images` are optional. The `images` field is an array of UUIDs (max 2) for image metadata tracking when creating recipes via JSON. When `recipesCollectionId` is provided, user must have EDITOR or OWNER access to the collection.

### POST /recipes (Multipart)
- Description: Add new recipe with images
- Authenticated: true
- Content-Type: multipart/form-data
- Request parts:
    - `data` (JSON, required): Recipe data including `images` array with UUIDs matching image file names
    - `images` (files, optional): Recipe image files (JPEG/PNG, max 2 images, max 5MB each). Each file must be named with its corresponding UUID (e.g., `550e8400-e29b-41d4-a716-446655440000.jpg`)
- Example request data JSON:
  ```json
  {
    "name": "Pizza",
    "recipesCollectionId": "550e8400-e29b-41d4-a716-446655440000",
    "data": {"ingredients": [...], "instructions": [...], "sourceUrl": "...", "servingSize": 4},
    "images": ["image-uuid-1", "image-uuid-2"]
  }
  ```
- Example response: Same as JSON endpoint
- Success: 201 Created
- Errors:
    - 400 Bad request (invalid data, unsupported image format, image size exceeds 5MB, more than 2 images, or mismatch between image UUIDs and files)
    - 403 Forbidden (if user lacks access to specified collection)
    - 404 Not Found (if collection doesn't exist)
    - 429 Too many requests (recipe cap reached — nothing is written and no image is uploaded)
- Note: Images are stored in S3 and automatically resized to create thumbnails. Only JPEG and PNG formats are supported. Image files must be named with their UUID and appropriate extension. The extension in the filename is normalized (jpeg → jpg).

### PUT /recipes/{uuid} (JSON)
- Description: Update existing recipe by UUID
- Authenticated: true
- Content-Type: application/json
- Request body:
  ```json
  {
    "name": "Updated Pizza",
    "recipesCollectionId": "550e8400-e29b-41d4-a716-446655440000",
    "data": {
      "ingredients": [
        {"name": "flour", "quantity": 400, "unit": "g"},
        {"name": "cheese", "quantity": 200, "unit": "g"}
      ],
      "instructions": [
        {"step": "Make better dough"},
        {"step": "Add cheese"}
      ],
      "sourceUrl": "https://example.com/recipe/updated-pizza",
      "servingSize": 6
    },
    "images": ["existing-uuid-1"]
  }
  ```
- Example response: Same structure as GET /recipes/{uuid}
- Success: 200 OK
- Errors: 400 Bad request, 403 Forbidden (if user lacks access to recipe or specified collection), 404 Not Found (if recipe or collection doesn't exist)
- Note: Both OWNER and EDITOR roles can update recipes, but only OWNER can change `recipesCollectionId`. If an EDITOR attempts to change `recipesCollectionId`, the request succeeds (200 OK) but the collection assignment remains unchanged. `recipesCollectionId`, `sourceUrl`, and `images` are optional and can be null. When null, no changes are made to those fields. When `recipesCollectionId` is explicitly null, recipe is removed from collection. When `images` is null, no image changes are made. When `images` is `[]`, all images are deleted. When `images` contains UUIDs, images are kept/reordered/deleted to match the list. This JSON endpoint supports delete and reorder operations only (no new image uploads).

### PUT /recipes/{uuid} (Multipart)
- Description: Update existing recipe with images by UUID
- Authenticated: true
- Content-Type: multipart/form-data
- Request parts:
    - `data` (JSON, required): Recipe data including optional `images` array with UUIDs
    - `images` (files, optional): New recipe image files (JPEG/PNG, max 2 images total, max 5MB each). Only include files for NEW images being added.
- Example request data JSON:
  ```json
  {
    "name": "Updated Pizza",
    "recipesCollectionId": "550e8400-e29b-41d4-a716-446655440000",
    "data": {"ingredients": [...], "instructions": [...], "sourceUrl": "...", "servingSize": 6},
    "images": ["existing-uuid-1", "new-uuid-2"]
  }
  ```
- Behavior:
    - `images` field null: No changes to images
    - `images` field `[]`: Delete all images
    - `images` field `[uuids]`: Keep/add/reorder/delete images based on UUIDs
    - Images not in the `images` array are DELETED from both S3 and DB
    - Images in the array matching existing images are RETAINED
    - Images in the array not matching existing images are ADDED (requires corresponding file in `images` part)
    - Order in the `images` array determines display order
- Example response: Same as JSON PUT endpoint
- Success: 200 OK
- Errors:
    - 400 Bad request (total image count exceeds 2, missing file for new image UUID, invalid image format/size)
    - 403 Forbidden (if user lacks OWNER/EDITOR access or lacks access to specified collection)
    - 404 Not Found (if recipe or collection doesn't exist)
- Note: Only files for NEW images should be included. Existing images are referenced by UUID only. Maximum of 2 images total per recipe. Only OWNER can change `recipesCollectionId`.

### DELETE /recipes/{uuid}
- Description: Delete recipe by UUID
- Authenticated: true
- Success: 204 No Content
- Errors: 403 Forbidden (if user is not OWNER of the recipe), 404 Not Found
- Note: Only OWNER role can delete recipes. Users with access via collection permission cannot delete recipes. When a recipe is deleted, a `RecipeDeleted` event is published and the owner's `RECIPE` unit is returned.

### GET /recipes/{uuid}/shared_users
- Description: Get all users that a recipe is shared with, including their roles
- Authenticated: true
- Example response:
  ```json
  [
    {"email": "owner@example.com", "role": "OWNER"},
    {"email": "editor@example.com", "role": "EDITOR"}
  ]
  ```
- Success: 200 OK
- Errors: 403 Forbidden (if user lacks access to recipe), 404 Not Found
- Note: OWNER appears first in the returned list. Users can access this endpoint if they have direct recipe permission or access to the collection containing the recipe.

### POST /recipes/{uuid}/share
- Description: Share recipe with another user (grants EDITOR access)
- Authenticated: true
- Request body: `{"email": "user@example.com"}`
- Success: 200 OK
- Errors: 400 Bad request, 403 Forbidden (if user has no access to the recipe), 404 Not Found
- Note: Shared user receives EDITOR access. Users with access to a collection can share recipes within that collection.

### POST /recipes/{uuid}/unshare
- Description: Remove shared access from another user
- Authenticated: true
- Request body: `{"email": "user@example.com"}`
- Success: 200 OK
- Errors: 400 Bad request, 403 Forbidden (if user has no access to the recipe or EDITOR tries to unshare from OWNER), 404 Not Found
- Note: Removes EDITOR access from target user.

---

## Recipe Collections

### GET /collections
- Description: Get all recipes collections accessible by the authenticated user, ordered by creation date (oldest first)
- Authenticated: true
- Example response:
  ```json
  [
    {"id": "550e8400-e29b-41d4-a716-446655440000", "name": "Italian Recipes"},
    {"id": "660e8400-e29b-41d4-a716-446655440001", "name": "Asian Recipes"}
  ]
  ```
- Success: 200 OK
- Errors: 401 Unauthorized

### POST /collections
- Description: Create a new recipes collection and grant OWNER permission to the authenticated user
- Authenticated: true
- Request body: `{"name": "My Collection"}`
- Example response: `{"id": "uuid", "name": "My Collection"}`
- Success: 201 Created
- Errors: 400 Bad Request (blank name), 401 Unauthorized, 429 Too many requests (collection cap reached)

### PUT /collections/{id}
- Description: Update the name of an existing recipes collection
- Authenticated: true
- Roles: OWNER and EDITOR can update
- Request body: `{"name": "Updated Collection Name"}`
- Example response: `{"id": "uuid", "name": "Updated Collection Name"}`
- Success: 200 OK
- Errors: 400 Bad Request, 401 Unauthorized, 403 Forbidden (user lacks EDITOR/OWNER permission), 404 Not Found

### DELETE /collections/{id}
- Description: Delete a recipes collection and all associated permissions
- Authenticated: true
- Roles: Only OWNER can delete
- Success: 204 No Content
- Errors: 401 Unauthorized, 403 Forbidden (user is not OWNER), 404 Not Found
- Note: Deletes the collection and all permissions, and returns the owner's `RECIPES_COLLECTION` unit. Recipes in the collection have their `recipes_collection_id` set to null (ON DELETE SET NULL) — they are not deleted, so no recipe unit is returned.

### GET /collections/{id}/users
- Description: Get all users that a recipes collection is shared with, including their roles
- Authenticated: true
- Example response:
  ```json
  [
    {"email": "owner@example.com", "role": "OWNER"},
    {"email": "editor@example.com", "role": "EDITOR"}
  ]
  ```
- Success: 200 OK
- Errors: 401 Unauthorized, 403 Forbidden (if user lacks access), 404 Not Found
- Note: OWNER appears first in the returned list.

### POST /collections/{id}/share
- Description: Share recipes collection with another user (grants EDITOR access)
- Authenticated: true
- Request body: `{"email": "user@example.com"}`
- Success: 204 No Content
- Errors: 400 Bad Request (invalid email format), 401 Unauthorized, 403 Forbidden, 404 Not Found
- Note: Shared user receives EDITOR access. Duplicate shares are silently ignored (idempotent).

### POST /collections/{id}/unshare
- Description: Remove shared access from a user
- Authenticated: true
- Request body: `{"email": "user@example.com"}`
- Success: 204 No Content
- Errors: 400 Bad Request (invalid email format), 401 Unauthorized, 403 Forbidden (if user has no access, or trying to unshare OWNER), 404 Not Found
- Note: EDITOR can unshare EDITORs (including self); EDITOR cannot remove OWNER; OWNER cannot remove themselves. When a collection is unshared from a user, all recipes owned by that user in the collection are automatically removed from the collection (`recipesCollectionId` set to null).
