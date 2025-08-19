## FEATURE:

Due to recent changes on the API side using the `extraction` module no longer saves the extracted recipe in the
database.
Instead, it sends the extracted data directly to the client.
We need to refactor the UI to support this change.
Right now after import is finished user is taken to the recipe list page.
Instead, we want to show the recipe create page with the imported recipe data pre-filled so user can edit it and save it
to the database.
After that user will be taken to the recipe list page.
Additionally rename `import` feature to `extraction` feature (together with the screen)

## EXAMPLES:

No examples

## DOCUMENTATION:

- `docs/backend/api.md` - API Documentation
- `docs/mobile/mobile.md` - Mobile App Overview
- `docs/mobile/ui.md` - Mobile UI Overview
- `docs/reports/2025-08-19-api-refactor-remove-dependency-extraction-recipes.md` - Report on API refactor removing
  dependency between extraction and recipes modules

## OTHER CONSIDERATIONS:

- In the `create_recipe_screen.dart` we need to support two modes, creating recipe from scratch and creating recipe from
  imported data.