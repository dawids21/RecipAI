# SIP: Manual Recipe Creation UI

## Goal

- Implement a manual recipe creation screen in the mobile application that allows users to create recipes from scratch
- Convert the existing single FAB button to an expandable FAB with "Import" and "Create" options
- Create a form-based UI with fields for recipe name, ingredients (name + quantity/unit), and instructions
- Save recipes using the existing POST /recipes API endpoint and refresh the recipe list upon successful creation
- Success criteria: Users can manually create recipes, data is properly parsed (especially ingredient quantities), and
  the UI follows existing app patterns

## Context

### Documentation and References

- **PRD User Story US-002**: Manual recipe creation feature specification (`docs/prd.md:78-84`)
- **API Documentation**: POST /recipes endpoint structure (`docs/backend/api.md:55-114`)
- **Mobile Architecture**: Feature-based modular structure (`docs/mobile/mobile.md`)
- **UI Patterns**: Existing screen and widget patterns (`docs/mobile/ui.md`)
- **Flutter Expandable FAB**: https://docs.flutter.dev/cookbook/effects/expandable-fab
- **Flutter Form Validation**: https://docs.flutter.dev/cookbook/forms/validation
- **Package Alternative**: https://pub.dev/packages/flutter_expandable_fab (if native implementation proves complex)

### Current Codebase Tree

```
mobile/lib/
├── main.dart
├── core/
│   ├── api_service.dart           # Has POST method pattern, needs createRecipe()
│   ├── app_config.dart
│   └── theme.dart                 # Material Design 3 + AppSpacing constants
├── features/
│   ├── recipe/
│   │   ├── recipe.dart            # Basic Recipe model
│   │   ├── recipe_detail.dart     # RecipeDetail, Ingredient, Instruction models
│   │   ├── recipe_list_screen.dart # Has FAB for import - MODIFY to expandable FAB
│   │   ├── recipe_detail_screen.dart
│   │   ├── recipe_list_item.dart
│   │   ├── ingredient_bullet.dart
│   │   └── step_number_badge.dart
│   └── import/
│       ├── import_screen.dart     # Navigation pattern example
│       └── web_recipe_extractor.dart
└── shared/
    ├── loading_widget.dart        # Reusable loading indicator
    ├── api_error_widget.dart      # Error handling pattern
    └── error_icon.dart
```

### Desired Codebase Tree

```
mobile/lib/
├── main.dart
├── core/
│   ├── api_service.dart           # + createRecipe() method
│   ├── app_config.dart
│   └── theme.dart
├── features/
│   ├── recipe/
│   │   ├── recipe.dart
│   │   ├── recipe_detail.dart
│   │   ├── recipe_list_screen.dart # MODIFIED: FAB -> expandable FAB
│   │   ├── recipe_detail_screen.dart
│   │   ├── recipe_list_item.dart
│   │   ├── ingredient_bullet.dart
│   │   ├── step_number_badge.dart
│   │   ├── create_recipe_screen.dart     # NEW: Manual creation form
│   │   └── ingredient_input_widget.dart  # NEW: Reusable ingredient input
│   └── import/
│       ├── import_screen.dart
│       └── web_recipe_extractor.dart
└── shared/
    ├── loading_widget.dart
    ├── api_error_widget.dart
    └── error_icon.dart
```

### Known Gotchas of Our Codebase and Library Quirks

- **API Service Pattern**: Uses static methods with http.Client, follow existing `fetchRecipes()` and
  `extractRecipeFromText()` patterns
- **Navigation**: Always pass result back for list refresh (see `import_screen.dart:106-108`)
- **Theme Access**: Use `final theme = Theme.of(context);` pattern consistently (`mobile/lib/core/theme.dart:12`)
- **Error Handling**: Wrap API calls in try-catch, use ApiErrorWidget for display
- **Form Disposal**: Must dispose TextEditingController instances to prevent memory leaks
- **Ingredient Parsing**: Feature requirement to use regex for quantity/unit extraction from text input
- **Material Design 3**: Use `AppSpacing` constants instead of hardcoded values
- **Modular Architecture**: Keep all recipe-related code in `features/recipe/` folder per coding practices

## Implementation Plan

### Tasks

```
Task 1: Add createRecipe API method
  Action: MODIFY
  File: mobile/lib/core/api_service.dart
  Changes:
    - [ ] Add static Future<RecipeDetail> createRecipe(RecipeDetail recipe) method
    - [ ] Follow existing POST pattern from extractRecipeFromText()
    - [ ] Use '$_baseUrl/recipes' endpoint with JSON body recipe.toJson()
    - [ ] Return RecipeDetail.fromJson() on 201 Created response
    - [ ] Throw Exception with descriptive messages for error cases

Task 2: Create ingredient input widget
  Action: CREATE
  File: mobile/lib/features/recipe/ingredient_input_widget.dart
  Changes:
    - [ ] StatefulWidget with two TextEditingController (name, quantityWithUnit)
    - [ ] Use Row with two Expanded TextFormField widgets
    - [ ] Follow theme patterns: AppSpacing.small between fields
    - [ ] Add validation: required name, optional quantity
    - [ ] Include helper text for quantity format (e.g., "300g", "2 cups")
    - [ ] Provide callback for ingredient data: ValueChanged<Ingredient?>

Task 3: Create recipe creation screen
  Action: CREATE
  File: mobile/lib/features/recipe/create_recipe_screen.dart
  Changes:
    - [ ] StatefulWidget with Form and GlobalKey<FormState>
    - [ ] TextEditingController for recipe name (required validation)
    - [ ] List<IngredientInputWidget> with add/remove functionality
    - [ ] TextEditingController for instructions (required validation)
    - [ ] Follow existing screen patterns: AppBar + theme.colorScheme.inversePrimary
    - [ ] Use Column with AppSpacing.screenPadding
    - [ ] Add "Add Ingredient" button and ingredient removal icons
    - [ ] Save button calls API, shows loading state, handles errors
    - [ ] Navigate back with RecipeDetail result on success

Task 4: Implement ingredient parsing utility
  Action: MODIFY (add utility method)
  File: mobile/lib/features/recipe/create_recipe_screen.dart
  Changes:
    - [ ] Static method parseIngredientText(String text) -> Ingredient
    - [ ] Use RegExp to extract quantity + unit from text (e.g., "300g flour")
    - [ ] Pattern: /(\d+(?:\.\d+)?)\s*([a-zA-Z]*)\s*(.+)/ for "number unit name"
    - [ ] Fallback: if no match, use full text as name, empty quantity
    - [ ] Handle edge cases: "2 cups flour", "flour", "300g", "salt to taste"

Task 5: Convert FAB to expandable FAB
  Action: MODIFY
  File: mobile/lib/features/recipe/recipe_list_screen.dart
  Changes:
    - [ ] Replace single FloatingActionButton with ExpandableFab implementation
    - [ ] Use ExpandableFab widget with children property for multiple actions
    - [ ] Two FloatingActionButton.small children: "Import Recipe" and "Create Recipe"
    - [ ] Follow Material Design 3 styling with theme colors for action buttons
    - [ ] Use Icons.add for main FAB, Icons.download for import, Icons.edit for create
    - [ ] Reference _onImportTap() and _onCreateTap() methods in action button onPressed callbacks

Task 6: Update navigation and refresh logic
  Action: MODIFY
  File: mobile/lib/features/recipe/recipe_list_screen.dart
  Changes:
    - [ ] Add _onCreateTap() method similar to _onImportTap()
    - [ ] Handle CreateRecipeScreen result and call _refreshRecipeList()
    - [ ] Ensure proper navigation with MaterialPageRoute
    - [ ] Add success feedback (SnackBar) after recipe creation
```

### Per Task Pseudocode

```dart
// Task 1: API Service Method
static Future<RecipeDetail> createRecipe
(
RecipeDetail recipe) async {
try {
final response = await _client.post(
Uri.parse('$_baseUrl/recipes'),
headers: {'Content-Type': 'application/json'},
body: json.encode(recipe.toJson()),
);

if (response.statusCode == 201) {
return RecipeDetail.fromJson(json.decode(response.body));
} else {
throw Exception('Failed to create recipe: ${response.statusCode}');
}
} catch (e) {
throw Exception('Network error while creating recipe: $e');
}
}

// Task 4: Ingredient Parsing
static Ingredient parseIngredientText(String name, String quantityText) {
final regex = RegExp(r'(\d+(?:[.,]\d+)?)\s*([a-zA-Z]*)\s*');
final match = regex.firstMatch(quantityText.trim());

if (match != null) {
final quantity = match.group(1) ?? '';
final unit = match.group(2) ?? '';
return Ingredient(name: name, quantity: quantity, unit: unit.isEmpty ? null : unit);
} else {
return Ingredient(name: name, quantity: quantityText, unit: null);
}
}

// Task 5: Expandable FAB Implementation
ExpandableFab(
distance: 112,
children: [
FloatingActionButton.small(
heroTag: "import",
onPressed: _onImportTap,
child: const Icon(Icons.download),
tooltip: 'Import Recipe',
),
FloatingActionButton.small(
heroTag: "create",
onPressed: _onCreateTap,
child: const Icon(Icons.edit),
tooltip: 'Create Recipe',
)
,
]
,
)
```

## Validation

### Syntax and Style

```bash
# Run these FIRST - fix any errors before proceeding
cd mobile
flutter analyze
dart format --set-exit-if-changed lib/

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
# Manual testing required since no integration tests are set up:
# 1. Launch app: flutter run
# 2. Test expandable FAB opens with both action buttons
# 3. Test create recipe flow: name + ingredients + instructions
# 4. Verify ingredient parsing works correctly
# 5. Confirm recipe appears in list after creation
# 6. Test error cases: empty fields, network failures
```

## Integration Points

- **API Integration**: Uses existing POST /recipes endpoint, no backend changes required
- **Data Models**: Leverages existing RecipeDetail, Ingredient, Instruction models
- **Navigation**: Follows established pattern of returning result for list refresh
- **UI Components**: Integrates with existing shared widgets (LoadingWidget, ApiErrorWidget)

## Documentation

- Update `docs/mobile/ui.md` to include new CreateRecipeScreen and IngredientInputWidget
- Consider updating `mobile/CLAUDE.md` with form validation patterns if new patterns emerge
- No backend documentation changes needed as API endpoint already exists

## Final Validation Checklist

- [ ] Correct syntax (flutter analyze passes)
- [ ] Correct style (dart format passes)
- [ ] All tests pass (flutter test)
- [ ] Manual test successful (all user flows work)
- [ ] Error cases handled gracefully (network errors, validation errors)
- [ ] Logs are informative but not verbose (follow existing ApiService pattern)
- [ ] Documentation updated if needed (mobile UI docs)
- [ ] Ingredient parsing regex works for common cases ("300g flour", "2 cups", "salt")
- [ ] Expandable FAB is intuitive and follows Material Design
- [ ] Form validation provides clear user feedback
- [ ] Memory leaks prevented (controller disposal)
- [ ] Follows existing codebase patterns and conventions