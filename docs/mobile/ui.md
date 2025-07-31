# UI Overview - RecipAI

## Screens

### Recipe module

- List Screen (`recipe_list_screen.dart`) - Main screen displaying all available recipes
- Detail Screen (`recipe_detail_screen.dart`) - Displays full recipe details including ingredients and instructions
- Recipe List Item (`recipe_list_item.dart`) - Reusable widget for displaying individual recipes in a list
- Loading Widget (`loading_widget.dart`) - Reusable loading indicator for async operations

## Data Models

### Recipe module

- Recipe (`recipe.dart`) - Basic recipe data model with id and name
- Recipe Detail (`recipe_detail.dart`) - Complex nested structure for detailed recipe information

## Navigation Flow

1. **App Launch** → Recipe List Screen
2. **Recipe Tap** → Recipe Detail Screen (with recipe ID parameter)