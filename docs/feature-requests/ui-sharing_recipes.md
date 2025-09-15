## FEATURE:

Add option to share recipes with other users via the UI.
To do that add new option to AppBar on RecipeDetailScreen.
When user clicks on that option show popup with list of users the recipe is shared with and their roles (OWNER or
EDITOR).
Also add input field to add new user by email and share the recipe with them (set role to EDITOR).
Next to each user in the list add button to unshare the recipe from that user (can't unshare from OWNER).

Additionally, RecipeDTO now will include a `role` field to indicate the user's access level for that recipe.
Use this field to conditionally render button to delete the recipe only if the user is the OWNER.

## EXAMPLES:

- None

## DOCUMENTATION:

- `docs/mobile/mobile.md` - Mobile app overview
- `docs/mobile/ui.md` - Mobile app UI components and structure
- `docs/backend/api.md` - Backend API documentation (for reference on new endpoints)

## OTHER CONSIDERATIONS:

- None
