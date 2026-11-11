# Recipe — UI

## Screens and Widgets

### Recipes Tab

- Recipe Grid Widget (`recipe_grid.dart`) - Recipes tab body, also used by the Recipe Picker: the Recipe Filter Bar, a
  search bar and a 3-column grid of recipes. What a tap does is up to the host screen. How filter and search apply is
  in `module.md` → Services and State.
- Recipe Grid Item (`recipe_grid_item.dart`) - One recipe card: its image on top (with loading, error and placeholder
  states) and its name below, up to 3 lines.
- Recipe Filter Bar (`recipe_filter_bar.dart`) - A scrollable row of filter chips: "All Recipes" (default),
  "Unassigned", and one per collection. The selection survives restarts and is shared by every grid.
- Recipe Search Bar (`recipe_search_bar.dart`) - "Search recipes..." field; the grid narrows as the user types, best
  matches first.
- Recipe List FAB (`recipe_list_fab.dart`) - Speed dial with Extract and Create.

### Recipe Detail

- Detail Screen (`recipe_detail_screen.dart`) - One recipe, read-only:
  - *Content*: the image carousel (when the recipe has images), name, collection (when assigned), source link (when
    set), servings, ingredients and instructions.
  - *Actions*: the "Add to Meal Plan" FAB; the menu holds "Edit Recipe", "Add to Shopping List", "Share Recipe", and
    "Delete Recipe" for owners only.
  - The screen stays awake while it is open, for cooking from it.
- Recipe Image Carousel (`recipe_image_carousel.dart`) - Swipeable full-width square images; tapping one opens the
  fullscreen viewer.
- Recipe Image Fullscreen Viewer (`recipe_image_fullscreen_viewer.dart`) - Fullscreen image with pinch-to-zoom (1x to
  4x), pan when zoomed, double-tap to zoom, tap to dismiss, and a close button.
- Source Link Widget (`source_link_widget.dart`) - Shows the source URL's domain; tapping opens the URL.
- Ingredient bullet (`ingredient_bullet.dart`) - The bullet before each ingredient.
- Step number badge (`step_number_badge.dart`) - The round step number before each instruction.
- Recipe Sharing Dialog (`recipe_sharing_dialog.dart`) - The sharing dialog for a recipe
  (`docs/mobile/modules/sharing/ui.md`).
- Recipe To Shopping List Screen (`recipe_to_shopping_list_screen.dart`) - The recipe's ingredients in the Shopping
  List Review Widget (`docs/mobile/modules/shopping_list/ui.md`), each with the recipe name as its source, to select,
  edit and add to a chosen list (see `module.md` → Cross-module Flows). Returns to recipe detail on success.

### Create and Edit

- Create Recipe Screen (`create_recipe_screen.dart`) - The recipe form for a new recipe:
  - *Prefill*: from extraction, the extracted recipe, source URL and images; the collection from the active filter.
  - *Limit*: the `used / limit` counter above the form, and Save disabled at the quota (see
    `docs/mobile/modules/limits/module.md` → Services and State).
- Edit Recipe Screen (`edit_recipe_screen.dart`) - The recipe form for an existing recipe; no counter.
- Recipe Form Widget (`recipe_form_widget.dart`) - The form both screens share:
  - *Fields*: recipe name, collection ("None" or any collection; shows loading and error states), source URL
    (optional), serving size, ingredients ("Add" appends one), cooking instructions (one step per line), and images.
  - *Collection prefill*: the collection of the recipe being edited or extracted wins over the active filter's.
  - *Validation*: name, instructions and each ingredient's name are required; a source URL must be valid.
- Ingredient Input Widget (`ingredient_input_widget.dart`) - One ingredient: name and quantity on one row, then
  "Comment (optional)" (hint: "e.g., to taste, fresh").
- Recipe Image Manager (`recipe_image_manager.dart`) - Add images from the camera or gallery (images over 5MB are
  refused), shown as a scrollable strip of thumbnails that can be reordered by drag and removed.

### Picker

- Recipe Picker Screen (`recipe_picker_screen.dart`) - Full-screen Recipe Grid with search and filter; tapping a
  recipe returns it to the screen that opened the picker.

### Collections

- Recipes Collection List Screen (`collection/recipes_collection_list_screen.dart`) - Every collection, with
  pull-to-refresh, a FAB opening the create dialog, a per-collection menu (Rename, Share, Delete), and an error state
  with Retry.
- Recipes Collection Create Dialog (`collection/recipes_collection_create_dialog.dart`) - "Create recipes collection":
  a name field with the `used / limit` counter under it; Create is disabled at the quota. The name is trimmed.
- Recipes Collection List Item (`collection/recipes_collection_list_item.dart`) - One collection: its name and its
  menu.
- Recipes Collection Rename Dialog (`collection/recipes_collection_rename_dialog.dart`) - "Rename recipes collection":
  the name field, pre-filled; empty names are refused.

## Flows

#### Recipe Management Flow

1. **Recipe Tap** (on Recipes tab) → Recipe Detail Screen (`/recipes/:id`)
2. **Speed Dial FAB → Extract Tap** (on Recipes tab) → see `docs/mobile/modules/extraction/ui.md` → Extraction Flow
3. **Speed Dial FAB → Create Tap** (on Recipes tab) → Create Recipe Screen (`/recipes/create`) with collection
   pre-selected if a collection filter is active
4. **Add to Meal Plan FAB Tap** (on Recipe Detail Screen) → Meal Entry Form Dialog
   (`docs/mobile/modules/planning/ui.md`) with the recipe preselected and its serving size pre-filled → Select plan
   and date → Entry created → Success message → Dialog closes
5. **Edit Recipe** (Recipe Detail Screen menu) → Edit Recipe Screen (`/recipes/:id/edit`)
6. **Share Recipe** (Recipe Detail Screen menu) → Recipe Sharing Dialog → Share by email (invitation sent) → Back to
   Recipe Detail Screen
7. **Add to Shopping List** (Recipe Detail Screen menu) → Recipe To Shopping List Screen
   (`/recipes/:id/to-shopping-list`) → Select ingredients → Choose shopping list → Items queued for sync → Back to
   Recipe Detail Screen
8. **Delete Recipe** (Recipe Detail Screen menu, owners only) → Confirmation dialog → Recipe deletion → Back to Main
   Screen
9. **Successful Manual Creation** → Back to Main Screen (with recipe added to selected collection if filter was active)
10. **Successful Edit** → Back to Recipe Detail Screen (with updated data)

#### Recipe Collections Management Flow

1. **App bar menu → Recipes collections** → Recipes Collection List Screen (`/recipes-collections`)
2. **FAB Tap** (on Collections screen) → Create dialog with name input → Collection created → List refreshed
3. **Pull to Refresh** (on Collections screen) → Collections reloaded from API
4. **Collection Item Menu → Rename** → Rename dialog with pre-filled name → Collection renamed → List refreshed
5. **Collection Item Menu → Share** → Sharing dialog → Invite a user by email, or cancel an invite / remove a user →
   List refreshed
6. **Collection Item Menu → Delete** → Confirmation dialog → Collection deleted → List refreshed
7. **Back Button** (on Collections screen) → Back to Main Screen
