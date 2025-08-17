# SIP: UI Update & Delete Recipe

## Goal

- Add UI functionality to update/edit existing recipes through a dedicated edit screen
- Add UI functionality to delete recipes from the recipe detail screen
- Reuse existing form logic by extracting it into a reusable `RecipeFormWidget`
- Implement navigation flow: Recipe Detail → Edit Screen → Recipe Detail (with updated data)
- Add route `/recipes/:id/edit` for recipe editing
- Add Edit FAB button on recipe detail screen and red Delete icon button in app bar
- Ensure proper error handling, loading states, and user feedback throughout the flow

## Context

### Documentation and References

- **Mobile Architecture**: `docs/mobile/mobile.md` - Current app structure and patterns
- **API Documentation**: `docs/backend/api.md` - PUT /recipes/{uuid} and DELETE /recipes/{uuid} endpoints exist
- **UI Documentation**: `docs/mobile/ui.md` - Current screens, widgets, and navigation patterns
- **PRD Requirements**: `docs/prd.md` - User story US-003 for update/delete functionality
- **Flutter go_router docs**: https://pub.dev/packages/go_router - For nested route patterns
- **Flutter Form Best Practices**: https://docs.flutter.dev/cookbook/forms - For form validation patterns

### Current Codebase Tree

```
mobile/lib/
├── core/
│   ├── api_service.dart           # Needs updateRecipe & deleteRecipe methods
│   ├── routes.dart                # Needs new edit route
│   └── theme.dart                 # Spacing constants
├── features/recipe/
│   ├── create_recipe_screen.dart  # Contains form logic to extract
│   ├── recipe_detail_screen.dart  # Needs Edit FAB & Delete button
│   ├── ingredient_input_widget.dart # Supports initial values
│   └── recipe_detail.dart         # Data models
└── shared/
    ├── loading_widget.dart        # For loading states
    └── error_message_widget.dart  # For error handling
```

### Desired Codebase Tree

```
mobile/lib/
├── core/
│   ├── api_service.dart           # ✓ Added updateRecipe & deleteRecipe methods
│   ├── routes.dart                # ✓ Added recipeEdit route
│   └── theme.dart
├── features/recipe/
│   ├── create_recipe_screen.dart  # ✓ Refactored to use RecipeFormWidget
│   ├── edit_recipe_screen.dart    # ✓ NEW - Edit screen using RecipeFormWidget
│   ├── recipe_form_widget.dart    # ✓ NEW - Extracted reusable form widget
│   ├── recipe_detail_screen.dart  # ✓ Added Edit FAB & Delete button
│   ├── ingredient_input_widget.dart
│   └── recipe_detail.dart
└── shared/
    ├── loading_widget.dart
    └── error_message_widget.dart
```

### Known Gotchas of Our Codebase and Library Quirks

- **Route Order Rule**: When defining nested routes in go_router, always define most specific routes first, followed by
  more generic routes (e.g., `/recipes/:id/edit` before `/recipes/:id`, and `/recipes/create` before `/recipes/:id`)
- **API Service Pattern**: Use static methods matching existing `ApiService.createRecipe()` pattern
- **Form Validation**: Current form uses GlobalKey<FormState> and validates on submit - maintain this pattern
- **Ingredient Parsing**: Current form has complex ingredient parsing logic in `parseIngredientText()` - preserve this
- **Navigation Pattern**: Use `context.pop(result)` to return data and `context.go()` for navigation
- **Theme Access**: Always use `final theme = Theme.of(context)` pattern at start of build methods
- **Error Handling**: Use try-catch with user-friendly error messages and setState for error display

## Implementation Plan

### Tasks

```
Task 1: Add API methods for update and delete operations
  Action: MODIFY
  File: mobile/lib/core/api_service.dart
  Changes:
    - [ ] Add updateRecipe(String id, RecipeDetail recipe) static method
    - [ ] Add deleteRecipe(String id) static method
    - [ ] Follow existing createRecipe method pattern for HTTP calls
    - [ ] Handle 200 OK response for updates, 204 No Content for deletes
    - [ ] Handle 404 Not Found and 400 Bad Request errors
    - [ ] Use same error handling pattern as existing methods

Task 2: Extract form logic to reusable widget
  Action: CREATE
  File: mobile/lib/features/recipe/recipe_form_widget.dart
  Changes:
    - [ ] Extract form logic from CreateRecipeScreen to RecipeFormWidget  
    - [ ] Support optional initialRecipe parameter for edit mode
    - [ ] Maintain existing form validation and ingredient parsing logic
    - [ ] Return Future<RecipeDetail?> on save with created/updated recipe
    - [ ] Support both "Create Recipe" and "Update Recipe" button text
    - [ ] Pre-populate form fields when initialRecipe is provided
    - [ ] Parse existing ingredients back to name/quantity format for editing

Task 3: Add edit route to router configuration
  Action: MODIFY  
  File: mobile/lib/core/routes.dart
  Changes:
    - [ ] Add recipeEdit('edit') to AppRoute enum
    - [ ] Add nested GoRoute under recipeDetail route: path: 'edit'
    - [ ] Extract recipeId from parent route parameters using state.pathParameters['id']
    - [ ] Pass recipeId to EditRecipeScreen constructor
    - [ ] Ensure route order follows pattern: create, edit, then :id

Task 4: Create edit recipe screen
  Action: CREATE
  File: mobile/lib/features/recipe/edit_recipe_screen.dart
  Changes:
    - [ ] Create StatefulWidget accepting required String recipeId parameter
    - [ ] Fetch recipe data using ApiService.fetchRecipeDetail in initState
    - [ ] Show LoadingWidget while fetching data
    - [ ] Show ApiErrorWidget if fetch fails with retry functionality
    - [ ] Use RecipeFormWidget with initialRecipe when data loaded
    - [ ] Handle form submission by calling ApiService.updateRecipe
    - [ ] Navigate back to recipe detail with context.pop(updatedRecipe)
    - [ ] Follow existing screen patterns for AppBar and theme usage

Task 5: Update create recipe screen to use form widget
  Action: MODIFY
  File: mobile/lib/features/recipe/create_recipe_screen.dart
  Changes:
    - [ ] Replace inline form with RecipeFormWidget
    - [ ] Remove form logic (moved to RecipeFormWidget)
    - [ ] Keep same AppBar title "Create Recipe"
    - [ ] Maintain existing navigation pattern with context.pop(createdRecipe)
    - [ ] Preserve existing error handling and loading states
    - [ ] Keep same screen padding and layout structure

Task 6: Add Edit FAB to recipe detail screen
  Action: MODIFY
  File: mobile/lib/features/recipe/recipe_detail_screen.dart
  Changes:
    - [ ] Add floatingActionButton to Scaffold
    - [ ] Use FloatingActionButton with edit icon (Icons.edit)
    - [ ] Navigate to edit screen: context.go('/recipes/${widget.recipeId}/edit')
    - [ ] Handle navigation result to refresh recipe data if updated
    - [ ] Match existing FAB patterns in the app (Speed Dial style if applicable)
    - [ ] Position FAB appropriately without interfering with scroll

Task 7: Add Delete button to recipe detail screen
  Action: MODIFY
  File: mobile/lib/features/recipe/recipe_detail_screen.dart
  Changes:
    - [ ] Add IconButton to AppBar actions with Icons.delete
    - [ ] Set icon color to theme.colorScheme.error (red)
    - [ ] Show confirmation dialog before deletion using showDialog
    - [ ] Call ApiService.deleteRecipe if user confirms
    - [ ] Show loading state during deletion
    - [ ] Navigate back to recipe list with context.go('/recipes') after successful deletion
    - [ ] Handle deletion errors with user-friendly error messages
    - [ ] Use SnackBar for success/error feedback
```

### Per Task Pseudocode

```dart
// Task 1: API Service Methods
static Future<RecipeDetail> updateRecipe

(String id, RecipeDetail recipe) async
{

final response = await
_client.put
(
Uri.parse('$_baseUrl/recipes/$id'),
headers: {'Content-Type': 'application/json'},
body: json.encode(recipe.toJson()),
);

if (response.statusCode == 200) {
return RecipeDetail.fromJson(jsonDecode(response.body));
} else {
throw Exception('Failed to update recipe: ${response.statusCode}');
}
}

static Future<void> deleteRecipe(String id) async {
final response = await _client.delete(
Uri.parse('$_baseUrl/recipes/$id'),
headers: {'Content-Type': 'application/json'},
);

if (response.statusCode != 204) {
throw Exception('Failed to delete recipe: ${response.statusCode}');
}
}

// Task 2: Recipe Form Widget Structure  
class RecipeFormWidget extends StatefulWidget {
final RecipeDetail? initialRecipe;
final String buttonText;
final Future<RecipeDetail> Function(RecipeDetail recipe) onSave;

// Pre-populate controllers with initial data if provided
// Handle save operation through callback
// Return saved recipe through callback result
}

// Task 4: Edit Screen Structure
class EditRecipeScreen extends StatefulWidget {
final String recipeId;

// Fetch recipe data in initState
// Pass fetched recipe to RecipeFormWidget
// Handle update through ApiService.updateRecipe
// Navigate back with updated recipe
}

// Task 6 & 7: Recipe Detail Screen Updates
class _RecipeDetailScreenState {
Future<void> _navigateToEdit() async {
final result = await context.push('/recipes/${widget.recipeId}/edit');
if (result != null) {
// Refresh recipe data
setState(() {
futureRecipeDetail = ApiService.fetchRecipeDetail(widget.recipeId);
});
}
}

Future<void> _showDeleteConfirmation() async {
final shouldDelete = await showDialog<bool>(
context: context,
builder: (context) => AlertDialog(
title: Text('Delete Recipe'),
content: Text('Are you sure you want to delete this recipe?'),
actions: [
TextButton(onPressed: () => Navigator.pop(context, false), child: Text('Cancel')),
TextButton(onPressed: () => Navigator.pop(context, true), child: Text('Delete')),
],
),
);

if (shouldDelete == true) {
await _deleteRecipe();
}
}
}
```

## Validation

### Syntax and Style

```bash
# Run these FIRST - fix any errors before proceeding
cd mobile
flutter analyze

# Expected: No issues found! If errors, READ the error and fix.
```

### Unit Tests

```bash
# Run and iterate until passing:
cd mobile  
flutter test

# If failing: Read error, understand root cause, fix code, re-run
# Note: May need to add tests for new API methods and form widget
```

### Integration Tests

```bash
# Manual testing approach since no integration tests currently exist:
# 1. Test recipe creation still works with new form widget
# 2. Test recipe editing flow: Detail → Edit → Detail with updated data
# 3. Test recipe deletion with confirmation dialog
# 4. Test error handling for network failures
# 5. Test navigation flow and back button behavior
# 6. Test form validation in both create and edit modes
```

## Integration Points

- **API Integration**: Uses existing PUT /recipes/{uuid} and DELETE /recipes/{uuid} endpoints
- **Navigation Integration**: Integrates with existing go_router navigation system
- **Form Integration**: Reuses existing form validation and ingredient input patterns
- **Data Integration**: Uses existing RecipeDetail data models and JSON serialization
- **UI Integration**: Follows existing AppBar, FAB, and theming patterns

## Documentation

- **Mobile UI Documentation**: Update `docs/mobile/ui.md` to include new EditRecipeScreen and RecipeFormWidget
- **Mobile CLAUDE.md**: No updates needed - follows existing architectural patterns
- **API Documentation**: No updates needed - endpoints already documented

## Final Validation Checklist

- [ ] Correct syntax (flutter analyze passes)
- [ ] Correct style (follows existing code patterns)
- [ ] All tests pass (flutter test passes)
- [ ] Manual test successful (all user flows work)
- [ ] Error cases handled gracefully (network errors, validation errors)
- [ ] Logs are informative but not verbose (appropriate error messages)
- [ ] Documentation updated if needed (UI docs updated)
- [ ] Edit flow: Recipe Detail → Edit → Recipe Detail works
- [ ] Delete flow with confirmation works
- [ ] Form reuse works for both create and edit modes
- [ ] Navigation maintains app state correctly
- [ ] Loading states and error handling work properly

## Confidence Score: 9/10

This SIP provides comprehensive context and implementation details for one-pass success. The existing codebase patterns
are well-established, the API endpoints exist, and the implementation follows proven Flutter patterns. The only minor
uncertainty is around specific form state management during the extraction process, but the existing code provides clear
patterns to follow.