# SIP: UI Refactor - Use ChangeNotifier for Recipes List

## Goal

- Replace StatefulWidget-based recipe list management with ChangeNotifier pattern for global state management
- Enable recipe list updates from anywhere in the app without manual refresh calls
- Implement lazy loading for recipes list that only fetches data when displayed
- Provide a unified refresh mechanism callable from create, edit, and delete operations
- Success criteria: Recipe list automatically updates after any CRUD operation without manual refresh calls

## Context

### Documentation and References

- [Flutter ChangeNotifier Documentation](https://api.flutter.dev/flutter/foundation/ChangeNotifier-class.html)
- [Flutter InheritedNotifier Documentation](https://api.flutter.dev/flutter/widgets/InheritedNotifier-class.html)
- [Flutter Simple State Management Guide](https://docs.flutter.dev/data-and-backend/state-mgmt/simple)
- [ChangeNotifier Best Practices 2025](https://www.hungrimind.com/articles/flutter-state-management)
- Feature request file: `docs/feature-requests/ui-refactor-use-change-notifier-for-recipes-list.md`
- Current implementation: `mobile/lib/features/recipe/recipe_list_screen.dart`
- API integration: `mobile/lib/core/api_service.dart`

### Current Codebase Tree

```
mobile/lib/
├── core/
│   ├── api_service.dart          # HTTP client for recipe operations
│   ├── app_config.dart           # App configuration
│   ├── routes.dart               # Go router configuration
│   └── theme.dart                # Theme and spacing constants
├── features/
│   └── recipe/
│       ├── create_recipe_screen.dart    # Create recipe form screen
│       ├── edit_recipe_screen.dart      # Edit recipe form screen
│       ├── recipe_detail_screen.dart    # Recipe details with delete function
│       ├── recipe_list_screen.dart      # StatefulWidget with manual refresh
│       ├── recipe_form_widget.dart      # Shared form widget
│       ├── recipe.dart                  # Recipe model (id, name)
│       ├── recipe_detail.dart           # Detailed recipe model
│       └── [other recipe widgets]
├── main.dart                      # App entry point with MaterialApp.router
└── [other directories]
```

### Desired Codebase Tree

```
mobile/lib/
├── core/
│   ├── api_service.dart          # HTTP client for recipe operations
│   ├── app_config.dart           # App configuration
│   ├── routes.dart               # Go router configuration
│   └── theme.dart                # Theme and spacing constants
├── features/
│   └── recipe/
│       ├── recipe_list_model.dart       # NEW: ChangeNotifier for recipe list state
│       ├── create_recipe_screen.dart    # MODIFIED: Call refresh after create
│       ├── edit_recipe_screen.dart      # MODIFIED: Call refresh after edit
│       ├── recipe_detail_screen.dart    # MODIFIED: Call refresh after delete
│       ├── recipe_list_screen.dart      # MODIFIED: Use ChangeNotifier instead of StatefulWidget
│       ├── recipe_form_widget.dart      # Shared form widget (unchanged)
│       ├── recipe.dart                  # Recipe model (unchanged)
│       ├── recipe_detail.dart           # Detailed recipe model (unchanged)
│       └── [other recipe widgets]
├── main.dart                      # MODIFIED: Add InheritedRecipeListModel provider
└── [other directories]
```

### Known Gotchas of Our Codebase and Library Quirks

- **Go Router Navigation**: Uses `context.pushNamed()` and `context.goNamed()` with AppRoute enum
- **Theme Pattern**: Always use `final theme = Theme.of(context);` at beginning of build methods
- **API Service**: Singleton pattern with static methods, returns Future-based results
- **Form Widget Return Pattern**: RecipeFormWidget returns saved recipe via `Navigator.of(context).pop(savedRecipe)`
- **Error Handling**: Uses try-catch with ApiErrorWidget for displaying errors
- **Flutter ChangeNotifier Limitation**: O(N²) for dispatching notifications, optimized for small listener counts
- **InheritedNotifier Performance**: Coalesces multiple notifications between frames for efficiency
- **Future Management**: Current implementation uses `late Future<List<Recipe>>` pattern that needs conversion to
  ChangeNotifier

## Implementation Plan

### Tasks

```
Task 1: Create RecipeListModel ChangeNotifier
  Action: CREATE
  File: mobile/lib/features/recipe/recipe_list_model.dart
  Changes:
    - [ ] Create RecipeListModel class extending ChangeNotifier
    - [ ] Implement lazy loading with late Future<List<Recipe>> _recipes
    - [ ] Add refresh() method that re-fetches and notifies listeners
    - [ ] Follow example pattern from feature request with async loading state
    - [ ] Include error handling for API failures
    - [ ] Add private _initRecipes() method for initial load

Task 2: Create InheritedRecipeListModel Provider
  Action: MODIFY
  File: mobile/lib/features/recipe/recipe_list_model.dart (append)
  Changes:
    - [ ] Create InheritedRecipeListModel extending InheritedNotifier<RecipeListModel>
    - [ ] Implement static of(BuildContext context) method
    - [ ] Follow InheritedNotifier pattern from codebase research
    - [ ] Assert notifier is not null in of() method
    - [ ] Use generic type safety pattern

Task 3: Update Main App to Provide RecipeListModel
  Action: MODIFY
  File: mobile/lib/main.dart
  Changes:
    - [ ] Create RecipeListModel instance in _RecipAIAppState
    - [ ] Wrap MaterialApp.router with InheritedRecipeListModel
    - [ ] Pass RecipeListModel instance as notifier
    - [ ] Ensure proper disposal in dispose() method
    - [ ] Import new recipe_list_model.dart file

Task 4: Refactor RecipeListScreen to Use ChangeNotifier
  Action: MODIFY
  File: mobile/lib/features/recipe/recipe_list_screen.dart
  Changes:
    - [ ] Convert from StatefulWidget to StatelessWidget
    - [ ] Remove late Future<List<Recipe>> futureRecipes and setState calls
    - [ ] Use InheritedRecipeListModel.of(context) to get model
    - [ ] Keep FutureBuilder pattern but use model.recipes as future source
    - [ ] Remove _refreshRecipeList() method (replaced by model.refresh())
    - [ ] Update _onCreateTap and _onRecipeTap to only handle navigation (remove refresh calls)
    - [ ] Import InheritedRecipeListModel

Task 5: Update CreateRecipeScreen to Refresh Recipe List
  Action: MODIFY
  File: mobile/lib/features/recipe/create_recipe_screen.dart
  Changes:
    - [ ] Import InheritedRecipeListModel
    - [ ] In _createRecipe method, call InheritedRecipeListModel.of(context).refresh() after successful creation
    - [ ] Remove dependency on Navigator.pop return value handling
    - [ ] Ensure refresh is called before navigation

Task 6: Update EditRecipeScreen to Refresh Recipe List
  Action: MODIFY
  File: mobile/lib/features/recipe/edit_recipe_screen.dart
  Changes:
    - [ ] Import InheritedRecipeListModel
    - [ ] In _updateRecipe method, call InheritedRecipeListModel.of(context).refresh() after successful update
    - [ ] Remove dependency on Navigator.pop return value handling
    - [ ] Ensure refresh is called before navigation

Task 7: Update RecipeDetailScreen to Refresh Recipe List
  Action: MODIFY
  File: mobile/lib/features/recipe/recipe_detail_screen.dart
  Changes:
    - [ ] Import InheritedRecipeListModel
    - [ ] In _deleteRecipe method, call InheritedRecipeListModel.of(context).refresh() after successful deletion
    - [ ] Call refresh before navigation to recipes list
    - [ ] Remove dependency on manual refresh patterns
```

### Per Task Pseudocode

```dart
# Task 1: RecipeListModel Implementation
class RecipeListModel extends ChangeNotifier {
late Future<List<Recipe>> _recipes = _initRecipes();

Future<List<Recipe>> get recipes => _recipes;

Future<List<Recipe>> _initRecipes() {
print("Loading recipes...");
return ApiService.fetchRecipes();
}

void refresh() {
print("Refreshing recipes...");
_recipes = ApiService.fetchRecipes();
notifyListeners(); // Critical for UI updates
}
}

# Task 2: InheritedNotifier Implementation
class InheritedRecipeListModel extends InheritedNotifier<RecipeListModel> {
const InheritedRecipeListModel({
super.key,
required super.notifier,
required super.child,
});

static RecipeListModel of(BuildContext context) {
final result = context.dependOnInheritedWidgetOfExactType<InheritedRecipeListModel>();
assert(result != null, 'No InheritedRecipeListModel found in context');
return result!.notifier!;
}
}

# Task 4: RecipeListScreen Refactor
class RecipeListScreen extends StatelessWidget {
Widget build(BuildContext context) {
final recipeModel = InheritedRecipeListModel.of(context);

return FutureBuilder<List<Recipe>>(
future: recipeModel.recipes,
builder: (context, snapshot) {
// Same loading/error/data handling pattern as before
// But no setState() calls needed
},
);
}
}

# Task 5-7: Refresh Pattern
void _afterSuccessfulOperation() async {
// ... perform operation
final recipeModel = InheritedRecipeListModel.of(context);
recipeModel.refresh(); // Global state update
// Navigate or continue
}
```

## Validation

### Syntax and Style

```bash
# Run these FIRST - fix any errors before proceeding
cd mobile
flutter analyze

# Expected: No errors. If errors, READ the error and fix.
```

### Unit Tests

```bash
# Run and iterate until passing:
cd mobile
flutter test

# If failing: Read error, understand root cause, fix code, re-run (never mock to pass)
```

### Integration Tests

```bash
# Manual testing approach - no automated integration tests in current setup
cd mobile
flutter run

# Test scenarios:
# 1. Create recipe -> verify list updates automatically
# 2. Edit recipe -> verify list updates automatically  
# 3. Delete recipe -> verify list updates automatically
# 4. Navigate between screens -> verify lazy loading works
# 5. Error scenarios -> verify error handling still works
```

## Integration Points

- **Go Router Integration**: Navigation patterns remain unchanged, using existing AppRoute enum
- **API Service Integration**: Continue using static ApiService methods, no changes needed
- **Theme Integration**: Maintain existing theme access patterns with `Theme.of(context)`
- **Error Widget Integration**: Existing ApiErrorWidget and LoadingWidget patterns remain compatible
- **Form Widget Integration**: RecipeFormWidget continues to return results via Navigator.pop()

## Documentation

- Update `mobile/CLAUDE.md` to include ChangeNotifier pattern guidance
- Add state management section to mobile architecture documentation
- Document the refresh pattern for future developers
- Add examples of accessing RecipeListModel from any screen

## Final Validation Checklist

- [ ] Correct syntax (`flutter analyze` passes)
- [ ] Correct style (no linting warnings)
- [ ] All tests pass (`flutter test`)
- [ ] Manual test successful (create/edit/delete operations refresh list)
- [ ] Error cases handled gracefully (API failures, network issues)
- [ ] Logs are informative but not verbose (print statements for debugging)
- [ ] Navigation patterns remain consistent with existing codebase
- [ ] Performance is maintained (lazy loading works as expected)
- [ ] Global state updates work from all screens
- [ ] Documentation updated in mobile/CLAUDE.md

**SIP Confidence Score: 9/10**

This SIP provides comprehensive context for one-pass implementation success. The implementation follows established
Flutter patterns, maintains existing codebase conventions, and provides clear step-by-step tasks. The only potential
challenge is ensuring proper integration with the existing Go Router navigation, but the established patterns should
make this straightforward.