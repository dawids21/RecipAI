## FEATURE:

Currently, recipes are shared among all users. I want to change this schema so that each recipe is asscociated with a
specific group of users.
When a user creates a recipe, it should be associated with their email (extracted from the JWT token).
When fetching recipes, the API should return only recipes associated with the authenticated user.
When updating or deleting a recipe, the API should ensure that the recipe belongs to the authenticated user.
Relationship between users and recipes should be many-to-many, allowing multiple users to share the same recipe.
Changes should be in "recipes" module.

## EXAMPLES:

- None

## DOCUMENTATION:

- `docs/backend/backend.md` - backend app overview
- `docs/backend/api.md` - backend API documentation
- `docs/backend/db.md` - database schema

## OTHER CONSIDERATIONS:

- None