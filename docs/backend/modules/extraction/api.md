# Extraction API

### POST /extract/text
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
    "ingredients": [
      {"name": "flour", "quantity": 300, "unit": "g"},
      {"name": "tomato sauce", "quantity": 200, "unit": "ml"},
      {"name": "salt", "comment": "to taste"}
    ],
    "instructions": [
      {"step": "Make dough"},
      {"step": "Add sauce and toppings"}
    ],
    "servingSize": 4
  }
  ```
- Success: 200 OK
- Errors: 400 Bad request

### POST /extract/image
- Description: Extract recipe information from an uploaded image file (JPEG/PNG)
- Authenticated: true
- Request: multipart/form-data with file parameter
- Supported formats: JPEG, PNG
- Example response:
  ```json
  {
    "name": "Veggie Burger",
    "ingredients": [
      {"name": "black beans", "quantity": 1, "unit": "cup"},
      {"name": "breadcrumbs", "quantity": 0.5, "unit": "cup"}
    ],
    "instructions": [
      {"step": "Mash the black beans in a bowl"},
      {"step": "Mix in breadcrumbs and seasonings"}
    ],
    "servingSize": 2
  }
  ```
- Success: 200 OK
- Errors: 400 Bad request (unsupported file type), 413 Payload too large
