# REST API Documentation - RecipAI
## Resources
- Recipes: Maps the `recipes` DB table.
## Endpoints
### Recipes
- GET /recipes
  - Description: Get all recipes as list with basic info
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
      }
    }
    ```
  - Success: 200 OK
  - Errors: 404 Not Found
- POST /recipes
  - Description: Add new recipe
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
      }
    }
    ```
  - Success: 201 Created
  - Errors: 400 Bad request
- PUT /recipes/{uuid}
    - Description: Update existing recipe by UUID
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
        }
      }
      ```
    - Success: 200 OK
    - Errors: 404 Not Found, 400 Bad request
- DELETE /recipes/{uuid}
    - Description: Delete recipe by UUID
    - Example response: No content
    - Success: 204 No Content
    - Errors: 404 Not Found
### Extraction
- POST /extract/text
    - Description: Extract recipe information from text
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
## Authentication and Authorization
- Currently not implemented