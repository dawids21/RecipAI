# Planning API

Creating a meal plan consumes one unit of the owner's `MEAL_PLAN` budget, reserved *before* anything
is written and keyed by the `email` claim of the JWT. Deleting one returns the unit. It is a stock cap:
a refusal does not resolve itself by waiting, and only creation is blocked — reading, editing and
sharing keep working while the owner is over the cap. Sharing never charges the recipient. See
`docs/backend/modules/limits/` for how the cap is configured and changed.

## Refusal Response

A create past the cap returns **429 Too Many Requests** with an RFC 7807 `ProblemDetail`:

```json
{
  "type": "about:blank",
  "title": "Limit Exceeded",
  "status": 429,
  "detail": "Limit for MEAL_PLAN reached (2 of 2 used)",
  "resource": "MEAL_PLAN",
  "kind": "STOCK",
  "limit": 2,
  "used": 2
}
```

Neither `retryAfterSeconds` nor the `Retry-After` header is present, because a stock cap never
restarts — the owner has to delete something or have the cap raised.

### GET /meal-plans/usage
- Description: Get how much of the caller's `MEAL_PLAN` budget is already spent, for displaying
  `used / limit` before a plan is created
- Authenticated: true
- Example response — a stock cap never restarts, so no `resetsInSeconds`:
  ```json
  {
    "used": 1,
    "periodStart": "2026-08-23T10:00:00Z"
  }
  ```
- Success: 200 OK
- See `docs/backend/modules/limits/api.md` for the contract these usage reads share.

### GET /meal-plans
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

### POST /meal-plans
- Description: Create a new meal plan and grant OWNER permission to the authenticated user
- Authenticated: true
- Note: Automatically creates a permission record with OWNER role. There is a max number of plans owned per user, configured as the `MEAL_PLAN` limit in `limit_config`.
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
- Errors: 400 Bad Request (blank name, invalid color format), 401 Unauthorized, 429 Too many requests (plan cap reached)
- Note: Color must be a valid hex color in format `#RRGGBB` (e.g., `#FF5733`).

### PUT /meal-plans/{id}
- Description: Update the name and color of an existing meal plan
- Authenticated: true
- Roles: OWNER and EDITOR can update
- Request body: `{"name": "Updated Plan", "color": "#00FF00"}`
- Example response: Same structure as POST response
- Success: 200 OK
- Errors: 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found

### DELETE /meal-plans/{id}
- Description: Delete a meal plan and all associated entries and permissions
- Authenticated: true
- Roles: Only OWNER can delete
- Success: 204 No Content
- Errors: 401 Unauthorized, 403 Forbidden (user is not OWNER), 404 Not Found
- Note: Deletes the meal plan, all entries (via database CASCADE), and all permissions, and releases one unit of the owner's `MEAL_PLAN` budget.

### POST /meal-plans/{planId}/entries
- Description: Create a new entry in a meal plan
- Authenticated: true
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
- Note: Entry must have either `recipeId` or `placeholderText`, not both and not neither. When `recipeId` is provided, `servingSize` is required. When `placeholderText` is provided, `servingSize` cannot be provided. `servingSize` must be positive.

### PUT /meal-plans/{planId}/entries/{entryId}
- Description: Update an existing meal plan entry
- Authenticated: true
- Roles: OWNER and EDITOR can update entries
- Request body:
  ```json
  {
    "date": "2026-02-01",
    "recipeId": null,
    "placeholderText": "Leftovers",
    "servingSize": null,
    "planId": "660e8400-e29b-41d4-a716-446655440001"
  }
  ```
- Example response:
  ```json
  {
    "id": 1,
    "planId": "660e8400-e29b-41d4-a716-446655440001",
    "date": "2026-02-01",
    "recipeId": null,
    "placeholderText": "Leftovers",
    "servingSize": null,
    "createdAt": "2026-01-29T10:00:00Z"
  }
  ```
- Success: 200 OK
- Errors: 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found (entry not found, belongs to different plan, or target planId does not exist; also returned when requesting user lacks EDITOR/OWNER access on the target plan specified by `planId`)
- Note: Same validation rules as create (recipeId XOR placeholderText, servingSize required with recipeId, servingSize cannot be provided with placeholderText). The optional `planId` field in the request body moves the entry to a different plan.

### DELETE /meal-plans/{planId}/entries/{entryId}
- Description: Delete a meal plan entry
- Authenticated: true
- Roles: OWNER and EDITOR can delete entries
- Success: 204 No Content
- Errors: 401 Unauthorized, 403 Forbidden, 404 Not Found (entry not found or belongs to different plan)

### POST /meal-plans/generate-shopping-list
- Description: Generate shopping list items from planned meals across one or more meal plans on specified dates. Only entries with a recipe (not placeholders) are considered. Ingredient quantities are scaled by the ratio of the entry's `servingSize` to the recipe's base `servingSize`. Recipes the requesting user cannot access are skipped and their names are included in `inaccessibleRecipeNames`.
- Authenticated: true
- Request body:
  ```json
  {
    "planIds": [
      "550e8400-e29b-41d4-a716-446655440000",
      "660e8400-e29b-41d4-a716-446655440001"
    ],
    "selectedDates": [
      "2026-02-01",
      "2026-02-02"
    ]
  }
  ```
- Example response:
  ```json
  {
    "items": [
      {
        "name": "flour",
        "quantity": 300,
        "unit": "g",
        "source": "Pizza"
      },
      {
        "name": "tomato sauce",
        "quantity": 200,
        "unit": "ml",
        "source": "Pizza"
      }
    ],
    "inaccessibleRecipeNames": [
      "Secret Recipe"
    ]
  }
  ```
- Success: 200 OK
- Errors: 400 Bad Request (planIds or selectedDates are null or empty), 401 Unauthorized, 403 Forbidden (user lacks access to one of the specified plans), 404 Not Found (one of the specified plans does not exist)
- Note: All specified plan IDs are validated — the user must have access to every plan in the request. Entries with placeholder text are ignored. Ingredient quantities are multiplied by `entry.servingSize / recipe.servingSize` (defaults to 1 if not set). For ingredients with null quantity (e.g., those with a `comment` like "to taste"), the multiplier itself is used as the quantity. When a `comment` is present on an ingredient, it is appended in parentheses to the item name (e.g., `"salt (to taste)"`). Each item includes a `source` field with the recipe name.

---

## Sharing & Permissions

### GET /meal-plans/{id}/users
- Description: Get all users that a meal plan is shared with, including their roles
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

### POST /meal-plans/{id}/share
- Description: Share meal plan with another user (grants EDITOR access)
- Authenticated: true
- Roles: OWNER and EDITOR can share
- Request body: `{"email": "user@example.com"}`
- Success: 204 No Content
- Errors: 400 Bad Request (invalid email format), 403 Forbidden, 404 Not Found
- Note: Shared user receives EDITOR access. Duplicate shares are silently ignored (idempotent).

### POST /meal-plans/{id}/unshare
- Description: Remove shared access from a user
- Authenticated: true
- Roles: OWNER and EDITOR can unshare (except EDITOR cannot unshare OWNER)
- Request body: `{"email": "user@example.com"}`
- Success: 204 No Content
- Errors: 400 Bad Request (invalid email format), 403 Forbidden (if user has no access, or EDITOR tries to unshare OWNER, or OWNER tries to unshare themselves), 404 Not Found
- Note: EDITOR can unshare EDITORs (including themselves); EDITOR cannot remove OWNER; OWNER cannot remove themselves.

---

## Calendar View

### GET /meal-plans/calendar
- Description: Get meal plan entries grouped by date for calendar view. Returns entries from specified meal plans within the date range, with recipe names and access flags populated.
- Authenticated: true
- Query parameters:
    - `startDate` (required, ISO 8601 date): Start of date range (inclusive)
    - `endDate` (required, ISO 8601 date): End of date range (inclusive)
    - `planIds` (required, comma-separated UUIDs): Filter entries by specific meal plan IDs. Can be empty string to get no entries.
- Example request:
  `GET /meal-plans/calendar?startDate=2026-02-01&endDate=2026-02-28&planIds=550e8400-e29b-41d4-a716-446655440000,660e8400-e29b-41d4-a716-446655440001`
- Example response:
  ```json
  {
    "2026-02-01": [
      {
        "id": 1,
        "planId": "550e8400-e29b-41d4-a716-446655440000",
        "planColor": "#FF5733",
        "date": "2026-02-01",
        "recipeId": "770e8400-e29b-41d4-a716-446655440002",
        "recipeName": "Pasta Carbonara",
        "placeholderText": null,
        "servingSize": 4,
        "hasRecipeAccess": true
      },
      {
        "id": 2,
        "planId": "660e8400-e29b-41d4-a716-446655440001",
        "planColor": "#33FF57",
        "date": "2026-02-01",
        "recipeId": null,
        "recipeName": null,
        "placeholderText": "Lunch with friends",
        "servingSize": 2,
        "hasRecipeAccess": true
      }
    ],
    "2026-02-02": [
      {
        "id": 3,
        "planId": "550e8400-e29b-41d4-a716-446655440000",
        "planColor": "#FF5733",
        "date": "2026-02-02",
        "recipeId": "880e8400-e29b-41d4-a716-446655440003",
        "recipeName": "Chicken Curry",
        "placeholderText": null,
        "servingSize": 3,
        "hasRecipeAccess": false
      }
    ]
  }
  ```
- Success: 200 OK
- Errors:
    - 400 Bad Request (invalid date format, startDate after endDate, date range exceeds 3 months, or planIds parameter is missing)
    - 401 Unauthorized
- Notes:
    - Entries are grouped by date and sorted by date (ascending), then by creation time within each date
    - `planIds` is required and must be present in the request; empty string returns `{}`
    - `hasRecipeAccess` is `true` if the user has permission to view the recipe (either direct permission or via recipe collection), or if the entry is a placeholder
    - `hasRecipeAccess` is `false` if the entry references a recipe the user cannot access
    - When a recipe is deleted, the entry is converted to a placeholder: `recipeId` and `recipeName` become null, `placeholderText` is set to the original recipe name
    - Date range cannot exceed 3 months
    - Returns empty object `{}` if no entries exist in the date range
