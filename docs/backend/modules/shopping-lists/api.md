# Shopping Lists API

Creating a shopping list consumes one unit of the owner's `SHOPPING_LIST` budget, reserved *before*
anything is written and keyed by the `email` claim of the JWT; deleting one returns the unit. It is a
stock cap: a refusal does not resolve itself by waiting, and only creation is blocked — reading,
editing, sharing and every item operation keep working while the owner is over the cap. Sharing never
charges the recipient. Item endpoints consume no budget. See `docs/backend/modules/limits/` for how the
cap is configured and changed.

## Refusal Response

A create past the cap returns **429 Too Many Requests** with an RFC 7807 `ProblemDetail`:

```json
{
  "type": "about:blank",
  "title": "Limit Exceeded",
  "status": 429,
  "detail": "Limit for SHOPPING_LIST reached (2 of 2 used)",
  "resource": "SHOPPING_LIST",
  "kind": "STOCK",
  "limit": 2,
  "used": 2
}
```

Neither `retryAfterSeconds` nor the `Retry-After` header is present, because a stock cap never
restarts — the owner has to delete a list or have the cap raised.

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
- Note: Items are ordered by `position` ascending, ties broken by `id` ascending (`position` is not unique). Quantity and unit can be null.

### POST /shopping-lists
- Description: Create a new shopping list and grant OWNER permission to the authenticated user
- Authenticated: true
- Request body: `{"name": "My Shopping List"}`
- Example response: `{"id": "uuid", "name": "My Shopping List"}`
- Success: 201 Created
- Errors: 400 Bad Request (validation error), 401 Unauthorized, 429 Too many requests (shopping list cap reached)

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
- Note: Deletes the shopping list, all items (via database CASCADE), and all permissions, and returns the owner's `SHOPPING_LIST` unit.

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

### POST /shopping-lists/{id}/items
- Description: Create a new item on a shopping list. The client supplies `position`; creates never conflict (no `baseVersion`).
- Authenticated: true
- Roles: OWNER and EDITOR can create items
- Request body: `{"name": "Milk", "quantity": 2.0, "unit": "liters", "checked": false, "position": 1.0}` (`quantity` and `unit` are nullable)
- Example response: `{"id": "uuid", "name": "Milk", "quantity": 2.0, "unit": "liters", "checked": false, "position": 1.0, "version": 0}`
- Success: 201 Created
- Errors: 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found (list does not exist)
- Note: `checked` is optional and defaults to `false` when omitted. It exists so a client can re-create an item in its checked state — the mobile undo of "Delete All Checked" restores items straight into the Done section.

### PUT /shopping-lists/{id}/items/{itemId}
- Description: Update all mutable fields of an item (name, quantity, unit, checked, position) as one version-gated write — covers edits, check/uncheck, and reorders uniformly (first-action-wins).
- Authenticated: true
- Roles: OWNER and EDITOR can update items
- Request body: `{"baseVersion": 0, "name": "Whole Milk", "quantity": 2.0, "unit": "liters", "checked": false, "position": 1.0}`
- Example response: the updated item, e.g. `{"id": "uuid", "name": "Whole Milk", "quantity": 2.0, "unit": "liters", "checked": false, "position": 1.0, "version": 1}`
- Success: 200 OK
- Errors: 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found (list or item does not exist, or item belongs to a different list)
- **412 Precondition Failed**: `baseVersion` no longer matches the stored item's version (someone else changed it first). The response body is the **raw current item** (a `ShoppingListItemDto`, not a `ProblemDetail`) so the client can roll back to it directly.

### DELETE /shopping-lists/{id}/items/{itemId}?baseVersion={n}
- Description: Hard-delete an item, version-gated the same way as update. If the item was edited after the client's last read, the edit wins and the delete is rejected.
- Authenticated: true
- Roles: OWNER and EDITOR can delete items
- Success: 204 No Content
- Errors: 400 Bad Request (missing `baseVersion`), 401 Unauthorized, 403 Forbidden, 404 Not Found (list or item does not exist)
- **412 Precondition Failed**: `baseVersion` is stale (edit-wins-over-delete). The response body is the raw winning `ShoppingListItemDto`.
