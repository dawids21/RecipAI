# Extraction API

Both endpoints consume one unit of the caller's `EXTRACTION` budget, reserved *before* the AI
provider is called and identified by the `email` claim of the JWT. A refused call consumes nothing;
a call that reaches the provider and then fails still consumes its unit — there is no refund. See
`docs/backend/modules/limits/` for how the budget is configured and changed.

## Refusal Response

A call past the budget returns **429 Too Many Requests** with an RFC 7807 `ProblemDetail`:

```json
{
  "type": "about:blank",
  "title": "Limit Exceeded",
  "status": 429,
  "detail": "Limit for EXTRACTION reached (2 of 2 used)",
  "resource": "EXTRACTION",
  "kind": "FLOW",
  "limit": 2,
  "used": 2
}
```

`retryAfterSeconds` and the `Retry-After` header are present only when the quota restarts on a period.
The seeded `EXTRACTION` quota is an "N ever" allowance, so it carries neither.

### GET /extract/balance
- Description: Get how much of the caller's `EXTRACTION` budget is already spent, for displaying
  `used / limit` before an extraction is started
- Authenticated: true
- Example response — the seeded `EXTRACTION` quota is a periodless `FLOW`, so no `resetsInSeconds`:
  ```json
  {
    "used": 1,
    "periodStart": "2026-08-23T10:00:00Z"
  }
  ```
- Success: 200 OK
- See `docs/backend/modules/limits/api.md` for the contract these balance reads share.

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
- Errors: 400 Bad request, 429 Too many requests (extraction budget exhausted), 500 Internal server error (`Extraction Failed` — the AI provider returned no recipe)

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
- Errors: 400 Bad request (`Unsupported Image Type` — validated before any budget is reserved), 413 Payload too large, 429 Too many requests (extraction budget exhausted), 500 Internal server error (`Extraction Failed` — the AI provider returned no recipe)
