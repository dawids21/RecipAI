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
