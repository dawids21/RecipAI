## FEATURE:

I want to remove the dependency between "extraction" and "recipes" modules in the API.
Currently, after the extraction the recipe is saved using recipes module.
Remove it and send the extracted data directly to the client.
Remove any references to recipes in the extraction module.
Create its own DTOs.
Remove "public" keyword from the recipes module when it is no longer needed.

## EXAMPLES:

No examples

## DOCUMENTATION:

- `docs/prd.md` - Product Requirements Document
- `docs/backend/api.md` - API Documentation (will need to be updated)
- `docs/backend/backend.md` - Backend App Overview (will need to be updated)

## OTHER CONSIDERATIONS:

- Mobile app will be updated later to support this change, don't worry about it now.