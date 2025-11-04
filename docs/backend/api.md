# REST API Documentation - RecipAI

## Resources

- Recipes: Maps the `recipes` DB table with user-scoped access.
- Shopping Lists: Maps the `shopping_lists` DB table for managing shopping lists.

## Endpoints

### Recipes

- GET /recipes
    - Description: Get all recipes as list with basic info
    - Authenticated: true
    - Example response:
      ```json
      [
        {
          "id": "uuid",
          "name": "Pizza"
        },
        {
          "id": "uuid",
          "name": "Spaghetti"
        }
      ]
      ```
    - Success: 200 OK
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
          ]
        },
        "role": "OWNER"
      }
      ```
    - Success: 200 OK
    - Errors: 403 Forbidden (if user lacks access to recipe), 404 Not Found
  - Note: `role` field indicates user's access level: "OWNER" (can view, edit, delete, share, unshare) or "EDITOR" (can
    view and edit only)
- POST /recipes
    - Description: Add new recipe
    - Authenticated: true
    - Request body:
      ```json
      {
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
          ]
        }
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
          ]
        },
        "role": "OWNER"
      }
      ```
    - Success: 201 Created
    - Errors: 400 Bad request
- PUT /recipes/{uuid}
    - Description: Update existing recipe by UUID
  - Authenticated: true
      - Request body:
        ```json
        {
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
            ]
          }
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
            ]
          },
          "role": "OWNER"
        }
        ```
      - Success: 200 OK
      - Errors: 403 Forbidden (if user lacks access to recipe), 404 Not Found, 400 Bad request
    - Note: Both OWNER and EDITOR roles can update recipes
- DELETE /recipes/{uuid}
    - Description: Delete recipe by UUID
  - Authenticated: true
      - Example response: No content
      - Success: 204 No Content
    - Errors: 403 Forbidden (if user is not OWNER of the recipe), 404 Not Found
    - Note: Only OWNER role can delete recipes
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
    - Note: OWNER appears first in the returned list, followed by EDITOR roles

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
    - Note: Shared user receives EDITOR access.

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
    - Note: Removes EDITOR access from target user.

### Shopping Lists

- GET /shopping-lists
    - Description: Get all shopping lists
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
            "position": 1,
            "version": 0
          },
          {
            "id": "880e8400-e29b-41d4-a716-446655440011",
            "name": "Bread",
            "quantity": null,
            "unit": null,
            "checked": true,
            "position": 2,
            "version": 0
          }
        ],
        "role": "OWNER"
      }
      ```
    - Success: 200 OK
  - Errors: 404 Not Found (returned when list doesn't exist OR user lacks permission), 401 Unauthorized
    - Note: Items are ordered by `position` in ascending order. Quantity and unit can be null.
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
