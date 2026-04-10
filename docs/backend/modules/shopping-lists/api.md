# Shopping Lists API

### GET /shopping-lists
- Description: Get all shopping lists ordered by creation date (oldest first)
- Authenticated: true
- Example response:
  ```json
  [
    {"id": "550e8400-e29b-41d4-a716-446655440000", "name": "Groceries"},
    {"id": "660e8400-e29b-41d4-a716-446655440001", "name": "Hardware"}
  ]
  ```
- Success: 200 OK
- Errors: 401 Unauthorized

### GET /shopping-lists/{id}
- Description: Get a shopping list by ID with all its items
- Authenticated: true
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
- Errors: 401 Unauthorized, 403 Forbidden (user lacks permission), 404 Not Found
- Note: Items are ordered by `position` in ascending order. Quantity and unit can be null.

### POST /shopping-lists
- Description: Create a new shopping list and grant OWNER permission to the authenticated user
- Authenticated: true
- Request body: `{"name": "My Shopping List"}`
- Example response: `{"id": "uuid", "name": "My Shopping List"}`
- Success: 201 Created
- Errors: 400 Bad Request (validation error), 401 Unauthorized

### PUT /shopping-lists/{id}
- Description: Update the name of an existing shopping list
- Authenticated: true
- Roles: OWNER and EDITOR can update
- Request body: `{"name": "Updated Shopping List Name"}`
- Example response: `{"id": "uuid", "name": "Updated Shopping List Name"}`
- Success: 200 OK
- Errors: 400 Bad Request, 401 Unauthorized, 403 Forbidden (user lacks permission), 404 Not Found

### DELETE /shopping-lists/{id}
- Description: Delete a shopping list and all associated items and permissions
- Authenticated: true
- Roles: Only OWNER can delete
- Success: 204 No Content
- Errors: 401 Unauthorized, 403 Forbidden (user is not OWNER), 404 Not Found
- Note: Deletes the shopping list, all items (via database CASCADE), and all permissions.

### POST /shopping-lists/{shopping_list_id}/item
- Description: Create a new item in a shopping list at the specified index or at the end
- Authenticated: true
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
    - `index` (integer, optional): 0-based index where to insert the item. If not provided, item is appended at the end
- Behavior:
    - When `index` is not provided or null: Item is appended at the end
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
- Errors: 400 Bad Request (blank name, oversized fields, or negative index), 401 Unauthorized, 403 Forbidden (user lacks EDITOR/OWNER permission), 404 Not Found (shopping list doesn't exist)
- Note: Position is calculated using a fractional positioning algorithm allowing precise insertion between existing items.

### DELETE /shopping-lists/{shopping_list_id}/item/{id}
- Description: Delete an item from a shopping list
- Authenticated: true
- Headers:
    - `If-Match` (required): Version number of the item (obtained from GET request)
- Roles: OWNER and EDITOR can delete items
- Success: 204 No Content
- Errors:
    - 401 Unauthorized
    - 403 Forbidden (user lacks EDITOR/OWNER permission)
    - 404 Not Found (item not found or doesn't belong to the shopping list)
    - 412 Precondition Failed (version mismatch — item was modified by another user)
- Note: Deleting an item does not renumber remaining items (gaps are allowed in positions). The `If-Match` header must contain the current version of the item to prevent concurrent modification conflicts.

### PUT /shopping-lists/{shopping_list_id}/item/{id}
- Description: Update an item's name, quantity, and unit
- Authenticated: true
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
    - 400 Bad Request (blank name or oversized fields)
    - 401 Unauthorized
    - 403 Forbidden (user lacks EDITOR/OWNER permission)
    - 404 Not Found (item not found or doesn't belong to the shopping list)
    - 412 Precondition Failed (version mismatch)
- Note: Version is automatically incremented on successful update.

### POST /shopping-lists/{shopping_list_id}/item/{id}/move
- Description: Move an item to a different position in the shopping list by index
- Authenticated: true
- Headers:
    - `If-Match` (required): Version number of the item
- Roles: OWNER and EDITOR can move items
- Request body: `{"index": 2}`
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
    - 400 Bad Request (negative index)
    - 401 Unauthorized
    - 403 Forbidden (user lacks EDITOR/OWNER permission)
    - 404 Not Found (item not found or doesn't belong to the shopping list)
    - 412 Precondition Failed (version mismatch)
- Note: Index is 0-based. Moving to the same index is idempotent. Position values are automatically normalized to 6 decimal places.

### POST /shopping-lists/{shopping_list_id}/item/{id}/check
- Description: Mark an item as checked
- Authenticated: true
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
- Errors: 401 Unauthorized, 403 Forbidden, 404 Not Found, 412 Precondition Failed (version mismatch)
- Note: Idempotent — checking an already checked item has no effect.

### POST /shopping-lists/{shopping_list_id}/item/{id}/uncheck
- Description: Mark an item as unchecked
- Authenticated: true
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
- Errors: 401 Unauthorized, 403 Forbidden, 404 Not Found, 412 Precondition Failed (version mismatch)
- Note: Idempotent — unchecking an already unchecked item has no effect.

### GET /shopping-lists/{id}/users
- Description: Get all users that a shopping list is shared with, including their roles
- Authenticated: true
- Example response:
  ```json
  [
    {"email": "owner@example.com", "role": "OWNER"},
    {"email": "editor@example.com", "role": "EDITOR"}
  ]
  ```
- Success: 200 OK
- Errors: 403 Forbidden (if user lacks access), 404 Not Found
- Note: OWNER appears first in the returned list.

### POST /shopping-lists/{id}/share
- Description: Share shopping list with another user (grants EDITOR access)
- Authenticated: true
- Request body: `{"email": "user@example.com"}`
- Success: 204 No Content
- Errors: 400 Bad Request, 403 Forbidden, 404 Not Found
- Note: Shared user receives EDITOR access. Duplicate shares are silently ignored.

### POST /shopping-lists/{id}/unshare
- Description: Remove shared access from a user
- Authenticated: true
- Request body: `{"email": "user@example.com"}`
- Success: 204 No Content
- Errors: 400 Bad Request, 403 Forbidden (if user has no access, or trying to unshare OWNER), 404 Not Found
- Note: EDITOR can unshare EDITORs (including self); EDITOR cannot remove OWNER; OWNER cannot remove themselves.
