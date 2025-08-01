# UI Overview - RecipAI

## Screens

### Recipe module

- List Screen (`recipe_list_screen.dart`) - Main screen displaying all available recipes with FAB for importing
- Detail Screen (`recipe_detail_screen.dart`) - Displays full recipe details including ingredients and instructions
- Recipe List Item (`recipe_list_item.dart`) - Reusable widget for displaying individual recipes in a list
- Ingredient bullet (`ingredient_bullet.dart`) - Small bullet point icon for ingredient lists (8px size)
- Step number badge (`step_number_badge.dart`) - Circular badge for recipe step numbers (24px container, white text)

### Import module

- Import Screen (`import_screen.dart`) - WebView-based screen for importing recipes from web pages
- Web Recipe Extractor (`web_recipe_extractor.dart`) - Utility class for extracting HTML content from WebView

## Shared Widgets

- **Loading Widget** (`loading_widget.dart`) - Reusable loading indicator for async operations
- **API Error Widget** (`api_error_widget.dart`) - Reusable error display with retry functionality for API failures
- **Error Icon** (`error_icon.dart`) - Standardized error icon (64px, theme-based color)

## Navigation Flow

1. **App Launch** → Recipe List Screen
2. **Recipe Tap** → Recipe Detail Screen (with recipe ID parameter)
3. **Import FAB Tap** → Import Screen
4. **Successful Import** → Back to Recipe List Screen (with new recipe added)

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

## Data Models

### Recipe module

- Recipe (`recipe.dart`) - Basic recipe data model with id and name
- Recipe Detail (`recipe_detail.dart`) - Complex nested structure for detailed recipe information