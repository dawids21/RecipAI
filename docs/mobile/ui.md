# UI Overview - RecipAI

## Screens

### Core feature

- Main Screen (`main_screen.dart`) - Main application screen with embedded bottom navigation, managing both recipe and
  shopping list tabs. Displays RecipeList or ShoppingListList widgets based on selected tab, with corresponding FABs (
  RecipeListFab or ShoppingListListFab)

### Recipe feature

- Recipe List Widget (`recipe_list.dart`) - Reusable body widget displaying all available recipes with pull-to-refresh
- Recipe List FAB (`recipe_list_fab.dart`) - Speed Dial FAB widget for importing and creating recipes
- Detail Screen (`recipe_detail_screen.dart`) - Displays full recipe details including ingredients and instructions
  with Edit FAB, Share button, and role-based conditional Delete button for recipe management
- Create Recipe Screen (`create_recipe_screen.dart`) - Form-based screen for manually creating recipes using
  RecipeFormWidget
- Edit Recipe Screen (`edit_recipe_screen.dart`) - Form-based screen for editing existing recipes using RecipeFormWidget
- Recipe Form Widget (`recipe_form_widget.dart`) - Reusable form widget for recipe creation and editing with ingredient
  and instruction inputs, validation, and save functionality
- Recipe List Item (`recipe_list_item.dart`) - Reusable widget for displaying individual recipes in a list
- Ingredient Input Widget (`ingredient_input_widget.dart`) - Reusable widget for entering ingredient name and quantity
  with validation
- Ingredient bullet (`ingredient_bullet.dart`) - Small bullet point icon for ingredient lists (8px size)
- Step number badge (`step_number_badge.dart`) - Circular badge for recipe step numbers (24px container, white text)
- Recipe Sharing Dialog (`recipe_sharing_dialog.dart`) - Modal dialog for sharing recipes with other users, featuring
  email input with validation, shared users list with UserRole enum display, and unshare functionality with Material
  Design 3 styling. Prevents users from unsharing themselves by hiding the unshare button for the current user

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
- Shopping List Detail Screen (`shopping_list_detail_screen.dart`) - Displays individual shopping list with all items,
  showing item name, quantity, unit, and checked status. Features PopupMenuButton with role-based actions: "Rename List"
  available to all users, "Delete List" only visible to OWNER role users
- Shopping List Rename Dialog (`shopping_list_rename_dialog.dart`) - Stateful dialog widget for renaming shopping lists
  with TextField input, proper TextEditingController lifecycle management, and validation to prevent empty names

### Extraction feature

- URL Extraction Screen (`url_extraction_screen.dart`) - WebView-based screen for extracting recipes from web pages with
  URL input field and loading states
- Image Extraction Screen (`image_extraction_screen.dart`) - Screen for extracting recipes from images using camera or
  gallery selection with image preview and upload functionality
- Extraction Dialog (`extraction_dialog.dart`) - Modal dialog for choosing between URL and image extraction methods with
  Material Design buttons
- Web Recipe Extractor (`web_recipe_extractor.dart`) - Utility class for extracting HTML content from WebView

## Shared Widgets

- **Loading Widget** (`loading_widget.dart`) - Reusable loading indicator for async operations
- **API Error Widget** (`api_error_widget.dart`) - Reusable error display with retry functionality for API failures
- **Error Icon** (`error_icon.dart`) - Standardized error icon (64px, theme-based color)

## Navigation

### Route Structure

The app uses a simple GoRoute structure with embedded bottom navigation in MainScreen:

#### Authentication Routes
- `/login` - Authentication screen with Google Sign-In (only accessible when unauthenticated)

#### Main App Routes

- `/` - Main screen with embedded bottom navigation (AppRoute.main)
    - Tab 1: Recipes (default) - Displays RecipeList widget
    - Tab 2: Shopping - Displays ShoppingListList widget
    - `/recipes/url-extraction` - URL extraction screen (nested route)
    - `/recipes/image-extraction` - Image extraction screen (nested route)
    - `/recipes/create` - Recipe creation screen (nested route)
    - `/recipes/:id` - Recipe detail screen with dynamic ID parameter (nested route)
    - `/recipes/:id/edit` - Recipe edit screen with dynamic ID parameter (nested route)
  - `/shopping-lists/:id` - Shopping list detail screen with dynamic ID parameter (AppRoute.shoppingListDetail)

### Bottom Navigation Bar

The app features an embedded bottom navigation bar (part of MainScreen) with two tabs:

- **Recipes** (restaurant_menu icon) - Access recipe management features
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
3. **Speed Dial FAB → Create Tap** (on Recipes tab) → Create Recipe Screen (`/recipes/create`)
4. **Edit FAB Tap** (on Recipe Detail Screen) → Edit Recipe Screen (`/recipes/:id/edit` with recipe ID parameter)
5. **Share Button Tap** (on Recipe Detail Screen) → List of shared users → Share recipe → Back to Recipe Detail Screen
6. **Delete Button Tap** (on Recipe Detail Screen) → Confirmation dialog → Recipe deletion → Back to Main Screen
7. **Successful URL/Image Extraction** → Create Recipe Screen with pre-filled extracted data → Recipe creation → Back to
   Main Screen
8. **Successful Manual Creation** → Back to Main Screen (with recipe added)
9. **Successful Edit** → Back to Recipe Detail Screen (with updated data)

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