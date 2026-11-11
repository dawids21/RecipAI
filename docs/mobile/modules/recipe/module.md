# Recipe Module

Recipes, the app's main content: the Recipes tab's grid with collection filter and fuzzy search, recipe detail,
create and edit forms with images, sharing, and the hand-offs to meal plans and shopping lists. The `collection/`
sub-feature manages recipe collections (create, rename, share, delete), which the grid filters by.

## Boundary

- **Backend endpoints**: `/recipes` and `/collections` with their `balance`, `permissions`, `share` and `unshare`
  endpoints (`docs/backend/modules/recipes/api.md`). Recipes with images are sent as multipart.
- **Uses**: `auth`; `limits` (recipe and collection counters and quotas); `sharing` (recipe and collection sharing
  dialogs); `extraction` (the Extract action opens its dialog); `planning` (Add to Meal Plan: `MealEntryFormDialog`
  and `MealPlanCalendarService`); `shopping_list` (Add to Shopping List: list picker, review widget and import
  service).
- **Used by**: `extraction` (passes `InitialRecipeFormData` to the create screen), `planning` (`Recipe` model, the
  recipe picker route, the recipe detail route), `shopping_list` (builds review items from `Ingredient`), `invites`
  (reloads the recipe and collection lists on accept), `core` (Recipes tab, routes).

## File Map

```
mobile/lib/features/recipe/
├── recipe.dart                         # Recipe list-item model (id, name, thumbnail)
├── recipe_detail.dart                  # RecipeDetail and its parts (images, ingredients, instructions); RecipeRequest
├── recipe_image_input.dart             # A form image: existing (URL) or newly picked (file)
├── initial_recipe_form_data.dart       # Prefill for the create screen: extracted detail, source URL, pending images
├── recipe_repository.dart              # /recipes endpoints
├── recipe_list_service.dart            # Recipe list, filter, search and balance (see Services and State)
├── recipe_detail_service.dart          # One recipe's detail and permissions (see Services and State)
├── recipe_setup.dart
├── recipe_grid.dart                    # Recipes tab body, also reused by the picker: filter bar, search, grid
├── recipe_grid_item.dart               # One recipe card: image and name
├── recipe_filter_bar.dart              # Collection filter chips: All Recipes, Unassigned, each collection
├── recipe_search_bar.dart              # Search field feeding the grid's local query
├── recipe_list_fab.dart                # Speed dial: extract or create
├── recipe_picker_screen.dart           # /recipes/picker — returns the tapped Recipe to the caller
├── recipe_detail_screen.dart           # Recipe detail with edit, share, delete, Add to Meal Plan and Add to Shopping List
├── create_recipe_screen.dart           # Create form; limited (see limits) and prefilled from extraction or the filter
├── edit_recipe_screen.dart             # Edit form for an existing recipe
├── recipe_form_widget.dart             # Form shared by create and edit
├── ingredient_input_widget.dart        # One ingredient's name, quantity and comment inputs
├── ingredient_bullet.dart
├── step_number_badge.dart
├── recipe_image_carousel.dart          # Detail-screen image carousel; tap opens the fullscreen viewer
├── recipe_image_fullscreen_viewer.dart # Fullscreen pinch-to-zoom viewer
├── recipe_image_manager.dart           # Form image picker: add from camera/gallery, reorder, remove
├── source_link_widget.dart             # Opens the recipe's source URL
├── recipe_sharing_dialog.dart          # Binds RecipeDetailService to SharingDialog
├── recipe_to_shopping_list_screen.dart # /recipes/:id/to-shopping-list — ingredients into a chosen list
└── collection/
    ├── recipes_collection.dart                 # RecipesCollection model
    ├── recipes_collection_repository.dart      # /collections endpoints
    ├── recipes_collection_list_service.dart    # Collections, balance and sharing (see Services and State)
    ├── recipes_collection_setup.dart
    ├── recipes_collection_list_screen.dart     # /recipes-collections — create, rename, share, delete; opens SharingDialog
    ├── recipes_collection_list_item.dart       # One collection row with its menu
    ├── recipes_collection_create_dialog.dart   # Name input; limited (see limits); the list screen performs the create
    └── recipes_collection_rename_dialog.dart
```

```
mobile/test/features/recipe/
└── main_screen_recipes_tab_widget_test.dart
```

## Services and State

- **`RecipeListService`** — session-scoped lazy singleton (see `docs/mobile/modules/core/module.md` → Services and
  State). Holds the recipe list, the selected collection filter and the recipe balance. The filter is applied by the
  server (all, unassigned, or one collection), persisted in `PreferencesService` and restored when the service is
  created, so it survives restarts and is shared by every grid, including the picker's. Search is applied on the
  device to the loaded list (fuzzy match on the name, best match first) and is not service state.
- **`RecipeDetailService`** — lazy singleton reset when the recipe detail screen is disposed. The edit and
  add-to-shopping-list routes are nested under the detail route, so they share the detail screen's instance. Holds
  one recipe's detail and its permissions. Updating or deleting the recipe also reloads the recipe list, as creating
  one through `RecipeListService` does.
- **`RecipesCollectionListService`** — session-scoped lazy singleton holding the collection list, the collection
  balance, and the permissions of the collection whose sharing dialog is open. Create, rename and delete reload the
  collection list; an unshare reloads it too, since the user may have removed their own access.

## Cross-module Flows

### Add to Shopping List

The add-to-shopping-list screen turns the recipe's ingredients into review items and shows `shopping_list`'s review
widget; the user selects and edits items and picks a list, and the selected items are written through
`ShoppingListItemImportService` (see `docs/mobile/modules/shopping_list/module.md` → Services and State).
