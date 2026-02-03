# UI Overview - RecipAI

## Screens

### Core feature

- Main Screen (`main_screen.dart`) - Main application screen with embedded bottom navigation, managing recipe,
  shopping list, and planning tabs. Displays RecipeList, ShoppingListList, or MealPlanCalendarScreen widgets based on
  selected tab, with corresponding FABs (
  RecipeListFab, ShoppingListListFab, or placeholder Planning FAB). Features PopupMenuButton in AppBar with "Recipes
  collections" and logout options. When Planning tab is active and meal planning feature flag is enabled, shows "Manage
  Plans" IconButton before the overflow menu

### Recipe feature

- Recipe List Widget (`recipe_list.dart`) - Reusable body widget displaying all available recipes with horizontal
  Recipe Filter Bar and fuzzy search bar. Results are filtered by collection (server-side) and search query
  (client-side with fuzzy matching).
- Recipe List Item (`recipe_list_item.dart`) - Reusable widget for displaying individual recipes with optional
  thumbnails in a list
- Recipe Filter Bar (`recipe_filter_bar.dart`) - Self-contained horizontal scrollable chip filter widget using Material
  3
  ChoiceChip components. Displays "All Recipes" (default), "Unassigned", and collection name chips. Selected filter
  persists across app restarts using SharedPreferences.
- Recipe Search Bar (`recipe_search_bar.dart`) - Search input widget with fuzzy matching for filtering recipes by name.
  Updates results in real-time as user types. Results are ranked by fuzzy match score with best matches at top.
- Recipe List FAB (`recipe_list_fab.dart`) - Speed Dial FAB widget for importing and creating recipes
- Detail Screen (`recipe_detail_screen.dart`) - Displays full recipe details including image carousel (when images
  available), ingredients, and instructions with Edit FAB, Share button, and role-based conditional Delete button for
  recipe management. Shows collection name with folder icon when recipe is assigned to a collection. Displays source URL
  as clickable link with link icon when available.
- Recipe Image Carousel (`recipe_image_carousel.dart`) - Full-width image carousel widget using PageView with 1:1 aspect
  ratio. Tapping an image opens a fullscreen zoomable viewer.
- Recipe Image Fullscreen Viewer (`recipe_image_fullscreen_viewer.dart`) - Fullscreen dialog-based image viewer widget
  using photo_view package. Features pinch-to-zoom (1x to 4x), pan when zoomed, double-tap to zoom, tap-to-dismiss, and
  close button. Uses PhotoViewComputedScale for responsive scaling with transparent background.
- Source Link Widget (`source_link_widget.dart`) - Clickable widget for opening recipe source URLs using url_launcher
  package. Displays domain name extracted from URL.
- Create Recipe Screen (`create_recipe_screen.dart`) - Form-based screen for manually creating recipes using
  RecipeFormWidget. Accepts InitialRecipeFormData parameter for prefilling form data (recipe details, source URL,
  images).
  Automatically prefills collection dropdown when user has an active collection filter.
- Edit Recipe Screen (`edit_recipe_screen.dart`) - Form-based screen for editing existing recipes using
  RecipeFormWidget.
- Recipe Form Widget (`recipe_form_widget.dart`) - Reusable form widget for recipe creation and editing with ingredient
  and instruction inputs, validation, collection dropdown menu for optional collection assignment, image management
  via RecipeImageManager, and save functionality. Accepts InitialRecipeFormData parameter containing recipeDetail,
  sourceUrl, and pendingImages for prefilling form. Also accepts optional initialCollection parameter to prefill
  collection
  dropdown. Dropdown shows loading/error states and all available collections with "None" option to create recipes
  without collection. Prefill priority: initialFormData's recipeDetail collection > initialCollection > null.
- Ingredient Input Widget (`ingredient_input_widget.dart`) - Reusable widget for entering ingredient name and quantity
  with validation
- Ingredient bullet (`ingredient_bullet.dart`) - Small bullet point icon for ingredient lists (8px size)
- Step number badge (`step_number_badge.dart`) - Circular badge for recipe step numbers (24px container, white text)
- Recipe Image Input (`recipe_image_input.dart`) - Data model for managing recipe images with support for both new
  uploads (XFile) and existing images (URL). Uses UUID for image tracking
- Initial Recipe Form Data (`initial_recipe_form_data.dart`) - Wrapper class for passing recipe data for prefilling.
- Recipe Image Manager (`recipe_image_manager.dart`) - Widget for managing recipe images with camera/gallery selection
  via bottom sheet modal, horizontal scrollable thumbnail list with drag-and-drop reordering (ReorderableListView) and
  remove functionality
- Recipe Sharing Dialog (`recipe_sharing_dialog.dart`) - Modal dialog for sharing recipes with other users, featuring
  email input with validation, shared users list with UserRole enum display, and unshare functionality with Material
  Design 3 styling. Prevents users from unsharing themselves by hiding the unshare button for the current user
- Recipe To Shopping List Screen (`recipe_to_shopping_list_screen.dart`) - Screen for adding recipe ingredients to a
  shopping list with checkbox selection for ingredients, Select All/Deselect All toggle, shopping list selection dialog,
  and integration with ShoppingListSyncService for queuing add operations. Navigates back to recipe detail on success
- Recipes Collection List Screen (`collection/recipes_collection_list_screen.dart`) - Screen for managing recipe
  collections with pull-to-refresh, FAB for creating new collections, inline rename/delete operations via
  PopupMenuButton
  on each list item, and error handling with retry functionality
- Recipes Collection List Item (`collection/recipes_collection_list_item.dart`) - Reusable Card widget for displaying
  individual recipe collections in a list with title, tap handling, and PopupMenuButton for rename/share/delete actions
- Recipes Collection Rename Dialog (`collection/recipes_collection_rename_dialog.dart`) - Stateful dialog widget for
  renaming recipe collections with TextField input, proper TextEditingController lifecycle management, validation to
  prevent empty names, and pre-filled current name

### Auth feature

- Login Screen (`login_screen.dart`) - Welcome screen with Google Sign-In button, app branding (RecipAI logo and
  title), loading states during authentication, and error handling for sign-in failures
- Auth Service (`auth_service.dart`) - Abstract service interface defining authentication contracts with
  `isAuthenticated`, `email`, `idToken`, `signIn()`, and `signOut()` methods
- Firebase Auth Service (`firebase_auth_service.dart`) - Firebase implementation with Google Sign-In integration,
  user state management, and automatic token refresh

### Shopping List feature

- Shopping List List Widget (`shopping_list_list.dart`) - Reusable body widget displaying all shopping lists with
  pull-to-refresh and navigation to detail screen on tap
- Shopping List List FAB (`shopping_list_list_fab.dart`) - FloatingActionButton widget for creating new shopping lists
  with dialog
- Shopping List Detail Screen (`shopping_list_detail_screen.dart`) - Displays individual shopping list with inline item
  management, drag-and-drop reordering, real-time sync status indicator, and optimistic UI updates. Items are organized
  into two sections: active items (unchecked) at the top and "Done" section at the bottom for checked items, separated
  by the add item widget and a "Done" header. Each section uses ReorderableListView with custom drag handles for item
  reordering within the section (drag-and-drop is restricted to within sections, not between them). The Done section
  uses AnimatedSize for smooth expand/collapse transitions when items are checked/unchecked. Features PopupMenuButton
  with actions: "Rename List", "Share List", "Delete all checked" (bulk delete checked items), and "Uncheck all"
  (bulk uncheck checked items). Integrates with ShoppingListSyncService for background syncing (10-second polling)
  and conflict resolution with user notifications
- Shopping List Item Widget (`shopping_list_item_widget.dart`) - Reusable inline-editable widget for shopping list
  items with smart text parsing (supports "2 kg apples", "500g flour", "bread" formats), automatic quantity/unit
  extraction using regex, TextField-based editing with focus management, optional drag handle for reordering (using
  ReorderableDragStartListener), and visual states (checked items with strikethrough). Drag handle is only shown
  when showDragHandle parameter is true, positioned on the left side before the checkbox
- Shopping List Item Add Widget (`shopping_list_item_add_widget.dart`) - Dedicated widget for adding new shopping list
  items with plus icon, "Add item..." hint text, smart text parsing (same as ShoppingListItemWidget), and automatic
  field clearing with focus retention after submission for quick consecutive entry
- Shopping List Rename Dialog (`shopping_list_rename_dialog.dart`) - Stateful dialog widget for renaming shopping lists
  with TextField input, proper TextEditingController lifecycle management, and validation to prevent empty names

### Meal Planning feature

- Meal Plan Calendar Screen (`meal_plan_calendar_screen.dart`) - Agenda view displaying weekly meal plan entries with
  week navigation strip, 7 vertical day sections (locale-aware first day of week), pull-to-refresh, and entry tap
  handling. Features right-side drawer for plan management accessed via "Manage Plans" button (calendar icon) when
  Planning tab is active. All dates formatted according to user locale.
- Meal Plan Drawer (`meal_plan_drawer.dart`) - Side drawer for managing meal plans with unified list of all plans
  (personal and shared), visibility checkboxes for filtering calendar display, and three-dot menu per plan for
  Edit/Share/Delete actions (Delete only visible to owners). Includes "Create New Plan" button and pull-to-refresh
  functionality.
- Plan List Tile (`plan_list_tile.dart`) - Card widget for individual plans with color indicator (CircleAvatar), plan
  name, visibility checkbox, and PopupMenuButton with role-based menu items (Edit, Share for all; Delete for owners
  only).
- Week Strip (`week_strip.dart`) - Week navigation header widget showing current week range with previous/next week
  navigation buttons and tappable week label to jump to today. Date range formatted according to user locale.
- Day Section (`day_section.dart`) - Day container widget showing date header (highlighted for today) and list of meal
  entries, or "No meals planned" empty state. Day header formatted according to user locale.
- Meal Entry Calendar Card (`meal_entry_calendar_card.dart`) - Card widget for individual meal entries with background
  color from plan color, recipe name or placeholder text, serving size, and placeholder overflow menu for edit/delete
  actions. Text color adapts to background luminance for readability.

### Extraction feature

- URL Extraction Screen (`url_extraction_screen.dart`) - WebView-based screen for extracting recipes from web pages with
  smart input field that automatically detects URLs vs search queries. Supports domain patterns (example.com,
  sub.example.co.uk, localhost:3000) and full URLs (https://example.com). Non-URL inputs trigger Google search with
  encoded query parameters. Captures the current URL from WebView and navigates to create screen with
  InitialRecipeFormData containing extracted recipe detail and source URL
- Image Extraction Screen (`image_extraction_screen.dart`) - Screen for extracting recipes from images using camera or
  gallery selection with image preview and upload functionality. Navigates to create screen with InitialRecipeFormData
  containing extracted recipe detail and the selected image file as a pending image
- Extraction Dialog (`extraction_dialog.dart`) - Modal dialog for choosing between URL and image extraction methods with
  Material Design buttons
- Web Recipe Extractor (`web_recipe_extractor.dart`) - Utility class for extracting HTML content from WebView

## Shared Widgets

- **Loading Widget** (`loading_widget.dart`) - Reusable loading indicator for async operations
- **API Error Widget** (`api_error_widget.dart`) - Reusable error display with retry functionality for API failures
- **Error Icon** (`error_icon.dart`) - Standardized error icon (64px, theme-based color)

### Generic Sharing Dialog

- **Implementation**: `lib/core/widgets/sharing_dialog.dart`
- **Purpose**: Reusable dialog for managing ACLs (Recipes, Recipes Collections, Shopping Lists)
- **SharedUser DTO**: Simple UI model with email (String), role (String displayName), isCurrentUser (bool)
- **Usage Pattern**:
    1. Service loads feature-specific Permission models (RecipePermission, RecipesCollectionPermission,
       ShoppingListPermission)
    2. Service wraps Permissions with isCurrentUser flag (RecipeSharedUser, etc.)
    3. Screen creates ValueNotifier<AsyncValue<List<SharedUser>>> for mapped data
    4. Screen sets up listener to map from SharedUser wrapper to SharedUser DTO
    5. Screen passes mapped notifier and callbacks to SharingDialog
    6. Dialog handles UI, validation, and user interactions
    7. Screen handles success/error SnackBar feedback
    8. Screen cleans up listener and disposes mapped notifier on dialog close

## Navigation

### Route Structure

The app uses a simple GoRoute structure with embedded bottom navigation in MainScreen:

#### Authentication Routes

- `/login` - Authentication screen with Google Sign-In (only accessible when unauthenticated)

#### Main App Routes

- `/` - Main screen with embedded bottom navigation (AppRoute.main)
    - Tab 1: Recipes (default) - Displays RecipeList widget
    - Tab 2: Shopping - Displays ShoppingListList widget
  - Tab 3: Planning - Displays MealPlanCalendarScreen widget with right-side drawer for plan management
    - `/recipes/url-extraction` - URL extraction screen (nested route)
    - `/recipes/image-extraction` - Image extraction screen (nested route)
    - `/recipes/create` - Recipe creation screen (nested route)
    - `/recipes/:id` - Recipe detail screen with dynamic ID parameter (nested route)
    - `/recipes/:id/edit` - Recipe edit screen with dynamic ID parameter (nested route)
  - `/recipes/:id/to-shopping-list` - Add ingredients to shopping list screen (nested route)
  - `/recipes-collections` - Recipe collections list screen (AppRoute.recipesCollections, shown when feature flag
    enabled)
  - `/shopping-lists/:id` - Shopping list detail screen with dynamic ID parameter (AppRoute.shoppingListDetail)

### Bottom Navigation Bar

The app features an embedded bottom navigation bar (part of MainScreen) with tabs:

- **Recipes** (restaurant_menu icon) - Access recipe management features
- **Shopping** (shopping_cart icon) - Access shopping list features
- **Planning** (calendar_today icon) - Access meal planning agenda view

Tab state is ephemeral and not preserved across app restarts. Navigation to sub-routes (like recipe detail) pushes a new
screen on top of MainScreen.

### Authentication & Route Protection

All routes except `/login` require user authentication. The app automatically redirects:

- Unauthenticated users → `/login`
- Authenticated users on `/login` → `/` (main screen)

### Flow

#### Authentication Flow

1. **App Launch** → Authentication check:
    - **If unauthenticated** → Login Screen (`/login`)
   - **If authenticated** → Main Screen (`/`) showing Recipes tab
2. **Login Screen → Google Sign-In Tap** → Authentication process → Main Screen (`/`)
3. **Main Screen → Logout Tap** → Confirmation dialog → Sign out → Login Screen (`/login`)

#### Recipe Management Flow

1. **Recipe Tap** (on Recipes tab) → Recipe Detail Screen (`/recipes/:id` with recipe ID parameter)
2. **Speed Dial FAB → Extract Tap** (on Recipes tab) → Extraction Dialog → URL/Image Extraction Screen (
   `/recipes/url-extraction` or `/recipes/image-extraction`)
3. **Speed Dial FAB → Create Tap** (on Recipes tab) → Create Recipe Screen (`/recipes/create`) with collection
   pre-selected if a collection filter is active
4. **Edit FAB Tap** (on Recipe Detail Screen) → Edit Recipe Screen (`/recipes/:id/edit` with recipe ID parameter)
5. **Share Button Tap** (on Recipe Detail Screen) → List of shared users → Share recipe → Back to Recipe Detail Screen
6. **Add to Shopping List Tap** (on Recipe Detail Screen) → Recipe To Shopping List Screen (
   `/recipes/:id/to-shopping-list`) → Select ingredients → Choose shopping list → Items queued for sync → Back to Recipe
   Detail Screen
7. **Delete Button Tap** (on Recipe Detail Screen) → Confirmation dialog → Recipe deletion → Back to Main Screen
8. **Successful URL Extraction** → Create Recipe Screen with pre-filled extracted data, source URL, and collection (if
   filter active) via InitialRecipeFormData → Recipe creation → Back to Main Screen
9. **Successful Image Extraction** → Create Recipe Screen with pre-filled extracted data, pending image, and
   collection (
   if filter active) via InitialRecipeFormData → Recipe creation → Back to Main Screen
10. **Successful Manual Creation** → Back to Main Screen (with recipe added to selected collection if filter was active)
11. **Successful Edit** → Back to Recipe Detail Screen (with updated data)

#### Recipe Collections Management Flow

1. **AppBar Menu → Recipes collections** (when feature flag enabled) → Recipes Collection List Screen (
   `/recipes-collections`)
2. **FAB Tap** (on Collections screen) → Create dialog with name input → Collection created → List refreshed
3. **Pull to Refresh** (on Collections screen) → Collections reloaded from API
4. **Collection Item Menu → Rename** → Rename dialog with pre-filled name → Collection renamed → List refreshed
5. **Collection Item Menu → Share** → Sharing dialog → Share/unshare collection with users → List refreshed
6. **Collection Item Menu → Delete** → Confirmation dialog → Collection deleted → List refreshed
7. **Back Button** (on Collections screen) → Back to Main Screen

#### Shopping List Management Flow

1. **Bottom Navigation → Shopping Tab** → MainScreen switches to Shopping tab view
2. **FAB Tap** (on Shopping tab) → Create dialog with name input → Shopping list created → List refreshed
3. **Pull to Refresh** (on Shopping tab) → Shopping lists reloaded from API
4. **Shopping List Tap** → Shopping List Detail Screen (`/shopping-lists/:id` with shopping list ID parameter)
5. **Back Button** (on Shopping List Detail Screen) → Back to Shopping tab on Main Screen

#### Meal Planning Management Flow

1. **Bottom Navigation → Planning Tab** → MainScreen switches to Planning tab view with calendar
2. **Manage Plans Button Tap** (calendar icon in AppBar) → Opens MealPlanDrawer overlay from right side
3. **Pull to Refresh** (in drawer) → Meal plans reloaded from API
4. **Visibility Checkbox Tap** (in drawer) → Toggle plan visibility → Calendar refreshes automatically to show/hide plan
   entries
5. **Plan Menu → Delete** (owners only) → SnackBar "Delete feature coming soon" (placeholder)
6. **Plan Menu → Edit** → SnackBar "Edit feature coming soon" (placeholder)
7. **Plan Menu → Share** → SnackBar "Share feature coming soon" (placeholder)
8. **Create New Plan Button** (bottom of drawer) → SnackBar "Create plan feature coming soon" (placeholder)
9. **Close Drawer** → Swipe left or tap outside drawer → Returns to calendar view
10. **Switch Tabs** → Drawer closes automatically

## Theme System

The app uses a centralized theming approach with Material Design 3, configured in `core/theme.dart`:

### AppTheme

- **Material 3**
- **Color Scheme**: Generated from `Colors.deepOrange` seed color
- **Usage**: Applied in `main.dart` as `theme: AppTheme.theme`

### AppSpacing Constants

- **screenPadding**: `EdgeInsets.all(16.0)` - Standard screen padding
- **cardMargin**: `EdgeInsets.symmetric(horizontal: 16.0, vertical: 4.0)` - Card margins
- **listTilePadding**: `EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0)` - ListTile content padding
- **smallVertical**: `EdgeInsets.symmetric(vertical: 4.0)` - Small vertical spacing
- **mediumVertical**: `EdgeInsets.symmetric(vertical: 8.0)` - Medium vertical spacing
- **Spacing Values**: `small` (8dp), `medium` (16dp), `large` (24dp), `extraSmall` (4dp), `extraLarge` (32dp)

### AppAnimations Constants

- **sectionTransition**: `Duration(milliseconds: 300)` - Duration for section expand/collapse animations
- **sectionCurve**: `Curves.easeInOut` - Animation curve for smooth section transitions