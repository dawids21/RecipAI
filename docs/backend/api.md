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
        "...": "..."
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
        "...": "..."
      }
    }
    ```
  - Example response:
    ```json
    {
      "id": "uuid",
      "name": "Pizza",
      "data": {
        "...": "..."
      }
    }
    ```
  - Success: 201 Created
  - Errors: 400 Bad request
### Extraction
- POST /extract/text
  - Description: Extract recipe information from text and save it as a new recipe
  - Request body:
    ```json
    {
      "text": "text with recipe for pizza"
    }
    ```
  - Example response:
    ```json
    {
      "id": "uuid",
      "name": "Pizza",
      "data": {
        "...": "..."
      }
    }
    ```
  - Success: 200 OK
  - Errors: 400 Bad request
## Authentication and Authorization
- Currently not implemented