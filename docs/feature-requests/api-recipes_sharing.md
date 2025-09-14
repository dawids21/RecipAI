## FEATURE:

I want to implement sharing feature for recipes between users.
First, I want to implement the API.
Before sharing implementation I need to change a few things in the current API:

1. Add column ROLE to the user_recipes table to distinguish between owner (OWNER role) and shared users (EDITOR role).
2. Delete current methods from UserRecipesRepository and replace them with: `isOwner(email, recipeId)`,
   `isEditor(email, recipeId)`, `getUserRole(email, recipeId)`. Use `@Query` for these methods.
3. In RecipeDto add field `role` to indicate the user's role for that recipe.
4. When adding a new recipe to user_recipes table, set the role to OWNER.
5. When updating the recipe, check if the user is the OWNER or EDTIOR.
6. When deleting the recipe, check if the user is the OWNER.

Next implement the sharing feature:

1. Add new methods in RecipeService:
    1. `shareRecipe(email, recipeId)` - share the recipe with another user (set role to EDITOR)
    2. `unshareRecipe(email, recipeId)` - unshare the recipe from a user (can't unshare from OWNER)
2. Add new endpoints in RecipeController:
    1. `POST /recipes/{id}/share` - share the recipe with another user (request body contains email)
    2. `POST /recipes/{id}/unshare` - unshare the recipe from a user (request body contains email)

Use create and delete methods from UserRecipesRepository to implement sharing and unsharing.

## EXAMPLES:

- None

## DOCUMENTATION:

- `docs/backend/backend.md` - Backend app overview
- `docs/backend/api.md` - API documentation
- `docs/backend/db.md` - Database schema documentation

## OTHER CONSIDERATIONS:

- None