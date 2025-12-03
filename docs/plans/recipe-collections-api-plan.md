# API Plan - Recipe Collections

## Overview

This feature enables users to organize recipes into folder-like "Collections". It supports a 1-to-1 relationship between
recipes and collections (no nesting), allows for "push" sharing of collections (granting Editor access to all recipes
within), and handles recipe movements between collections.

## API Endpoints

### List Collections

* **Path**: `GET /collections`
* **Purpose**: Retrieve all collections the user has access to (both Owned and Shared).
* **Authentication**: User
* **Response**:
    - **Success (200)**:
      ```json
      [
        {
          "id": "uuid",
          "name": "Holiday Dinner",
          "role": "OWNER",
          "ownerEmail": "me@example.com"
        },
        {
          "id": "uuid",
          "name": "Shared Keto",
          "role": "EDITOR",
          "ownerEmail": "friend@example.com"
        }
      ]
      ```

### Create Collection

* **Path**: `POST /collections`
* **Purpose**: Create a new empty collection.
* **Authentication**: User
* **Request Parameters**:
    - **Body**: `{ "name": "Summer BBQ" }`
* **Response**:
    - **Success (201)**: `{ "id": "uuid", "name": "Summer BBQ", "role": "OWNER", "ownerEmail": "..." }`
* **Notes**: Name is not required to be unique globally, but distinct names are recommended for UX.

### Rename Collection

* **Path**: `PUT /collections/{uuid}`
* **Purpose**: Rename an existing collection. Updates globally for all shared users.
* **Authentication**: Owner or Editor
* **Request Parameters**:
    - **Body**: `{ "name": "Winter BBQ" }`
* **Response**:
    - **Success (200)**: Updated collection object.
* **Notes**: Follows "Last Save Wins" logic.

### Delete Collection

* **Path**: `DELETE /collections/{uuid}`
* **Purpose**: Permanently delete a collection.
* **Authentication**: Owner Only
* **Response**:
    - **Success (204)**: No Content.
* **Notes**:
    - **Recipes are NOT deleted.**
    - Recipes owned by the Collection Owner become "Unassigned" in their library.
    - Recipes owned by Editors become "Unassigned" in the specific Editor's library.

### List Shared Users

* **Path**: `GET /collections/{uuid}/shared_users`
* **Purpose**: View who has access to this collection.
* **Authentication**: Owner or Editor
* **Response**:
    - **Success (200)**:
      ```json
      [
        { "email": "owner@example.com", "role": "OWNER" },
        { "email": "friend@example.com", "role": "EDITOR" }
      ]
      ```

### Share Collection

* **Path**: `POST /collections/{uuid}/share`
* **Purpose**: Grant "Editor" access to another user (Push model, no invite).
* **Authentication**: Owner Only
* **Request Parameters**:
    - **Body**: `{ "email": "friend@example.com" }`
* **Response**:
    - **Success (200)**: Updated list of shared users.

### Unshare / Leave Collection

* **Path**: `POST /collections/{uuid}/unshare`
* **Purpose**: Revoke access to a collection.
* **Authentication**: Owner (to remove others) or Editor (to leave).
* **Request Parameters**:
    - **Body**: `{ "email": "target@example.com" }`
* **Response**:
    - **Success (200)**: Success message.
* **Notes**:
    - If `email` == Current User: "Leave" action.
    - If `email` != Current User: "Kick" action (Owner only).
    - Recipes owned by the removed user are moved to their "Unassigned" library and are no longer visible in the
      collection.

### List Recipes (Updated)

* **Path**: `GET /recipes`
* **Request Parameters**:
    - **Query (New)**: `collectionId={uuid}` (Optional) - Returns only recipes in this specific collection.
    - **Query (New)**: `unassigned=true` (Optional) - Returns only recipes NOT in any collection.
* **Notes**:
    - If neither param is present, return **all** accessible recipes.
    - Default Sort: `created_at DESC` (Newest First).

### Get Recipe (Updated)

* **Path**: `GET /recipes/{uuid}`
* **Response**: Adds `collectionId` field to the standard response.
* **Notes**:
    - Security Check Update: Access is granted if User is Owner OR has `recipe_permission` OR (Recipe is in a Collection
      AND User has `collection_permission`).

### Create Recipe (Updated)

* **Path**: `POST /recipes`
* **Request Parameters**:
    - **Body**: Adds optional `"collectionId": "uuid"` field.
* **Notes**:
    - If `collectionId` is provided, the recipe is immediately created within that collection.

### Update Recipe / Move (Updated)

* **Path**: `PUT /recipes/{uuid}`
* **Request Parameters**:
    - **Body**: Adds optional `"collectionId": "uuid"` (or `null`) field.
* **Notes**:
    - Changing `collectionId` moves the recipe.
    - **Validation**: User must have `EDITOR` or `OWNER` role on the **destination** collection.
    - If moved out of a shared collection to a private one, other users lose access immediately.

## Data Models

### Collection

```json
{
  "id": "UUID | Primary Key",
  "name": "String | Display name",
  "role": "String | Computed field (OWNER/EDITOR) based on requestor"
}
````

### CollectionPermission

```json
{
  "collectionId": "UUID | Foreign Key",
  "email": "String | Foreign Key to User",
  "role": "String | Enum: OWNER, EDITOR"
}
```

## Integration Points

* **Database Tables**:
    * **New**: `collections`
    * **New**: `collection_permission`
    * **Modified**: `recipes` table receives a nullable `collection_id` column with `ON DELETE SET NULL` constraint.
* **Security Model**:
    * **Implicit Permissions**: Creating a record in `collection_permission` implicitly grants access to all recipes
      linked to that collection. The system must resolve permissions dynamically rather than syncing
      `recipe_permissions`.