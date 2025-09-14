## FEATURE:

Add an API endpoint that will return all users that a recipe is shared with.
The list should include roles of each user (OWNER or EDITOR).
New endpoint: `GET /recipes/{id}/shared_users`

## EXAMPLES:

### JSON example

```json
[
  {
    "email": "user@example.com",
    "role": "OWNER"
  },
  {
    "email": "user2@example.com",
    "role": "EDITOR"
  }
]
```

## DOCUMENTATION:

- `docs/backend/backend.md` - Backend app overview
- `docs/backend/api.md` - API documentation

## OTHER CONSIDERATIONS:

- None