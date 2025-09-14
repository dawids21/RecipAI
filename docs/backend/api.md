# REST API Documentation - RecipAI

## Resources

- Recipes: Maps the `recipes` DB table with user-scoped access.

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
              "quantity": "300g",
              "unit": null
            },
            {
              "name": "tomato sauce",
              "quantity": "200ml",
              "unit": null
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
              "quantity": "300g",
              "unit": null
            },
            {
              "name": "tomato sauce",
              "quantity": "200ml",
              "unit": null
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
              "quantity": "300g",
              "unit": null
            },
            {
              "name": "tomato sauce",
              "quantity": "200ml",
              "unit": null
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
                "quantity": "400g",
                "unit": null
              },
              {
                "name": "cheese",
                "quantity": "200g",
                "unit": null
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
                "quantity": "400g",
                "unit": null
              },
              {
                "name": "cheese",
                "quantity": "200g",
                "unit": null
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
          "quantity": "300g",
          "unit": null
        },
        {
          "name": "tomato sauce",
          "quantity": "200ml",
          "unit": null
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
            "quantity": "1 cup",
            "unit": null
          },
          {
            "name": "breadcrumbs", 
            "quantity": "1/2 cup",
            "unit": null
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
