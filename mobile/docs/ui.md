# UI Overview - RecipAI

## Screens

### Core feature

- Main Screen (`main_screen.dart`) - Main application screen with embedded bottom navigation, managing recipe,
  planning, and shopping list tabs. Displays RecipeGrid, MealPlanCalendarScreen, or ShoppingListList widgets based on
  selected tab, with corresponding FABs (
  RecipeListFab, MealPlanCalendarFab, or ShoppingListListFab). Features PopupMenuButton in AppBar with "Recipes
  collections", "Generate shopping list", and logout options. When Planning tab is active and meal planning feature flag
  is enabled, shows "Manage
  Plans" IconButton before the overflow menu

### Recipe feature

- Recipe Grid Widget (`recipe_grid.dart`) - Reusable body widget displaying all available recipes in a 3-column grid
  with horizontal Recipe Filter Bar and fuzzy search bar. Recipe Filter Bar state is shared across the app. Requires
  `onRecipeTap` callback parameter to define tap behavior. Results are filtered by collection (server-side) and search
  query (client-side with fuzzy matching).
- Recipe Grid Item (`recipe_grid_item.dart`) - Reusable card widget for displaying individual recipes in a grid with a
  full-width image on top (with loading, error, and placeholder states) and a recipe name below (up to 3 lines,
  ellipsis overflow)
- Recipe Filter Bar (`recipe_filter_bar.dart`) - Self-contained horizontal scrollable chip filter widget using Material
  3
  ChoiceChip components. Displays "All Recipes" (default), "Unassigned", and collection name chips. Selected filter
  persists across app restarts using SharedPreferences.
- Recipe Search Bar (`recipe_search_bar.dart`) - Search input widget accepting current search query (String)
  and onChange callback. Used by RecipeGrid for local search state. Updates parent in real-time as user types.
  Search filtering is performed by RecipeListService.getFilteredRecipes() with fuzzy matching, ranking results by
  match score with best matches at top.
- Recipe List FAB (`recipe_list_fab.dart`) - Speed Dial FAB widget for importing and creating recipes
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
  images).
  Automatically prefills collection dropdown when user has an active collection filter.
- Edit Recipe Screen (`edit_recipe_screen.dart`) - Form-based screen for editing existing recipes using
  RecipeFormWidget.
- Recipe Form Widget (`recipe_form_widget.dart`) - Reusable form widget for recipe creation and editing with ingredient
  and instruction inputs, serving size input (using ServingSizeInput widget), validation, collection dropdown menu for
  optional collection assignment, image management via RecipeImageManager, and save functionality. Accepts
  InitialRecipeFormData parameter containing recipeDetail, sourceUrl, and pendingImages for prefilling form. Also
  accepts optional initialCollection parameter to prefill collection dropdown. Dropdown shows loading/error states and
  all available collections with "None" option to create recipes without collection. Prefill priority:
  initialFormData's recipeDetail collection > initialCollection > null.
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
- Recipe Picker Screen (`recipe_picker_screen.dart`) - Full-screen screen for selecting recipes using RecipeGrid
  component with search and filter capabilities. Uses Scaffold with AppBar and standard navigation (Navigator.push/pop).
  Returns selected Recipe via Navigator.pop when user taps an item. Route: /recipes/picker
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
  items. Smart text parsing (supports "2 kg apples", "500g flour", "bread" formats), automatic quantity/unit extraction
  using regex, TextField-based editing with focus management. Optional drag handle for reordering (using
  ReorderableDragStartListener), positioned on the left before the checkbox. Optional `subtitle` parameter shows
  secondary text (e.g. source recipe name) below the item text.
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
  Edit/Share/Delete actions (Delete only visible to owners, requires confirmation dialog). Share action opens
  MealPlanSharingDialog with email input, shared users list, and unshare functionality. Includes "Create New Plan"
  button with integrated form dialog, pull-to-refresh functionality, and comprehensive error handling with user-friendly
  messages including special handling for plan limit exceeded (409 Conflict).
- Plan Form Dialog (`plan_form_dialog.dart`) - Unified modal dialog for creating and editing meal plans with name input
  field (autofocus, validation) and color picker.
- Plan Color Picker (`plan_color_picker.dart`) - Reusable grid-based color picker widget with 12 predefined
  Material Design 3 colors. Selected color displays checkmark icon and thicker border.
- Plan List Tile (`plan_list_tile.dart`) - Card widget for individual plans with color indicator (CircleAvatar), plan
  name, visibility checkbox, and PopupMenuButton with role-based menu items (Edit, Share for all; Delete for owners
  only).
- Meal Plan Sharing Dialog (`meal_plan_sharing_dialog.dart`) - Modal dialog for sharing meal plans with other users,
  featuring email input with validation, shared users list with UserRole display, and unshare functionality with
  Material Design 3 styling. Prevents users from unsharing themselves by hiding the unshare button for the current user.
  Uses MealPlanSharingService for sharing operations, which automatically refreshes the meal plans list after successful
  share/unshare actions.
- Meal Entry Form Dialog (`meal_entry_form_dialog.dart`) - Modal dialog for creating and editing meal entries with
  plan dropdown (OWNER/EDITOR only), date picker, recipe/note mode toggle (segmented button with icons), recipe
  selection button (navigates to RecipePickerScreen), serving size input using ServingSizeInput widget (for recipes),
  and note text input. Form content is scrollable. Supports both create mode (with defaultDate from FAB) and edit mode
  (with pre-filled existingEntry data). Supports preselected recipe mode (when preselectedRecipe parameter is provided):
  hides mode toggle and recipe selection UI, pre-fills serving size from recipe's default, only shows plan dropdown,
  date picker, and serving size input. Validation ensures plan selection, recipe selection with positive serving size
  for recipe mode, or non-empty text for note mode.
- Week Strip (`week_strip.dart`) - Week navigation header widget showing current week range with previous/next week
  navigation buttons and tappable week label to jump to today. Date range formatted according to user locale.
- Day Section (`day_section.dart`) - Day container widget showing date header (highlighted for today) and list of meal
  entries, or "No meals planned" empty state. Day header formatted according to user locale.
- Meal Entry Calendar Card (`meal_entry_calendar_card.dart`) - Card widget for individual meal entries with background
  color from plan color, recipe name or placeholder text, serving size, and placeholder overflow menu for edit/delete
  actions. Text color adapts to background luminance for readability.
- Shopping List Generation Screen (`shopping_list_generation_screen.dart`) - Multi-step wizard screen for generating
  shopping lists from meal plan entries. Uses a PageView with 3 steps: Select Plans, Select Dates, Review Items.
  Features an animated step indicator bar at the top. Navigation between steps is controlled programmatically (swipe
  disabled). Back button shown in AppBar from step 2 onwards. Lazy singleton services are reset on dispose.
- Shopping List Generation Select Plan Step (`shopping_list_generation_select_plan_step.dart`) - First step widget
  displaying a list of meal plans with CheckboxListTile items (color indicator + name). "Next" button enabled only when
  at least one plan is selected.
- Shopping List Generation Select Dates Step (`shopping_list_generation_select_dates_step.dart`) - Second step widget
  with a scrollable MonthCalendarWidget for date selection. Supports month navigation (previous/next). Loads calendar
  data for the displayed month from ShoppingListGenerationCalendarService. "Generate Shopping List" button enabled only
  when at least one date is selected. Shows selected date count.
- Shopping List Generation Review Step (`shopping_list_generation_review_step.dart`) - Third step widget showing
  generated items via ShoppingListReviewWidget. Displays a collapsible warnings banner (errorContainer styled) when
  some meals were skipped due to inaccessible recipes, listing the inaccessible recipe names. Shows LoadingWidget
  during generation and ApiErrorWidget on failure with retry support.
- Shopping List Review Widget (`shopping_list_review_widget.dart`) - Widget for reviewing and selecting generated
  shopping list items before adding them to a list. Items are displayed using ShoppingListItemWidget with drag-and-drop
  reordering (ReorderableListView with auto-scroll), inline editing, and checkbox selection. Unchecked items appear
  with strikethrough. Source recipe name is shown as a subtitle below each item text. "Select All / Deselect All"
  toggle in the header. Only checked items are submitted when tapping "Add to Shopping List". Items are a mutable
  local copy — all modifications are client-side only.
- Month Calendar Widget (`month_calendar_widget.dart`) - Reusable month grid calendar widget with previous/next month
  navigation, locale-aware weekday labels (respects first day of week), and tappable day cells. Each day cell shows a
  dot indicator when the date has meal plan entries. Selected dates are highlighted with a filled circle using
  primary color. Accepts calendarData (MealPlanCalendarData) for entry indicators.

### Extraction feature

- URL Extraction Screen (`url_extraction_screen.dart`) - WebView-based screen for extracting recipes from web pages with
  smart input field that automatically detects URLs vs search queries. Supports domain patterns (example.com,
  sub.example.co.uk, localhost:3000) and full URLs (https://example.com). Non-URL inputs trigger Google search with
  encoded query parameters. Captures the current URL from WebView and navigates to create screen with
  InitialRecipeFormData containing extracted recipe detail and source URL. Back button uses WebView history navigation
  when possible, only popping the route when there is no WebView history to go back to.
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
- **Serving Size Input** (`serving_size_input.dart`) - Reusable spinner-style control for serving size input with
  increment/decrement buttons. Minimum value is 1 (decrement disabled at 1). Uses IconButton.outlined with Material
  icons, displays value using headlineMedium text style. Used in RecipeFormWidget and MealEntryFormDialog.

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
  - Tab 1: Recipes (default) - Displays RecipeGrid widget
  - Tab 2: Planning - Displays MealPlanCalendarScreen widget with right-side drawer for plan management
  - Tab 3: Shopping - Displays ShoppingListList widget
- `/recipes/url-extraction` - URL extraction screen (nested route)
- `/recipes/image-extraction` - Image extraction screen (nested route)
- `/recipes/create` - Recipe creation screen (nested route)
- `/recipes/:id` - Recipe detail screen with dynamic ID parameter (nested route)
- `/recipes/:id/edit` - Recipe edit screen with dynamic ID parameter (nested route)
- `/recipes/:id/to-shopping-list` - Add ingredients to shopping list screen (nested route)
- `/recipes/picker` - Recipe picker screen for selecting a recipe (AppRoute.recipePicker, nested route)
- `/recipes-collections` - Recipe collections list screen (AppRoute.recipesCollections, shown when feature flag enabled)
- `/shopping-list-generation` - Shopping list generation wizard screen (AppRoute.shoppingListGeneration)
- `/shopping-lists/:id` - Shopping list detail screen with dynamic ID parameter (AppRoute.shoppingListDetail)

### Bottom Navigation Bar

The app features an embedded bottom navigation bar (part of MainScreen) with tabs:

- **Recipes** (restaurant_menu icon) - Access recipe management features
- **Planning** (calendar_today icon) - Access meal planning agenda view
- **Shopping** (shopping_cart icon) - Access shopping list features

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
4. **Add to Meal Plan FAB Tap** (on Recipe Detail Screen) → MealEntryFormDialog opens with
   recipe preselected, serving size pre-filled → Select plan and date → Entry created → Success message → Dialog closes
5. **Edit Menu Item Tap** (on Recipe Detail Screen) → Edit Recipe Screen (`/recipes/:id/edit` with recipe ID parameter)
6. **Share Button Tap** (on Recipe Detail Screen) → List of shared users → Share recipe → Back to Recipe Detail Screen
7. **Add to Shopping List Tap** (on Recipe Detail Screen) → Recipe To Shopping List Screen (
   `/recipes/:id/to-shopping-list`) → Select ingredients → Choose shopping list → Items queued for sync → Back to Recipe
   Detail Screen
8. **Delete Button Tap** (on Recipe Detail Screen) → Confirmation dialog → Recipe deletion → Back to Main Screen
9. **Successful URL Extraction** → Create Recipe Screen with pre-filled extracted data, source URL, and collection (if
   filter active) via InitialRecipeFormData → Recipe creation → Back to Main Screen
10. **Successful Image Extraction** → Create Recipe Screen with pre-filled extracted data, pending image, and
    collection (
    if filter active) via InitialRecipeFormData → Recipe creation → Back to Main Screen
11. **Successful Manual Creation** → Back to Main Screen (with recipe added to selected collection if filter was active)
12. **Successful Edit** → Back to Recipe Detail Screen (with updated data)

#### Recipe Collections Management Flow

1. **AppBar Menu → Recipes collections** (when feature flag enabled) → Recipes Collection List Screen (
   `/recipes-collections`)
2. **FAB Tap** (on Collections screen) → Create dialog with name input → Collection created → List refreshed
3. **Pull to Refresh** (on Collections screen) → Collections reloaded from API
4. **Collection Item Menu → Rename** → Rename dialog with pre-filled name → Collection renamed → List refreshed
5. **Collection Item Menu → Share** → Sharing dialog → Share/unshare collection with users → List refreshed
6. **Collection Item Menu → Delete** → Confirmation dialog → Collection deleted → List refreshed
7. **Back Button** (on Collections screen) → Back to Main Screen

#### Meal Planning Management Flow

1. **Bottom Navigation → Planning Tab** → MainScreen switches to Planning tab view with calendar
2. **Manage Plans Button Tap** (calendar icon in AppBar) → Opens MealPlanDrawer overlay from right side
3. **Pull to Refresh** (in drawer) → Meal plans reloaded from API
4. **Visibility Checkbox Tap** (in drawer) → Toggle plan visibility → Calendar refreshes automatically to show/hide plan
   entries
5. **Plan Menu → Delete** (owners only) → Confirmation dialog "Are you sure you want to delete...?" → Tap "Delete" →
   Plan
   deleted (with all entries) → SnackBar "Plan deleted successfully" → Drawer list refreshes → Calendar refreshes to
   remove deleted plan entries
6. **Plan Menu → Edit** → PlanFormDialog opens with pre-filled name and color → Edit fields → Tap "Save" → Plan updated
   → SnackBar "Plan updated successfully" → Drawer list refreshes
7. **Plan Menu → Share** → MealPlanSharingDialog opens → Enter email and tap "Share" → Email validated → Shared user
   added to list → SnackBar "Meal plan shared successfully!" → Can unshare users (except self) via remove icon →
   SnackBar "Meal plan unshared successfully!" → Close dialog → Plan list refreshes
8. **Create New Plan Button** (bottom of drawer) → PlanFormDialog opens → Enter name and select color → Tap "Create" →
   Plan created → SnackBar "Plan created successfully" → New plan appears in drawer list
9. **Create/Edit Validation Errors** → Name required, color required → Error messages displayed inline
10. **Create/Edit/Delete API Errors** → Network failures, plan limit exceeded (409), permission errors (403 for
    delete), plan not found (404) → User-friendly error messages in SnackBars
11. **Close Drawer** → Swipe left or tap outside drawer → Returns to calendar view
12. **Switch Tabs** → Drawer closes automatically
13. **Add Meal Entry (FAB)** → Tap FAB on calendar screen → MealEntryFormDialog opens with date defaulting to week
    start → Select plan from dropdown → Choose Recipe or Note mode → For Recipe: tap "Select Recipe" button →
    RecipePickerScreen opens with full-screen navigation → Search/filter and tap recipe → Navigate back with selected
    recipe → Enter serving size
    → Tap "Create" → Entry added to calendar → SnackBar "Meal entry added" → Calendar refreshes
14. **Add Note Entry** → Same as step 13 but select "Note" mode → Enter text description → Tap
    "Create" → Note added to calendar
15. **Edit Meal Entry** → Tap three-dot menu on entry card → Select "Edit" → MealEntryFormDialog opens with pre-filled
    data → Modify fields (plan, date, recipe, serving size, or text) → Tap "Save" → Entry updated → SnackBar "Meal
    entry updated" → Calendar refreshes
16. **Delete Meal Entry** → Tap three-dot menu on entry card → Select "Delete" → Confirmation dialog "Are you sure you
    want to delete...?" → Tap "Delete" → Entry deleted → SnackBar "Meal entry deleted" → Calendar refreshes
17. **Navigate to Recipe from Entry** → Tap on recipe entry card (not menu) → If hasRecipeAccess, navigates to Recipe
    Detail Screen → If no access, shows SnackBar "Recipe details not shared"

#### Shopping List Generation Flow

1. **AppBar Menu → Generate shopping list** → Shopping List Generation Screen (`/shopping-list-generation`)
2. **Step 1 - Select Plans** → Displays all available meal plans with checkboxes → Select one or more → Tap "Next"
3. **Step 2 - Select Dates** → Month calendar loaded for selected plans → Navigate months with previous/next →
   Tap dates to toggle selection (dots show days with meal entries) → Tap "Generate Shopping List"
4. **Step 3 - Review Items** → Loading indicator during generation → Generated items displayed via
   ShoppingListReviewWidget → Warnings banner shown for skipped inaccessible recipes → Select shopping list and
   add items → Navigate back on success
5. **Back Button** (steps 2 and 3) → Returns to previous step

#### Shopping List Management Flow

1. **Bottom Navigation → Shopping Tab** → MainScreen switches to Shopping tab view
2. **FAB Tap** (on Shopping tab) → Create dialog with name input → Shopping list created → List refreshed
3. **Pull to Refresh** (on Shopping tab) → Shopping lists reloaded from API
4. **Shopping List Tap** → Shopping List Detail Screen (`/shopping-lists/:id` with shopping list ID parameter)
5. **Back Button** (on Shopping List Detail Screen) → Back to Shopping tab on Main Screen

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