# SIP: UI Refactor - Recipe Extraction Flow and Create Recipe Enhancement

## Goal

- **Primary**: Refactor the mobile app to handle the new backend API that returns extraction-specific DTOs without
  automatically saving recipes
- **Behavior Change**: After recipe extraction, navigate to create recipe screen with pre-filled data instead of going
  directly to recipe list
- **UI Enhancement**: Support two modes in create recipe screen: manual creation and extraction-based creation with
  pre-filled data
- **Terminology**: Rename "Import Recipe" to "Extract Recipe" throughout the UI to reflect the API changes
- **Success Criteria**: Users can extract recipe data, review and edit it in the create recipe form, then save it to the
  database

## Context

### Documentation and References

- **Feature Request**: `docs/feature-requests/ui-refactor-importing-edit-recipe.md`
- **Backend API Changes**: `docs/reports/2025-08-19-api-refactor-remove-dependency-extraction-recipes.md`
- **API Documentation**: `docs/backend/api.md` - Shows new ExtractedRecipe response format
- **Mobile App Overview**: `docs/mobile/mobile.md` - Current app structure and features
- **Mobile UI Overview**: `docs/mobile/ui.md` - Current screens and navigation flow

### Current Codebase Tree

```
mobile/lib/
├── main.dart
├── core/
│   ├── routes.dart                 # Go router configuration
│   ├── api_service.dart           # API service methods
│   ├── app_config.dart            
│   └── theme.dart                 
├── shared/                         # Shared widgets
└── features/
    ├── recipe/
    │   ├── recipe.dart                    # Basic recipe model
    │   ├── recipe_detail.dart             # Detailed recipe models (Ingredient, Instruction, RecipeData, RecipeDetail)
    │   ├── create_recipe_screen.dart      # Create recipe screen
    │   ├── recipe_form_widget.dart        # Form widget that supports initialRecipe parameter
    │   ├── recipe_list_screen.dart        # Main recipe list with speed dial
    │   └── [other recipe widgets...]
    └── import/
        ├── import_screen.dart             # Current import screen with WebView
        └── web_recipe_extractor.dart      # HTML extraction utility
```

### Desired Codebase Tree

```
mobile/lib/
├── main.dart
├── core/
│   ├── routes.dart                 # Updated with new route parameter support
│   ├── api_service.dart           # Updated to handle new ExtractedRecipe response
│   ├── app_config.dart            
│   └── theme.dart                 
├── shared/                         # Shared widgets
└── features/
    ├── recipe/
    │   ├── recipe.dart                    
    │   ├── recipe_detail.dart             
    │   ├── create_recipe_screen.dart      # Updated to handle extracted data parameter
    │   ├── recipe_form_widget.dart        # Already supports initialRecipe, may need ExtractedRecipe conversion
    │   ├── recipe_list_screen.dart        # Updated UI labels to "Extract Recipe"
    │   └── [other recipe widgets...]
    └── extraction/                         # RENAMED from import/
        ├── extracted_recipe.dart           # NEW: ExtractedRecipe data models
        ├── extraction_screen.dart          # RENAMED, updated navigation flow
        └── web_recipe_extractor.dart      # No changes needed
```

### Known Gotchas of Our Codebase and Library Quirks

- **Go Router Navigation**: Routes use nested structure with pathParameters for dynamic routes
- **RecipeFormWidget**: Already supports `initialRecipe` parameter for pre-filling form data
- **API Response Format**: New ExtractedRecipe format has different field structure than RecipeDetail
- **Navigation Pattern**: Currently uses `context.pop(recipe)` to return data to previous screen
- **Flutter State Management**: Uses StatefulWidget pattern with manual state updates via setState()
- **Form Data Conversion**: RecipeFormWidget expects RecipeDetail format but will receive ExtractedRecipe format

## Implementation Plan

### Tasks

```
Task 1: Create ExtractedRecipe Data Models
  Action: CREATE
  File: mobile/lib/features/extraction/extracted_recipe.dart
  Changes:
    - [ ] Create ExtractedRecipe class matching new API response format
    - [ ] Create ExtractedIngredient class with name, quantity, unit fields
    - [ ] Create ExtractedInstruction class with step field  
    - [ ] Add fromJson() factory constructors for JSON deserialization
    - [ ] Follow existing pattern from recipe_detail.dart models
    - [ ] Add method to convert ExtractedRecipe to RecipeDetail format

Task 2: Update API Service for New Response Format
  Action: MODIFY
  File: mobile/lib/core/api_service.dart
  Changes:
    - [ ] Update extractRecipeFromText() method to return ExtractedRecipe instead of RecipeDetail
    - [ ] Update import statement to include ../features/extraction/extracted_recipe.dart
    - [ ] Update JSON deserialization to use ExtractedRecipe.fromJson()
    - [ ] Keep method signature compatible for existing usage

Task 3: Rename Import Feature Directory and Files
  Action: RENAME/MODIFY
  Files: 
    - mobile/lib/features/import/ → mobile/lib/features/extraction/
    - mobile/lib/features/import/import_screen.dart → mobile/lib/features/extraction/extraction_screen.dart
  Changes:
    - [ ] Rename directory from import to extraction
    - [ ] Rename import_screen.dart to extraction_screen.dart
    - [ ] Update class name from ImportScreen to ExtractionScreen
    - [ ] Update import statements in affected files

Task 4: Update Extraction Screen Navigation Flow
  Action: MODIFY  
  File: mobile/lib/features/extraction/extraction_screen.dart
  Changes:
    - [ ] Update UI labels from "Import Recipe" to "Extract Recipe"
    - [ ] Change navigation flow: instead of context.pop(recipe), navigate to create recipe screen
    - [ ] Pass extracted recipe data as parameter to create recipe screen
    - [ ] Update success message from "imported" to "extracted"
    - [ ] Handle ExtractedRecipe response type instead of RecipeDetail

Task 5: Update Routes Configuration
  Action: MODIFY
  File: mobile/lib/core/routes.dart  
  Changes:
    - [ ] Rename recipeImport to recipeExtraction in AppRoute enum
    - [ ] Update route path from 'import' to 'extraction'
    - [ ] Update builder to use ExtractionScreen instead of ImportScreen
    - [ ] Add parameter support for create recipe route to accept extracted data
    - [ ] Update import statement from import_screen to extraction_screen

Task 6: Enhanced Create Recipe Screen with Extracted Data Support
  Action: MODIFY
  File: mobile/lib/features/recipe/create_recipe_screen.dart
  Changes:
    - [ ] Add optional ExtractedRecipe parameter to constructor
    - [ ] Convert ExtractedRecipe to RecipeDetail format if provided
    - [ ] Pass converted recipe as initialRecipe to RecipeFormWidget
    - [ ] Update screen title to indicate "Create from Extracted" vs "Create Recipe"
    - [ ] Handle case where user came from extraction vs manual creation

Task 7: Update Recipe List Screen UI Labels
  Action: MODIFY
  File: mobile/lib/features/recipe/recipe_list_screen.dart
  Changes:
    - [ ] Update SpeedDialChild label from "Import Recipe" to "Extract Recipe"  
    - [ ] Update _onImportTap method name to _onExtractionTap
    - [ ] Update navigation call to use AppRoute.recipeExtraction instead of recipeImport
    - [ ] Update result handling variable names for clarity

Task 8: Update Recipe Form Widget for Better Extracted Data Handling
  Action: MODIFY (if needed)
  File: mobile/lib/features/recipe/recipe_form_widget.dart
  Changes:
    - [ ] Review if ExtractedRecipe to RecipeDetail conversion needs form-specific handling
    - [ ] Ensure instructions parsing works correctly with extracted data format
    - [ ] Verify ingredient parsing handles extracted format properly
    - [ ] Add any extraction-specific initialization logic if needed
```

### Per Task Pseudocode

```dart
// Task 1: ExtractedRecipe Models
class ExtractedIngredient {
  final String name;
  final String quantity;
  final String? unit;

  factory ExtractedIngredient.fromJson(Map<String, dynamic> json) {
    // Parse from new API format
  }
}

class ExtractedRecipe {
  final String name;
  final String description;
  final List<ExtractedIngredient> ingredients;
  final List<ExtractedInstruction> steps;

  // Conversion method to RecipeDetail
  RecipeDetail toRecipeDetail() {
    return RecipeDetail(
      id: '', // Empty for new recipes
      name: name,
      data: RecipeData(
        ingredients: ingredients.map((e) => e.toIngredient()).toList(),
        instructions: steps.map((e) => e.toInstruction()).toList(),
      ),
    );
  }
}

// Task 4: Updated Extraction Screen Navigation
Future<void> _extractRecipe() async {
  try {
    final extractedRecipe = await ApiService.extractRecipeFromText(htmlContent);

    // Navigate to create recipe screen with extracted data
    if (mounted) {
      context.pushNamed(
        AppRoute.recipeCreate.name,
        extra: extractedRecipe, // Pass as extra parameter
      );
    }
  } catch (e) {
    // Handle error
  }
}

// Task 6: Enhanced Create Recipe Screen
class CreateRecipeScreen extends StatefulWidget {
  final ExtractedRecipe? extractedRecipe;

  const CreateRecipeScreen({super.key, this.extractedRecipe});

  @override
  Widget build(BuildContext context) {
    final initialRecipe = extractedRecipe?.toRecipeDetail();
    final title = extractedRecipe != null ? 'Review & Create Recipe' : 'Create Recipe';

    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: RecipeFormWidget(
        initialRecipe: initialRecipe,
        onSave: _createRecipe,
      ),
    );
  }
}
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
# Manual testing required - no automated integration tests exist yet
cd mobile
flutter run
# Test flow: 
# 1. Navigate to extract recipe
# 2. Extract a recipe from a website  
# 3. Verify navigation to create recipe screen
# 4. Verify form is pre-filled with extracted data
# 5. Save recipe and verify it appears in recipe list
```

## Integration Points

- **API Response Format**: Mobile app must handle new ExtractedRecipe response from POST /extract/text endpoint
- **Navigation Flow**: New flow goes extraction -> create recipe -> recipe list instead of extraction -> recipe list
- **Data Conversion**: ExtractedRecipe format must be converted to RecipeDetail format for form usage
- **Route Parameters**: Create recipe screen must accept extracted data via Go Router parameters

## Documentation

- **docs/mobile/mobile.md**: Update features section to reflect "extraction" instead of "import"
- **docs/mobile/ui.md**: Update screens section and navigation flow to reflect new extraction -> create recipe flow
- **mobile/CLAUDE.md**: Update AI rules if any changes affect coding practices or architecture

## Final Validation Checklist

- [ ] Correct syntax (flutter analyze passes)
- [ ] Correct style (dart format passes)
- [ ] All tests pass (flutter test)
- [ ] Manual test successful - full extraction to save flow works
- [ ] Error cases handled gracefully (network errors, invalid extraction)
- [ ] UI labels updated from "Import" to "Extract" throughout
- [ ] Navigation flow works: extraction -> create recipe (pre-filled) -> recipe list
- [ ] Both creation modes work: manual creation and extraction-based creation
- [ ] Documentation updated to reflect changes

**SIP Confidence Score: 9/10** - High confidence for one-pass implementation success. The existing RecipeFormWidget
already supports pre-filled data, API changes are well-documented, and the navigation pattern follows existing Go Router
conventions. The main complexity is data format conversion, which is straightforward with clear model definitions.