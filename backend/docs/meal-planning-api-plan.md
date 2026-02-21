# API Plan - Meal Planning

## Overview

The Meal Planning feature enables users to create multiple distinct cooking schedules (e.g., Personal, Family),
visualize them on a layered calendar, and share plans with other users. It integrates deeply with the Shopping List
feature, allowing users to calculate required ingredients based on specific days and yield multipliers, while respecting
recipe ownership and privacy rules.

## API Endpoints

### Meal Plan Management

#### List Meal Plans

* **Path**: `GET /meal-plans`
* **Purpose**: Retrieve all meal plans the user owns or has access to.
* **Authentication**: User (JWT)
* **Response**:
    - **Success (200)**: List of `MealPlan` objects.
* **Notes**: Results include user's specific role (OWNER/EDITOR) for each plan. Visibility is not returned here as it is
  a client-side setting.

#### Create Meal Plan

* **Path**: `POST /meal-plans`
* **Purpose**: Create a new meal plan context.
* **Authentication**: User (JWT)
* **Request Parameters**:
    - **Body**: `{ "name": "String", "color": "String (Hex)" }`
* **Response**:
    - **Success (201)**: Created `MealPlan` object.
* **Notes**: Limit of 10 owned plans per user.

#### Update Meal Plan

* **Path**: `PUT /meal-plans/{id}`
* **Purpose**: Update plan metadata (Name, Color).
* **Authentication**: User (JWT) - Owner or Editor
* **Request Parameters**:
    - **Path**: `id` (UUID)
    - **Body**: `{ "name": "String", "color": "String (Hex)" }`
* **Response**:
    - **Success (200)**: Updated `MealPlan` object.

#### Delete Meal Plan

* **Path**: `DELETE /meal-plans/{id}`
* **Purpose**: Permanently delete a plan and all its entries.
* **Authentication**: User (JWT) - Owner only
* **Request Parameters**:
    - **Path**: `id` (UUID)
* **Response**:
    - **Success (204)**: No Content.

### Calendar Visualization

#### Get Calendar Entries

* **Path**: `GET /meal-plans/calendar`
* **Purpose**: Retrieve meal entries grouped by date for the calendar view.
* **Authentication**: User (JWT)
* **Request Parameters**:
    - **Query**:
        - `startDate` (ISO Date, required)
        - `endDate` (ISO Date, required, max range 3 months)
        - `planIds` (Comma-separated UUIDs, optional filter)
* **Response**:
    - **Success (200)**: Map<DateString, List<`MealPlanEntry`>>
      ```json
      {
        "2023-10-27": [ { ...entry_details... } ]
      }
      ```
* **Notes**:
    - **Dynamic Name**: For recipe entries, the `recipeName` is fetched dynamically from the Recipes table.
    - **Restricted Access**: If a recipe is not owned by the viewer, the entry returns `hasRecipeAccess: false` and the
      `recipeName`. The client must disable opening the recipe details.
    - **Shopping List Impact**: Restricted entries are automatically excluded from the "Generate Shopping List" flow.
    - Entries are sorted by creation time.

### Entry Management

#### Create Entry

* **Path**: `POST /meal-plans/{planId}/entries`
* **Purpose**: Add a recipe or placeholder to a specific date.
* **Authentication**: User (JWT) - Owner or Editor
* **Request Parameters**:
    - **Path**: `planId` (UUID)
    - **Body**:
      ```json
      {
        "date": "YYYY-MM-DD",
        "recipeId": "UUID (optional)",
        "placeholderText": "String (optional)",
        "servingSize": "Integer (required if recipeId present)"
      }
      ```
* **Response**:
    - **Success (201)**: Created `MealPlanEntry` object.
* **Notes**: Must provide either `recipeId` OR `placeholderText`. Do not send `recipeName` in body; it is linked via ID.

#### Update Entry

* **Path**: `PUT /meal-plans/{planId}/entries/{entryId}`
* **Purpose**: Edit an existing entry (change date, serving size, text, or associated recipe).
* **Authentication**: User (JWT) - Owner or Editor
* **Request Parameters**:
    - **Path**: `planId`, `entryId`
    - **Body**: Same structure as Create Entry.
* **Response**:
    - **Success (200)**: Updated `MealPlanEntry` object.
* **Notes**: Users are permitted to change the `recipeId` of an entry, even if they do not have access to the
  *currently* associated recipe details (Restricted Access), provided they have Editor permissions on the Plan.

#### Delete Entry

* **Path**: `DELETE /meal-plans/{planId}/entries/{entryId}`
* **Purpose**: Remove an entry from the plan.
* **Authentication**: User (JWT) - Owner or Editor
* **Request Parameters**:
    - **Path**: `planId`, `entryId`
* **Response**:
    - **Success (204)**: No Content.

### Sharing & Permissions

#### Get Shared Users

* **Path**: `GET /meal-plans/{id}/users`
* **Purpose**: List users with access to the plan.
* **Authentication**: User (JWT) - Owner or Editor
* **Response**:
    - **Success (200)**: List of `{ "email": "String", "role": "OWNER|EDITOR" }`

#### Share Meal Plan

* **Path**: `POST /meal-plans/{id}/share`
* **Purpose**: Grant Editor access to another user.
* **Authentication**: User (JWT) - Owner or Editor
* **Request Parameters**:
    - **Body**: `{ "email": "String" }`
* **Response**:
  - **Success (204)**: No Content.
* **Notes**: Duplicate shares are silently ignored (idempotent).

#### Unshare Meal Plan

* **Path**: `POST /meal-plans/{id}/unshare`
* **Purpose**: Revoke access from a user.
* **Authentication**: User (JWT) - Owner or Editor
* **Request Parameters**:
    - **Body**: `{ "email": "String" }`
* **Response**:
  - **Success (204)**: No Content.
* **Notes**: EDITOR cannot unshare OWNER. OWNER cannot unshare themselves.

### Shopping List Integration

#### Generate Shopping List Items

* **Path**: `POST /meal-plans/generate-shopping-list`
* **Purpose**: Calculate ingredients needed for selected plans and dates.
* **Authentication**: User (JWT)
* **Request Parameters**:
    - **Body**:
      ```json
      {
        "planIds": ["UUID"],
        "selectedDates": ["YYYY-MM-DD"]
      }
      ```
* **Response**:
    - **Success (200)**: `ShoppingListGeneratedItems` object.
* **Notes**:
    - **Client Responsibility**: This endpoint only calculates items. The frontend must accept this response and
      subsequently call the `POST /shopping-lists/{id}/items` endpoint to add them to a specific list.
    - **Privacy**: If a user selects a date containing a restricted recipe (unowned), those ingredients are **skipped**.
      The response `warnings` list will indicate which meals were excluded.
  - **No Aggregation**: The API DOES NOT merge ingredients (e.g., if two recipes call for "Salt", two "Salt" entries
    are returned).
  - **Sorting**: Items are sorted by Name (A-Z) to help the user manually review and deduplicate on the frontend.

## Data Models

### MealPlan

```json
{
  "id": "UUID",
  "name": "String",
  "color": "String (Hex)",
  "currentUserRole": "OWNER | EDITOR",
  "createdAt": "Timestamp"
}
```

### MealPlanEntry

```json
{
  "id": "UUID",
  "planId": "UUID",
  "planColor": "String (Hex)",
  "date": "YYYY-MM-DD",
  "recipeId": "UUID | null",
  "recipeName": "String | null | description: Fetched dynamically on read via recipeId",
  "placeholderText": "String | null",
  "servingSize": "Integer | null",
  "hasRecipeAccess": "Boolean | description: true if user does not have read access to source recipe"
}
```

* **Validation Rules**: `servingSize` required if `recipeId` is present. `servingSize` cannot be provided if
  `placeholderText` is present. `recipeId` and `placeholderText` are mutually exclusive.

### ShoppingListGeneratedItems

```json
{
  "items": [
    {
      "name": "String",
      "quantity": "String | null",
      "unit": "String | null",
      "sourceRecipeName": "String | Description: Context for where this item came from"
    }
  ],
  "warnings": [
    "String | Description: Messages about restricted recipes skipped during generation"
  ]
}
```

* **Notes**: Items are sorted by name. Quantities are calculated based on entry `servingSize` vs recipe yield. No unit
  conversion is performed (e.g., "cups" and "oz" remain separate). `quantity` and `unit` are null for Placeholder
  entries.

## Integration Points

### Database Tables

* **New Table**: `meal_plans` (id, name, color, created_at)
* **New Table**: `meal_plan_permissions` (email, plan_id, role)
* **New Table**: `meal_plan_entries` (id, plan_id, date, recipe_id, placeholder_text, serving_size)
* **Existing Table**: `recipes` (Referenced by `meal_plan_entries`)

### Logic Integrations

* **Recipe Access Check**: The Calendar and Shopping List generation logic must query `recipe_permission` (or the Recipe
  Service) to determine `hasRecipeAccess` status and filter ingredients.
* **Recipe Deletion Event**: An application event listener is required. When a recipe is deleted, the system must update
  associated `meal_plan_entries`:
    1. Copy the current `recipe_name` into `placeholder_text`.
    2. Set `recipe_id` to NULL.
    3. (Result: The entry effectively becomes a manual text placeholder).