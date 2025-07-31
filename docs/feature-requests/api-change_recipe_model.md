## FEATURE:

Change the API model for recipes. Currently, it has id, name and data field.
Data field is not structured.
Change it to still have a data field but with the following structure:

```json
{
  "ingredients": [
    {
      "name": "string",
      "quantity": "string",
      "unit": "string"
    }
  ],
  "instructions": [
    {
      "step": "string"
    }
  ]
}
```

Don't change the database schema, just the API model.

## EXAMPLES:

It's a simple change, so no specific examples are needed.

## DOCUMENTATION:

- `docs/backend/api.md` - Update the API documentation to reflect the new structure of the recipe data field.

## OTHER CONSIDERATIONS:

None.
