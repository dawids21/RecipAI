# REST API Documentation - RecipAI

## Resources

- Recipes: Maps the `recipes` DB table with user-scoped access.
- Shopping Lists: Maps the `shopping_lists` DB table for managing shopping lists.
- Collections: Maps the `recipes_collections` DB table for organizing recipes into groups.

## Endpoints

### Recipes

- GET /recipes
    - Description: Get recipes as list with basic info, with optional filtering by collection or unassigned status.
      Results are ordered by creation date (oldest first).
    - Authenticated: true
    - Query parameters:
        - `collectionId` (UUID, optional): Filter recipes by collection ID
        - `unassigned` (boolean, optional): Filter recipes not assigned to any collection (use `true` to enable)
        - Note: `collectionId` and `unassigned` are mutually exclusive and cannot be specified together
    - Behavior:
        - No parameters: Returns all recipes accessible by the user (either through direct permission or collection
          permission)
        - With `collectionId`: Returns only recipes in the specified collection (requires user to have access to the
          collection)
        - With `unassigned=true`: Returns recipes that the user has direct permission to access and are either: (1) not
          assigned to any collection, or (2) assigned to a collection the user does not have access to
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
    - Errors: 400 Bad Request (if both collectionId and unassigned are specified), 403 Forbidden (if user lacks access
      to specified collection), 404 Not Found (if collection doesn't exist)
- GET /recipes/{uuid}
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
              "quantity": "300",
              "unit": "g"
            },
            {
              "name": "tomato sauce",
              "quantity": "200",
              "unit": "ml"
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
          "sourceUrl": "https://example.com/recipe/pizza"
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
  - Note: `role` field indicates user's access level: "OWNER" (can view, edit, delete, share, unshare, and change
    collection assignment) or "EDITOR" (can view and edit only, cannot change collection assignment - attempts to
    change
    it are silently ignored). Users with access to a collection automatically receive EDITOR access to all recipes in
    that collection. `collectionId` and `collectionName` fields are null when recipe is not assigned to a
    collection.                                                      
    When the user does not have access to the assigned collection, `collectionId` is still returned
    but `collectionName` is null. The `sourceUrl` field in `data` is optional and contains the URL of
    the original recipe source. The `images` array contains presigned S3 URLs that are valid for a limited time
    (configured by server). Maximum of 2 images per recipe.
- POST /recipes (JSON)
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
            {
              "name": "flour",
              "quantity": "300",
              "unit": "g"
            },
            {
              "name": "tomato sauce",
              "quantity": "200",
              "unit": "ml"
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
          "sourceUrl": "https://example.com/recipe/pizza"
        },
        "images": []
      }
      ```
    - Example response:
      ```json
      {
        "id": "uuid",
        "name": "Pizza",
        "data": {
          "ingredients": [
            {
              "name": "flour",
              "quantity": "300",
              "unit": "g"
            },
            {
              "name": "tomato sauce",
              "quantity": "200",
              "unit": "ml"
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
          "sourceUrl": "https://example.com/recipe/pizza"
        },
        "role": "OWNER",
        "collectionId": "550e8400-e29b-41d4-a716-446655440000",
        "collectionName": "Italian Recipes",
        "images": []
      }
      ```
    - Success: 201 Created
  - Errors: 400 Bad request, 403 Forbidden (if user lacks access to specified collection), 404 Not Found (if
    collection
    doesn't exist)
      - Note: `recipesCollectionId`, `sourceUrl`, and `images` are optional and can be null or empty. The `images`
        field
        is an array of UUIDs (max 2) for image metadata tracking when creating recipes via JSON. When
        `recipesCollectionId` is provided, user must have EDITOR or OWNER access to the collection. Response includes
        `collectionId` and `collectionName` when recipe is assigned to a collection
- POST /recipes (Multipart)
    - Description: Add new recipe with images
    - Authenticated: true
    - Content-Type: multipart/form-data
    - Request parts:
        - `data` (JSON, required): Recipe data including `images` array with UUIDs matching image file names
        - `images` (files, optional): Recipe image files (JPEG/PNG, max 2 images, max 5MB each). Each file must be named
          with its corresponding UUID from the `images` array (e.g., `550e8400-e29b-41d4-a716-446655440000.jpg`)
  - Example request data JSON:
    ```json
    {
      "name": "Pizza",
      "recipesCollectionId": "550e8400-e29b-41d4-a716-446655440000",
      "data": {
        "ingredients": [...],
        "instructions": [...],
        "sourceUrl": "https://example.com/recipe/pizza"
      },
      "images": ["image-uuid-1", "image-uuid-2"]
    }
    ```
      - Example response: Same as JSON endpoint
      - Success: 201 Created
      - Errors:
          - 400 Bad request (invalid data, unsupported image format, image size exceeds 5MB, more than 2 images, or
            mismatch between image UUIDs and files)
          - 403 Forbidden (if user lacks access to specified collection)
          - 404 Not Found (if collection doesn't exist)
      - Note: Images are stored in S3 and automatically resized to create thumbnails. Maximum of 2 images per recipe.
        Only
        JPEG and PNG formats are supported. Image files must be named with their UUID and appropriate extension. The
        extension in the filename is normalized (jpeg → jpg).
- PUT /recipes/{uuid} (JSON)
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
              {
                "name": "flour",
                "quantity": "400",
                "unit": "g"
              },
              {
                "name": "cheese",
                "quantity": "200",
                "unit": "g"
              }
            ],
            "instructions": [
              {
                "step": "Make better dough"
              },
              {
                "step": "Add cheese"
              }
            ],
            "sourceUrl": "https://example.com/recipe/updated-pizza"
          },
          "images": ["existing-uuid-1"]
        }
        ```
      - Example response:
        ```json
        {
          "id": "uuid",
          "name": "Updated Pizza",
          "data": {
            "ingredients": [
              {
                "name": "flour",
                "quantity": "400",
                "unit": "g"
              },
              {
                "name": "cheese",
                "quantity": "200",
                "unit": "g"
              }
            ],
            "instructions": [
              {
                "step": "Make better dough"
              },
              {
                "step": "Add cheese"
              }
            ],
            "sourceUrl": "https://example.com/recipe/updated-pizza"
          },
          "role": "OWNER",
          "collectionId": "550e8400-e29b-41d4-a716-446655440000",
          "collectionName": "Italian Recipes",
          "images": [
            {
              "id": "existing-uuid-1",
              "url": "https://s3.amazonaws.com/recipes/uuid/existing-uuid-1.jpg",
              "thumbnailUrl": "https://s3.amazonaws.com/recipes/uuid/existing-uuid-1-thumb.jpg"
            }
          ]
        }
        ```
      - Success: 200 OK
      - Errors: 403 Forbidden (if user lacks access to recipe or specified collection), 404 Not Found (if recipe or
        collection doesn't exist), 400 Bad request
      - Note: Both OWNER and EDITOR roles can update recipes, but only OWNER can change the `recipesCollectionId`
        field.
        If an EDITOR attempts to change `recipesCollectionId`, the request succeeds (200 OK) but the collection
        assignment
        remains unchanged. Users with access to a collection automatically receive EDITOR access to all recipes in
        that
        collection. `recipesCollectionId`, `sourceUrl`, and `images` are optional and can be null. When null, no
        changes
        are made to those fields. When `recipesCollectionId` is null, recipe is removed from
        collection. When `images` is null, no image changes are made. When `images` is an empty array [], all images
        are
        deleted. When `images` contains UUIDs, images are kept/reordered/deleted to match the list. This JSON endpoint
        supports delete and reorder operations only (no new image uploads).
- PUT /recipes/{uuid} (Multipart)
    - Description: Update existing recipe with images by UUID
    - Authenticated: true
    - Content-Type: multipart/form-data
    - Request parts:
        - `data` (JSON, required): Recipe data including optional `images` array with UUIDs
        - `images` (files, optional): New recipe image files (JPEG/PNG, max 2 images total, max 5MB each). Only include
          files for NEW images being added.
    - Example request data JSON:
      ```json
      {
        "name": "Updated Pizza",
        "recipesCollectionId": "550e8400-e29b-41d4-a716-446655440000",
        "data": {
          "ingredients": [...],
          "instructions": [...],
          "sourceUrl": "https://example.com/recipe/updated-pizza"
        },
        "images": ["existing-uuid-1", "new-uuid-2"]
      }
      ```
    - Behavior:
        - `images` field null: No changes to images
        - `images` field []: Delete all images
        - `images` field [uuids]: Keep/add/reorder/delete images based on UUIDs
        - Images not in the `images` array are DELETED from both S3 and DB
        - Images in the array matching existing images are RETAINED
        - Images in the array not matching existing images are ADDED (requires corresponding file in `images` part)
        - Order in the `images` array determines display order
    - Example response: Same as JSON PUT endpoint
    - Success: 200 OK
    - Errors:
        - 400 Bad request (invalid data, total image count exceeds 2, missing file for new image UUID, invalid image
          format/size)
        - 403 Forbidden (if user lacks OWNER/EDITOR access or lacks access to specified collection)
        - 404 Not Found (if recipe or collection doesn't exist)
    - Note: Only files for NEW images should be included in the multipart request. Existing images are referenced by
      UUID only. Maximum of 2 images total per recipe. Both JPEG and PNG formats supported. Only OWNER can change
      `recipesCollectionId`; if EDITOR attempts to change it, the request succeeds but the collection assignment remains
      unchanged.
- DELETE /recipes/{uuid}
    - Description: Delete recipe by UUID
  - Authenticated: true
      - Example response: No content
      - Success: 204 No Content
      - Errors: 403 Forbidden (if user is not OWNER of the recipe), 404 Not Found
      - Note: Only OWNER role can delete recipes. Users with access via collection permission cannot delete recipes
- GET /recipes/{uuid}/shared_users
    - Description: Get all users that a recipe is shared with, including their roles
    - Authenticated: true
    - Example response:
      ```json
      [
        {
          "email": "owner@example.com",
          "role": "OWNER"
        },
        {
          "email": "editor@example.com",
          "role": "EDITOR"
        }
      ]
      ```
    - Success: 200 OK
    - Errors: 403 Forbidden (if user lacks access to recipe), 404 Not Found
  - Note: OWNER appears first in the returned list, followed by EDITOR roles. Users can access this endpoint if they
    have direct recipe permission or access to the collection containing the recipe

### Extraction

- POST /extract/text
    - Description: Extract recipe information from text
  - Authenticated: true
  - Request body:
    ```json
    {
      "text": "text with recipe for pizza"
    }
    ```
  - Example response:
    ```json
    {
      "name": "Pizza",
      "description": "Homemade pizza recipe",
      "ingredients": [
        {
          "name": "flour",
          "quantity": "300",
          "unit": "g"
        },
        {
          "name": "tomato sauce",
          "quantity": "200",
          "unit": "ml"
        }
      ],
      "instructions": [
        {
          "step": "Make dough"
        },
        {
          "step": "Add sauce and toppings"
        }
      ]
    }
    ```
  - Success: 200 OK
  - Errors: 400 Bad request

- POST /extract/image
    - Description: Extract recipe information from uploaded image file (JPEG/PNG)
    - Authenticated: true
    - Request: multipart/form-data with file parameter
    - Supported formats: JPEG, PNG
    - Example response:
      ```json
      {
        "name": "Veggie Burger",
        "description": "Delicious plant-based burger recipe",
        "ingredients": [
          {
            "name": "black beans",
            "quantity": "1",
            "unit": "cup"
          },
          {
            "name": "breadcrumbs", 
            "quantity": "1/2",
            "unit": "cup"
          }
        ],
        "instructions": [
          {
            "step": "Mash the black beans in a bowl"
          },
          {
            "step": "Mix in breadcrumbs and seasonings"
          }
        ]
      }
      ```
    - Success: 200 OK
    - Errors: 400 Bad request (unsupported file type), 413 Payload too large

- POST /recipes/{uuid}/share
    - Description: Share recipe with another user (grants EDITOR access)
    - Authenticated: true
    - Request body:
      ```json
      {
        "email": "user@example.com"
      }
      ```
    - Success: 200 OK
    - Errors: 403 Forbidden (if user has no access to the recipe), 404 Not Found, 400 Bad request
  - Note: Shared user receives EDITOR access. Users with access to a collection can share recipes within that
    collection.

- POST /recipes/{uuid}/unshare
    - Description: Remove shared access from another user
    - Authenticated: true
    - Request body:
      ```json
      {
        "email": "user@example.com"
      }
      ```
    - Success: 200 OK
    - Errors: 403 Forbidden (if user has no access to the recipe or EDITOR tries to unshare from OWNER), 404 Not Found,
      400 Bad request
  - Note: Removes EDITOR access from target user. Users with access to a collection can unshare recipes within that
    collection.

### Shopping Lists

- GET /shopping-lists
    - Description: Get all shopping lists ordered by creation date (oldest first)
    - Authenticated: true
    - Example response:
      ```json
      [
        {
          "id": "550e8400-e29b-41d4-a716-446655440000",
          "name": "Groceries"
        },
        {
          "id": "660e8400-e29b-41d4-a716-446655440001",
          "name": "Hardware"
        }
      ]
      ```
    - Success: 200 OK
    - Errors: 401 Unauthorized
- GET /shopping-lists/{id}
    - Description: Get a shopping list by ID with all its items
    - Authenticated: true
    - Path parameters:
        - `id` (UUID): Shopping list ID
    - Example response:
      ```json
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "name": "Weekly Groceries",
        "items": [
          {
            "id": "770e8400-e29b-41d4-a716-446655440010",
            "name": "Milk",
            "quantity": 2.0,
            "unit": "liters",
            "checked": false,
            "position": 1.0,
            "version": 0
          },
          {
            "id": "880e8400-e29b-41d4-a716-446655440011",
            "name": "Bread",
            "quantity": null,
            "unit": null,
            "checked": true,
            "position": 2.0,
            "version": 0
          }
        ],
        "role": "OWNER"
      }
      ```
    - Success: 200 OK
  - Errors: 403 Forbidden (user lacks permission), 404 Not Found, 401 Unauthorized
  - Note: Items are ordered by `position` in ascending order. Quantity and unit can be null. Returns explicit 403 when
    user has no access to the shopping list.
- POST /shopping-lists
    - Description: Create a new shopping list and grant OWNER permission to the authenticated user
    - Authenticated: true
    - Note: Automatically creates a permission record with OWNER role for the authenticated user
    - Request body:
      ```json
      {
        "name": "My Shopping List"
      }
      ```
    - Example response:
      ```json
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "name": "My Shopping List"
      }
      ```
    - Success: 201 Created
    - Errors: 400 Bad Request (validation error), 401 Unauthorized
- PUT /shopping-lists/{id}
    - Description: Update the name of an existing shopping list
    - Authenticated: true
    - Path parameters:
        - `id` (UUID): Shopping list ID
    - Roles: OWNER and EDITOR can update
    - Request body:
      ```json
      {
        "name": "Updated Shopping List Name"
      }
      ```
    - Example response:
      ```json
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "name": "Updated Shopping List Name"
      }
      ```
    - Success: 200 OK
    - Errors: 400 Bad Request (validation error), 401 Unauthorized, 403 Forbidden (user lacks permission), 404 Not Found
    - Note: Both OWNER and EDITOR roles can update the shopping list name
- DELETE /shopping-lists/{id}
    - Description: Delete a shopping list and all associated items and permissions
    - Authenticated: true
    - Path parameters:
        - `id` (UUID): Shopping list ID
    - Roles: Only OWNER can delete
    - Example response: No content
    - Success: 204 No Content
    - Errors: 401 Unauthorized, 403 Forbidden (user is not OWNER), 404 Not Found
    - Note: Deletes the shopping list, all items (via database CASCADE), and all permissions. Only OWNER role can
      delete.
- POST /shopping-lists/{shopping_list_id}/item
    - Description: Create a new item in a shopping list at the specified index or at the end
    - Authenticated: true
    - Path parameters:
        - `shopping_list_id` (UUID): Shopping list ID
    - Roles: OWNER and EDITOR can create items
    - Request body:
      ```json
      {
        "name": "Milk",
        "quantity": 2.0,
        "unit": "liters",
        "index": 0
      }
      ```
    - Request fields:
        - `name` (string, required): Item name (max 255 characters)
        - `quantity` (number, optional): Item quantity
        - `unit` (string, optional): Unit of measurement (max 64 characters)
        - `index` (integer, optional): 0-based index where to insert the item. If not provided, item is appended at the
          end
    - Behavior:
        - When `index` is not provided or null: Item is appended at the end (default behavior)
        - When `index` is 0: Item is inserted at the beginning
        - When `index` is between 0 and list size: Item is inserted at that position
        - When `index` >= list size: Item is appended at the end
    - Example response:
      ```json
      {
        "id": "770e8400-e29b-41d4-a716-446655440010",
        "name": "Milk",
        "quantity": 2.0,
        "unit": "liters",
        "checked": false,
        "position": 1.0,
        "version": 0
      }
      ```
    - Success: 201 Created
    - Errors:
        - 400 Bad Request (validation error - blank name, oversized fields, or negative index)
        - 401 Unauthorized
        - 403 Forbidden (user lacks EDITOR/OWNER permission)
        - 404 Not Found (shopping list doesn't exist)
    - Note: Position is calculated using the same fractional positioning algorithm as the move operation, allowing
      precise insertion between existing items. The `quantity` and `unit` fields are optional and can be null.
- DELETE /shopping-lists/{shopping_list_id}/item/{id}
    - Description: Delete an item from a shopping list
    - Authenticated: true
    - Path parameters:
        - `shopping_list_id` (UUID): Shopping list ID
        - `id` (UUID): Item ID
  - Headers:
      - `If-Match` (required): Version number of the item (obtained from GET request)
      - Roles: OWNER and EDITOR can delete items
      - Example response: No content
      - Success: 204 No Content
  - Errors:
      - 401 Unauthorized
      - 403 Forbidden (user lacks EDITOR/OWNER permission)
      - 404 Not Found (item not found or doesn't belong to the shopping list)
      - 412 Precondition Failed (version mismatch - item was modified by another user)
      - Note: EDITOR role is sufficient to delete items. Deleting an item does not renumber remaining items (gaps are
        allowed in positions). The `If-Match` header must contain the current version of the item to prevent
        concurrent
        modification conflicts.
- PUT /shopping-lists/{shopping_list_id}/item/{id}
    - Description: Update an item's name, quantity, and unit
    - Authenticated: true
    - Path parameters:
        - `shopping_list_id` (UUID): Shopping list ID
        - `id` (UUID): Item ID
    - Headers:
        - `If-Match` (required): Version number of the item (obtained from GET request)
    - Roles: OWNER and EDITOR can update items
    - Request body:
      ```json
      {
        "name": "Updated Name",
        "quantity": 3.0,
        "unit": "kg"
      }
      ```
    - Example response:
      ```json
      {
        "id": "770e8400-e29b-41d4-a716-446655440010",
        "name": "Updated Name",
        "quantity": 3.0,
        "unit": "kg",
        "checked": false,
        "position": 1.0,
        "version": 1
      }
      ```
    - Success: 200 OK
    - Errors:
        - 400 Bad Request (validation error - blank name or oversized fields)
        - 401 Unauthorized
        - 403 Forbidden (user lacks EDITOR/OWNER permission)
        - 404 Not Found (item not found or doesn't belong to the shopping list)
        - 412 Precondition Failed (version mismatch)
    - Note: The `If-Match` header must contain the current version. Version is automatically incremented on successful
      update.
- POST /shopping-lists/{shopping_list_id}/item/{id}/move
    - Description: Move an item to a different position in the shopping list by index
    - Authenticated: true
    - Path parameters:
        - `shopping_list_id` (UUID): Shopping list ID
        - `id` (UUID): Item ID
    - Headers:
        - `If-Match` (required): Version number of the item
    - Roles: OWNER and EDITOR can move items
    - Request body:
      ```json
      {
        "index": 2
      }
      ```
    - Example response:
      ```json
      {
        "id": "770e8400-e29b-41d4-a716-446655440010",
        "name": "Milk",
        "quantity": 2.0,
        "unit": "liters",
        "checked": false,
        "position": 2.500000,
        "version": 1
      }
      ```
    - Success: 200 OK
    - Errors:
        - 400 Bad Request (invalid index - negative number)
        - 401 Unauthorized
        - 403 Forbidden (user lacks EDITOR/OWNER permission)
        - 404 Not Found (item not found or doesn't belong to the shopping list)
        - 412 Precondition Failed (version mismatch)
    - Note: Index is 0-based. Moving to the same index is idempotent. Position values are automatically normalized to 6
      decimal places.
- POST /shopping-lists/{shopping_list_id}/item/{id}/check
    - Description: Mark an item as checked
    - Authenticated: true
    - Path parameters:
        - `shopping_list_id` (UUID): Shopping list ID
        - `id` (UUID): Item ID
    - Headers:
        - `If-Match` (required): Version number of the item
    - Roles: OWNER and EDITOR can check items
    - Request body: Empty
    - Example response:
      ```json
      {
        "id": "770e8400-e29b-41d4-a716-446655440010",
        "name": "Milk",
        "quantity": 2.0,
        "unit": "liters",
        "checked": true,
        "position": 1.0,
        "version": 1
      }
      ```
    - Success: 200 OK
    - Errors:
        - 401 Unauthorized
        - 403 Forbidden (user lacks EDITOR/OWNER permission)
        - 404 Not Found (item not found or doesn't belong to the shopping list)
        - 412 Precondition Failed (version mismatch)
    - Note: Idempotent - checking an already checked item has no effect.
- POST /shopping-lists/{shopping_list_id}/item/{id}/uncheck
    - Description: Mark an item as unchecked
    - Authenticated: true
    - Path parameters:
        - `shopping_list_id` (UUID): Shopping list ID
        - `id` (UUID): Item ID
    - Headers:
        - `If-Match` (required): Version number of the item
    - Roles: OWNER and EDITOR can uncheck items
    - Request body: Empty
    - Example response:
      ```json
      {
        "id": "770e8400-e29b-41d4-a716-446655440010",
        "name": "Milk",
        "quantity": 2.0,
        "unit": "liters",
        "checked": false,
        "position": 1.0,
        "version": 2
      }
      ```
    - Success: 200 OK
    - Errors:
        - 401 Unauthorized
        - 403 Forbidden (user lacks EDITOR/OWNER permission)
        - 404 Not Found (item not found or doesn't belong to the shopping list)
        - 412 Precondition Failed (version mismatch)
    - Note: Idempotent - unchecking an already unchecked item has no effect.
- GET /shopping-lists/{id}/users
    - Description: Get all users that a shopping list is shared with, including their roles
    - Authenticated: true
    - Path parameters:
        - `id` (UUID): Shopping list ID
    - Example response:
      ```json
      [
        {
          "email": "owner@example.com",
          "role": "OWNER"
        },
        {
          "email": "editor@example.com",
          "role": "EDITOR"
        }
      ]
      ```
    - Success: 200 OK
    - Errors: 403 Forbidden (if user lacks access), 404 Not Found
    - Note: OWNER appears first in the returned list, followed by EDITOR roles
- POST /shopping-lists/{id}/share
    - Description: Share shopping list with another user (grants EDITOR access)
    - Authenticated: true
    - Path parameters:
        - `id` (UUID): Shopping list ID
    - Request body:
      ```json
      {
        "email": "user@example.com"
      }
      ```
    - Success: 204 No Content
    - Errors: 403 Forbidden (if user has no access), 404 Not Found, 400 Bad Request
    - Note: Shared user receives EDITOR access. Duplicate shares are silently ignored.
- POST /shopping-lists/{id}/unshare
    - Description: Remove shared access from a user
    - Authenticated: true
    - Path parameters:
        - `id` (UUID): Shopping list ID
    - Request body:
      ```json
      {
        "email": "user@example.com"
      }
      ```
    - Success: 204 No Content
    - Errors: 403 Forbidden (if user has no access, or trying to unshare OWNER), 404 Not Found, 400 Bad Request
    - Note: EDITOR can unshare EDITORs (including self); EDITOR cannot remove OWNER; OWNER cannot remove themselves.

### Recipe Collections

- GET /collections
    - Description: Get all recipes collections accessible by the authenticated user, ordered by creation date (oldest
      first)
    - Authenticated: true
    - Example response:
      ```json
      [
        {
          "id": "550e8400-e29b-41d4-a716-446655440000",
          "name": "Italian Recipes"
        },
        {
          "id": "660e8400-e29b-41d4-a716-446655440001",
          "name": "Asian Recipes"
        }
      ]
      ```
    - Success: 200 OK
    - Errors: 401 Unauthorized
- POST /collections
    - Description: Create a new recipes collection and grant OWNER permission to the authenticated user
    - Authenticated: true
    - Note: Automatically creates a permission record with OWNER role for the authenticated user
    - Request body:
      ```json
      {
        "name": "My Collection"
      }
      ```
    - Example response:
      ```json
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "name": "My Collection"
      }
      ```
    - Success: 201 Created
    - Errors: 400 Bad Request (validation error - blank name), 401 Unauthorized
- PUT /collections/{id}
    - Description: Update the name of an existing recipes collection
    - Authenticated: true
    - Path parameters:
        - `id` (UUID): Collection ID
    - Roles: OWNER and EDITOR can update
    - Request body:
      ```json
      {
        "name": "Updated Collection Name"
      }
      ```
    - Example response:
      ```json
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "name": "Updated Collection Name"
      }
      ```
    - Success: 200 OK
    - Errors: 400 Bad Request (validation error), 401 Unauthorized, 403 Forbidden (user lacks EDITOR/OWNER permission),
      404 Not Found
    - Note: Both OWNER and EDITOR roles can update the collection name
- DELETE /collections/{id}
    - Description: Delete a recipes collection and all associated permissions
    - Authenticated: true
    - Path parameters:
        - `id` (UUID): Collection ID
    - Roles: Only OWNER can delete
    - Example response: No content
    - Success: 204 No Content
    - Errors: 401 Unauthorized, 403 Forbidden (user is not OWNER), 404 Not Found
    - Note: Deletes the collection and all permissions. Only OWNER role can delete.
- GET /collections/{id}/users
    - Description: Get all users that a recipes collection is shared with, including their roles
    - Authenticated: true
    - Path parameters:
        - `id` (UUID): Collection ID
    - Example response:
      ```json
      [
        {
          "email": "owner@example.com",
          "role": "OWNER"
        },
        {
          "email": "editor@example.com",
          "role": "EDITOR"
        }
      ]
      ```
    - Success: 200 OK
    - Errors: 401 Unauthorized, 403 Forbidden (if user lacks access), 404 Not Found
    - Note: OWNER appears first in the returned list, followed by EDITOR roles
- POST /collections/{id}/share
    - Description: Share recipes collection with another user (grants EDITOR access)
    - Authenticated: true
    - Path parameters:
        - `id` (UUID): Collection ID
    - Request body:
      ```json
      {
        "email": "user@example.com"
      }
      ```
    - Success: 204 No Content
    - Errors: 400 Bad Request (invalid email format), 401 Unauthorized, 403 Forbidden (if user has no access), 404 Not
      Found
    - Note: Shared user receives EDITOR access. Duplicate shares are silently ignored (idempotent).
- POST /collections/{id}/unshare
    - Description: Remove shared access from a user
    - Authenticated: true
    - Path parameters:
        - `id` (UUID): Collection ID
    - Request body:
      ```json
      {
        "email": "user@example.com"
      }
      ```
    - Success: 204 No Content
    - Errors: 400 Bad Request (invalid email format), 401 Unauthorized, 403 Forbidden (if user has no access, or trying
      to unshare OWNER), 404 Not Found
  - Note: EDITOR can unshare EDITORs (including self); EDITOR cannot remove OWNER; OWNER cannot remove themselves.
    When
    a collection is unshared from a user, all recipes owned by that user in the collection are automatically removed
    from the collection (recipesCollectionId set to null).

### Meal Plans

- GET /meal-plans
    - Description: Get all meal plans accessible by the authenticated user, ordered by creation date (oldest first)
    - Authenticated: true
    - Example response:
      ```json
      [
        {
          "id": "550e8400-e29b-41d4-a716-446655440000",
          "name": "Weekly Plan",
          "color": "#FF5733",
          "role": "OWNER",
          "createdAt": "2026-01-29T10:00:00Z"
        }
      ]
      ```
    - Success: 200 OK
    - Errors: 401 Unauthorized
- POST /meal-plans
    - Description: Create a new meal plan and grant OWNER permission to the authenticated user
    - Authenticated: true
  - Note: Automatically creates a permission record with OWNER role. There is a max number of plans owned per user (
    configured via `recipai.meal-plan.max-owned-plans`).
    - Request body:
      ```json
      {
        "name": "Weekly Plan",
        "color": "#FF5733"
      }
      ```
    - Example response:
      ```json
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "name": "Weekly Plan",
        "color": "#FF5733",
        "role": "OWNER",
        "createdAt": "2026-01-29T10:00:00Z"
      }
      ```
    - Success: 201 Created
    - Errors: 400 Bad Request (blank name, invalid color format), 409 Conflict (plan limit exceeded), 401 Unauthorized
    - Note: Color must be a valid hex color in format `#RRGGBB` (e.g., `#FF5733`)
- PUT /meal-plans/{id}
    - Description: Update the name and color of an existing meal plan
    - Authenticated: true
    - Path parameters:
        - `id` (UUID): Meal plan ID
    - Roles: OWNER and EDITOR can update
    - Request body:
      ```json
      {
        "name": "Updated Plan",
        "color": "#00FF00"
      }
      ```
    - Example response:
      ```json
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "name": "Updated Plan",
        "color": "#00FF00",
        "role": "OWNER",
        "createdAt": "2026-01-29T10:00:00Z"
      }
      ```
    - Success: 200 OK
    - Errors: 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found
- DELETE /meal-plans/{id}
    - Description: Delete a meal plan and all associated entries and permissions
    - Authenticated: true
    - Path parameters:
        - `id` (UUID): Meal plan ID
    - Roles: Only OWNER can delete
    - Example response: No content
    - Success: 204 No Content
    - Errors: 401 Unauthorized, 403 Forbidden (user is not OWNER), 404 Not Found
    - Note: Deletes the meal plan, all entries (via database CASCADE), and all permissions
- POST /meal-plans/{planId}/entries
    - Description: Create a new entry in a meal plan
    - Authenticated: true
    - Path parameters:
        - `planId` (UUID): Meal plan ID
    - Roles: OWNER and EDITOR can create entries
    - Request body:
      ```json
      {
        "date": "2026-01-29",
        "recipeId": "660e8400-e29b-41d4-a716-446655440001",
        "placeholderText": null,
        "servingSize": 4
      }
      ```
    - Example response:
      ```json
      {
        "id": 1,
        "planId": "550e8400-e29b-41d4-a716-446655440000",
        "date": "2026-01-29",
        "recipeId": "660e8400-e29b-41d4-a716-446655440001",
        "placeholderText": null,
        "servingSize": 4,
        "createdAt": "2026-01-29T10:00:00Z"
      }
      ```
    - Success: 201 Created
    - Errors: 400 Bad Request (validation errors), 401 Unauthorized, 403 Forbidden, 404 Not Found
    - Note: Entry must have either `recipeId` or `placeholderText`, not both and not neither. When `recipeId` is
      provided, `servingSize` is required. `servingSize` must be positive.
- PUT /meal-plans/{planId}/entries/{entryId}
    - Description: Update an existing meal plan entry
    - Authenticated: true
    - Path parameters:
        - `planId` (UUID): Meal plan ID
        - `entryId` (Long): Entry ID
    - Roles: OWNER and EDITOR can update entries
    - Request body:
      ```json
      {
        "date": "2026-02-01",
        "recipeId": null,
        "placeholderText": "Leftovers",
        "servingSize": null
      }
      ```
    - Example response:
      ```json
      {
        "id": 1,
        "planId": "550e8400-e29b-41d4-a716-446655440000",
        "date": "2026-02-01",
        "recipeId": null,
        "placeholderText": "Leftovers",
        "servingSize": null,
        "createdAt": "2026-01-29T10:00:00Z"
      }
      ```
    - Success: 200 OK
    - Errors: 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found (entry not found or belongs to different
      plan)
    - Note: Same validation rules as create (recipeId XOR placeholderText, servingSize required with recipeId)
- DELETE /meal-plans/{planId}/entries/{entryId}
    - Description: Delete a meal plan entry
    - Authenticated: true
    - Path parameters:
        - `planId` (UUID): Meal plan ID
        - `entryId` (Long): Entry ID
    - Roles: OWNER and EDITOR can delete entries
    - Example response: No content
    - Success: 204 No Content
    - Errors: 401 Unauthorized, 403 Forbidden, 404 Not Found (entry not found or belongs to different plan)
