# UI Overview - RecipAI

## Screens

### Recipe module

- List Screen (`recipe_list_screen.dart`) - Main screen displaying all available recipes with Speed Dial FAB for
  importing and creating recipes
- Detail Screen (`recipe_detail_screen.dart`) - Displays full recipe details including ingredients and instructions
  with Edit FAB and Delete button for recipe management
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

### Extraction module

- Extraction Screen (`extraction_screen.dart`) - WebView-based screen for extracting recipes from web pages
- Web Recipe Extractor (`web_recipe_extractor.dart`) - Utility class for extracting HTML content from WebView

## Shared Widgets

- **Loading Widget** (`loading_widget.dart`) - Reusable loading indicator for async operations
- **API Error Widget** (`api_error_widget.dart`) - Reusable error display with retry functionality for API failures
- **Error Icon** (`error_icon.dart`) - Standardized error icon (64px, theme-based color)

## Navigation

### Route Structure

- `/` - Home route (redirects to `/recipes`)
- `/extraction` - Recipe extraction screen with WebView
- `/recipes` - Main recipe list screen
- `/recipes/create` - Recipe creation screen (supports both manual creation and creation from extracted data)
- `/recipes/:id` - Recipe detail screen with dynamic ID parameter
- `/recipes/:id/edit` - Recipe edit screen with dynamic ID parameter

### Flow

1. **App Launch** → Recipe List Screen (`/recipes`)
2. **Recipe Tap** → Recipe Detail Screen (`/recipes/:id` with recipe ID parameter)
3. **Speed Dial → Extract Tap** → Extraction Screen (`/recipes/extraction`)
4. **Speed Dial → Create Tap** → Create Recipe Screen (`/recipes/create`)
5. **Edit FAB Tap** (on Recipe Detail Screen) → Edit Recipe Screen (`/recipes/:id/edit` with recipe ID parameter)
6. **Delete Button Tap** (on Recipe Detail Screen) → Confirmation dialog → Recipe deletion → Back to Recipe List Screen
7. **Successful Extraction** → Create Recipe Screen with pre-filled extracted data → Recipe creation → Back to Recipe
   List Screen
8. **Successful Manual Creation** → Back to Recipe List Screen (with recipe added)
9. **Successful Edit** → Back to Recipe Detail Screen (with updated data)

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