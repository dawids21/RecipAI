# UI Overview - RecipAI

## Screens

### Recipe feature

- List Screen (`recipe_list_screen.dart`) - Main screen displaying all available recipes with Speed Dial FAB for
  importing and creating recipes
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
  Design 3 styling

### Auth feature

- Login Screen (`login_screen.dart`) - Welcome screen with Google Sign-In button, app branding (RecipAI logo and
  title), loading states during authentication, and error handling for sign-in failures
- Auth Service (`auth_service.dart`) - Abstract service interface defining authentication contracts with
  `isAuthenticated`, `idToken`, `signIn()`, and `signOut()` methods
- Firebase Auth Service (`firebase_auth_service.dart`) - Firebase implementation with Google Sign-In integration,
  user state management, and automatic token refresh

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

- `/` - Main recipe list screen with logout functionality (requires authentication)
- `/login` - Authentication screen with Google Sign-In (only accessible when unauthenticated)
- `/extraction` - Recipe extraction screen with WebView (nested under recipes, requires authentication)
- `/create` - Recipe creation screen (supports both manual creation and creation from extracted data, requires
  authentication)
- `/:id` - Recipe detail screen with dynamic ID parameter (requires authentication)
- `/:id/edit` - Recipe edit screen with dynamic ID parameter (requires authentication)

### Authentication & Route Protection

All routes except `/login` require user authentication. The app automatically redirects unauthenticated users to the
login screen and authenticated users away from the login screen to the recipes list.

### Flow

#### Authentication Flow

1. **App Launch** → Authentication check:
    - **If unauthenticated** → Login Screen (`/login`)
    - **If authenticated** → Recipe List Screen (`/`)
2. **Login Screen → Google Sign-In Tap** → Authentication process → Recipe List Screen (`/`)
3. **Recipe List Screen → Logout Tap** → Confirmation dialog → Sign out → Login Screen (`/login`)

#### Recipe Management Flow

1. **Recipe Tap** → Recipe Detail Screen (`/:id` with recipe ID parameter)
2. **Speed Dial → Extract Tap** → Extraction Screen (`/extraction`)
3. **Speed Dial → Create Tap** → Create Recipe Screen (`/create`)
4. **Edit FAB Tap** (on Recipe Detail Screen) → Edit Recipe Screen (`/:id/edit` with recipe ID parameter)
5. **Share Button Tap** (on Recipe Detail Screen) -> List of shared users -> Share recipe -> Back to Recipe Detail
   Screen
5. **Delete Button Tap** (on Recipe Detail Screen) → Confirmation dialog → Recipe deletion → Back to Recipe List Screen
6. **Successful URL/Image Extraction** → Create Recipe Screen with pre-filled extracted data → Recipe creation → Back to
   Recipe
   List Screen
7. **Successful Manual Creation** → Back to Recipe List Screen (with recipe added)
8. **Successful Edit** → Back to Recipe Detail Screen (with updated data)

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