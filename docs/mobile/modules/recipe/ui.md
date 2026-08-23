# Recipe — UI

## Screens and Widgets

- Recipe Grid Widget (`recipe_grid.dart`) - Reusable body widget displaying all available recipes in a 3-column grid
  with horizontal Recipe Filter Bar and fuzzy search bar. Recipe Filter Bar state is shared across the app. Requires
  `onRecipeTap` callback parameter to define tap behavior. Results are filtered by collection (server-side) and search
  query (client-side with fuzzy matching).
- Recipe Grid Item (`recipe_grid_item.dart`) - Reusable card widget for displaying individual recipes in a grid with a
  full-width image on top (with loading, error, and placeholder states) and a recipe name below (up to 3 lines,
  ellipsis overflow).
- Recipe Filter Bar (`recipe_filter_bar.dart`) - Self-contained horizontal scrollable chip filter widget using Material
  3 ChoiceChip components. Displays "All Recipes" (default), "Unassigned", and collection name chips. Selected filter
  persists across app restarts using SharedPreferences.
- Recipe Search Bar (`recipe_search_bar.dart`) - Search input widget accepting current search query (String) and
  onChange callback. Used by RecipeGrid for local search state. Updates parent in real-time as user types. Search
  filtering is performed by RecipeListService.getFilteredRecipes() with fuzzy matching, ranking results by match score
  with best matches at top.
- Recipe List FAB (`recipe_list_fab.dart`) - Speed Dial FAB widget for importing and creating recipes.
- Detail Screen (`recipe_detail_screen.dart`) - Displays full recipe details including image carousel (when images
  available), ingredients, and instructions. FAB shows "Add to Meal Plan" button (calendar icon). Overflow menu includes
  Edit, Share button and role-based conditional Delete button for recipe management. Shows collection name with folder
  icon when recipe is assigned to a collection. Displays source URL as clickable link with link icon when available.
  Shows serving size with restaurant icon. Keeps the screen on while the screen is active (using wakelock_plus).
- Recipe Image Carousel (`recipe_image_carousel.dart`) - Full-width image carousel widget using PageView with 1:1 aspect
  ratio. Tapping an image opens a fullscreen zoomable viewer.
- Recipe Image Fullscreen Viewer (`recipe_image_fullscreen_viewer.dart`) - Fullscreen dialog-based image viewer widget
  using photo_view package. Features pinch-to-zoom (1x to 4x), pan when zoomed, double-tap to zoom, tap-to-dismiss, and
  close button. Uses PhotoViewComputedScale for responsive scaling with transparent background.
- Source Link Widget (`source_link_widget.dart`) - Clickable widget for opening recipe source URLs using url_launcher
  package. Displays domain name extracted from URL.
- Create Recipe Screen (`create_recipe_screen.dart`) - Form-based screen for manually creating recipes using
  RecipeFormWidget. Accepts InitialRecipeFormData parameter for prefilling form data (recipe details, source URL,
  images). Automatically prefills collection dropdown when user has an active collection filter. Loads the recipe
  count on open and passes the resulting counter and blocked flag down to the form (see
  `docs/mobile/modules/limits/ui.md`).
- Edit Recipe Screen (`edit_recipe_screen.dart`) - Form-based screen for editing existing recipes using RecipeFormWidget.
- Recipe Form Widget (`recipe_form_widget.dart`) - Reusable form widget for recipe creation and editing with ingredient
  and instruction inputs, serving size input (using ServingSizeInput widget), validation, collection dropdown menu for
  optional collection assignment, image management via RecipeImageManager, and save functionality. Accepts
  InitialRecipeFormData parameter containing recipeDetail, sourceUrl, and pendingImages for prefilling form. Also
  accepts optional initialCollection parameter to prefill collection dropdown. Dropdown shows loading/error states and
  all available collections with "None" option to create recipes without collection. Prefill priority:
  initialFormData's recipeDetail collection > initialCollection > null. Optional `limitCounter` and `saveBlocked`
  parameters render a `used / limit` line above the form and disable the save button; the edit screen passes
  neither, which is what keeps the display create-only in a widget both screens share.
- Ingredient Input Widget (`ingredient_input_widget.dart`) - Reusable widget for entering ingredient name, quantity,
  and optional comment. Uses `IngredientInput` data class (name, quantityText, comment?) for input/output instead of
  `Ingredient`. Renders a name+quantity row followed by a comment field ("Comment (optional)", hint: "e.g., to taste,
  fresh").
- Ingredient bullet (`ingredient_bullet.dart`) - Small bullet point icon for ingredient lists (8px size).
- Step number badge (`step_number_badge.dart`) - Circular badge for recipe step numbers (24px container, white text).
- Recipe Image Manager (`recipe_image_manager.dart`) - Widget for managing recipe images with camera/gallery selection
  via bottom sheet modal, horizontal scrollable thumbnail list with drag-and-drop reordering (ReorderableListView) and
  remove functionality.
- Recipe Sharing Dialog (`recipe_sharing_dialog.dart`) - Modal dialog for sharing recipes with other users, featuring
  email input with validation, shared users list with UserRole enum display, and unshare functionality with Material
  Design 3 styling. Prevents users from unsharing themselves by hiding the unshare button for the current user.
- Recipe To Shopping List Screen (`recipe_to_shopping_list_screen.dart`) - Screen for adding recipe ingredients to a
  shopping list with checkbox selection for ingredients, Select All/Deselect All toggle, shopping list selection dialog,
  and integration with ShoppingListSyncService for queuing add operations. Navigates back to recipe detail on success.
- Recipe Picker Screen (`recipe_picker_screen.dart`) - Full-screen screen for selecting recipes using RecipeGrid
  component with search and filter capabilities. Uses Scaffold with AppBar and standard navigation (Navigator.push/pop).
  Returns selected Recipe via Navigator.pop when user taps an item. Route: /recipes/picker.
- Recipes Collection List Screen (`collection/recipes_collection_list_screen.dart`) - Screen for managing recipe
  collections with pull-to-refresh, FAB opening the create dialog, inline rename/delete operations via
  PopupMenuButton on each list item, and error handling with retry functionality.
- Recipes Collection Create Dialog (`collection/recipes_collection_create_dialog.dart`) - Stateful dialog widget for
  creating recipe collections with TextField input and proper TextEditingController lifecycle management. Loads the
  collection count on open, shows the `used / limit` counter under the field, and disables Create at the cap. Returns
  the trimmed name to the list screen, which performs the create.
- Recipes Collection List Item (`collection/recipes_collection_list_item.dart`) - Reusable Card widget for displaying
  individual recipe collections in a list with title, tap handling, and PopupMenuButton for rename/share/delete actions.
- Recipes Collection Rename Dialog (`collection/recipes_collection_rename_dialog.dart`) - Stateful dialog widget for
  renaming recipe collections with TextField input, proper TextEditingController lifecycle management, validation to
  prevent empty names, and pre-filled current name.

## Flows

#### Recipe Management Flow

1. **Recipe Tap** (on Recipes tab) → Recipe Detail Screen (`/recipes/:id` with recipe ID parameter)
2. **Speed Dial FAB → Extract Tap** (on Recipes tab) → Extraction Dialog → URL/Image Extraction Screen
   (`/recipes/url-extraction` or `/recipes/image-extraction`)
3. **Speed Dial FAB → Create Tap** (on Recipes tab) → Create Recipe Screen (`/recipes/create`) with collection
   pre-selected if a collection filter is active
4. **Add to Meal Plan FAB Tap** (on Recipe Detail Screen) → MealEntryFormDialog opens with recipe preselected, serving
   size pre-filled → Select plan and date → Entry created → Success message → Dialog closes
5. **Edit Menu Item Tap** (on Recipe Detail Screen) → Edit Recipe Screen (`/recipes/:id/edit` with recipe ID parameter)
6. **Share Button Tap** (on Recipe Detail Screen) → List of shared users → Share recipe → Back to Recipe Detail Screen
7. **Add to Shopping List Tap** (on Recipe Detail Screen) → Recipe To Shopping List Screen
   (`/recipes/:id/to-shopping-list`) → Select ingredients → Choose shopping list → Items queued for sync → Back to
   Recipe Detail Screen
8. **Delete Button Tap** (on Recipe Detail Screen) → Confirmation dialog → Recipe deletion → Back to Main Screen
9. **Successful URL Extraction** → Create Recipe Screen with pre-filled extracted data, source URL, and collection (if
   filter active) via InitialRecipeFormData → Recipe creation → Back to Main Screen
10. **Successful Image Extraction** → Create Recipe Screen with pre-filled extracted data, pending image, and collection
    (if filter active) via InitialRecipeFormData → Recipe creation → Back to Main Screen
11. **Successful Manual Creation** → Back to Main Screen (with recipe added to selected collection if filter was active)
12. **Successful Edit** → Back to Recipe Detail Screen (with updated data)

#### Recipe Collections Management Flow

1. **AppBar Menu → Recipes collections** (when feature flag enabled) → Recipes Collection List Screen
   (`/recipes-collections`)
2. **FAB Tap** (on Collections screen) → Create dialog with name input → Collection created → List refreshed
3. **Pull to Refresh** (on Collections screen) → Collections reloaded from API
4. **Collection Item Menu → Rename** → Rename dialog with pre-filled name → Collection renamed → List refreshed
5. **Collection Item Menu → Share** → Sharing dialog → Share/unshare collection with users → List refreshed
6. **Collection Item Menu → Delete** → Confirmation dialog → Collection deleted → List refreshed
7. **Back Button** (on Collections screen) → Back to Main Screen
