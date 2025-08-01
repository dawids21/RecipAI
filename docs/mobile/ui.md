# UI Overview - RecipAI

## Screens

### Recipe module

- List Screen (`recipe_list_screen.dart`) - Main screen displaying all available recipes
- Detail Screen (`recipe_detail_screen.dart`) - Displays full recipe details including ingredients and instructions
- Recipe List Item (`recipe_list_item.dart`) - Reusable widget for displaying individual recipes in a list
- Loading Widget (`loading_widget.dart`) - Reusable loading indicator for async operations
- Ingredient bullet (`ingredient_bullet.dart`) - Small bullet point icon for ingredient lists (8px size)
- Step number badge (`step_number_badge.dart`) - Circular badge for recipe step numbers (24px container, white text)

## Data Models

### Recipe module

- Recipe (`recipe.dart`) - Basic recipe data model with id and name
- Recipe Detail (`recipe_detail.dart`) - Complex nested structure for detailed recipe information

## Navigation Flow

1. **App Launch** → Recipe List Screen
2. **Recipe Tap** → Recipe Detail Screen (with recipe ID parameter)

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