## FEATURE:

I want to implement a screen in the mobile application that allows users to create recipes manually.
The screen should be opened from the recipe list FAB button.
FAB button that already exists in the recipe list screen to open import screen should now be changed to
FAB menu button with two options: "Import" and "Create".
The screen should have input fields for recipe name, ingredients and instructions.
After clicking "Save" button, the recipe should be saved using API and user should be taken back to the recipe list
screen.
Recipes list should be refreshed after saving.

## EXAMPLES:

No examples

## DOCUMENTATION:

- `docs/` - project documentation folder

## OTHER CONSIDERATIONS:

- for ingredients input create two text fields: one for ingredient name and another for quantity with unit, after saving
  use some regex to extract quantity and unit from the text