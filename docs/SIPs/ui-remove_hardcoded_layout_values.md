# SIP: Remove Hardcoded Layout Values in UI Code

## Goal

- Replace all hardcoded layout values (spacing, sizing, colors, text styles) in Flutter UI code with theme-based values
  or constants defined in `theme.dart`
- Ensure consistency across the app following Material Design 3 guidelines
- Make the app easier to maintain and update by centralizing all styling decisions
- Success criteria: No hardcoded `EdgeInsets`, `TextStyle`, size values, or colors in widget files; all values come from
  theme or constants

## Context

### Documentation and References

- **Material Design 3 Spacing**: https://m3.material.io/foundations/layout/understanding-layout/spacing
- **Material Design 3 Typography**: https://m3.material.io/styles/typography/applying-type
- **Flutter Material Design**: https://docs.flutter.dev/ui/design/material
- **Flutter EdgeInsets API**: https://api.flutter.dev/flutter/painting/EdgeInsets-class.html
- **Material 3 Migration Guide**: https://docs.flutter.dev/release/breaking-changes/material-3-migration

### Current Codebase Tree

```
mobile/
├── lib/
│   ├── main.dart                           # MaterialApp with basic M3 theme
│   ├── core/
│   │   ├── api_service.dart
│   │   └── app_config.dart
│   ├── features/
│   │   └── recipe/
│   │       ├── recipe.dart                 # Data model
│   │       ├── recipe_detail.dart          # Data model
│   │       ├── recipe_list_screen.dart     # Contains hardcoded fontSize: 18
│   │       ├── recipe_detail_screen.dart   # Multiple hardcoded values
│   │       └── recipe_list_item.dart       # Hardcoded EdgeInsets
│   └── shared/
│       └── loading_widget.dart             # Clean, uses theme properly
└── pubspec.yaml                            # Flutter 3.32, Material enabled
```

### Desired Codebase Tree

```
mobile/
├── lib/
│   ├── main.dart                           # MaterialApp with custom theme
│   ├── core/
│   │   ├── api_service.dart
│   │   ├── app_config.dart
│   │   └── theme.dart                      # NEW: Centralized theme + constants
│   ├── features/
│   │   └── recipe/
│   │       ├── recipe.dart
│   │       ├── recipe_detail.dart
│   │       ├── recipe_list_screen.dart     # Uses theme.textTheme.labelMedium
│   │       ├── recipe_detail_screen.dart   # Uses theme constants
│   │       └── recipe_list_item.dart       # Uses theme constants
│   └── shared/
│       └── loading_widget.dart             # Already theme-compliant
```

### Known Gotchas of Our Codebase and Library Quirks

- **Material 3 already enabled**: `ColorScheme.fromSeed(seedColor: Colors.deepOrange)` in main.dart
- **Flutter lints enabled**: Using `flutter_lints: ^5.0.0` which will catch styling issues
- **Good existing patterns**: Most text already uses `Theme.of(context).textTheme.*` - follow this pattern
- **Widget consistency**: All widgets follow stateless/stateful pattern with proper key usage
- **API dependency**: Widgets depend on API service but layout values are independent

### Hardcoded Values Found (Complete List)

```dart
// TEXT STYLES (1 instance)
TextStyle
(
fontSize: 18) // recipe_list_screen.dart:74

// EDGE INSETS SPACING (8 instances)  
EdgeInsets.symmetric(horizontal: 16.0, vertical: 4.0) // recipe_list_item.dart:14
EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0) // recipe_list_item.dart:22
EdgeInsets.all(16.0) // recipe_detail_screen.dart:66, 89, 127
EdgeInsets.symmetric(vertical: 4.0) // recipe_detail_screen.dart:95
EdgeInsets.symmetric(vertical: 8.0) // recipe_detail_screen.dart:136

// SIZEBOX SPACING (12 instances)
SizedBox(height: 16) // Multiple files
SizedBox(height: 24) // recipe_detail_screen.dart
SizedBox(height: 8) // recipe_detail_screen.dart  
SizedBox(width: 8) // recipe_detail_screen.dart
SizedBox(width: 12) // recipe_detail_screen.dart

// ICON SIZING & COLORS (3 instances)
Icons.error_outline, size: 64, color: Colors.red // 2 files
Icons.circle, size: 8 // recipe_detail_screen.dart:98

// CONTAINER SIZING (1 instance)
width: 24, height: 24 // recipe_detail_screen.dart:143-144

// COMPLEX TEXT STYLES (1 instance)
TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight
.
bold
) // recipe_detail_screen.dart:152
```

## Implementation Plan

### Tasks

```
Task 1: Create centralized theme configuration
  Action: CREATE
  File: mobile/lib/core/theme.dart
  Changes:
    - [ ] Define Material 3 theme with extended color scheme and typography
    - [ ] Create AppSpacing class with standardized spacing constants (4, 8, 12, 16, 24dp)
    - [ ] Create AppSizes class for common widget dimensions
    - [ ] Create AppTextStyles class for custom text styles not in M3
    - [ ] Follow Material Design 3 8dp grid system
    - [ ] Use const constructors for performance

Task 2: Update main.dart to use custom theme
  Action: MODIFY  
  File: mobile/lib/main.dart
  Changes:
    - [ ] Import new theme.dart file
    - [ ] Replace basic ThemeData with custom AppTheme.theme
    - [ ] Maintain existing deep orange seed color for consistency

Task 3: Fix hardcoded text style in recipe list screen
  Action: MODIFY
  File: mobile/lib/features/recipe/recipe_list_screen.dart  
  Changes:
    - [ ] Add `const theme = Theme.of(context);` at the beginning of build method
    - [ ] Replace `TextStyle(fontSize: 18)` with `theme.textTheme.labelMedium`
    - [ ] Update all existing `Theme.of(context)` calls to use `theme` variable
    - [ ] Follow exact pattern from feature request example
    - [ ] Test that "No recipes found" message displays correctly

Task 4: Replace hardcoded spacing in recipe list item
  Action: MODIFY
  File: mobile/lib/features/recipe/recipe_list_item.dart
  Changes:
    - [ ] Add `const theme = Theme.of(context);` at the beginning of build method
    - [ ] Replace EdgeInsets.symmetric(horizontal: 16.0, vertical: 4.0) with AppSpacing.cardMargin
    - [ ] Replace EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0) with AppSpacing.listTilePadding
    - [ ] Update existing `Theme.of(context).textTheme.titleMedium` to use `theme` variable
    - [ ] Import theme.dart and access constants properly

Task 5: Replace all hardcoded values in recipe detail screen
  Action: MODIFY
  File: mobile/lib/features/recipe/recipe_detail_screen.dart
  Changes:
    - [ ] Add `const theme = Theme.of(context);` at the beginning of build method
    - [ ] Replace all EdgeInsets.all(16.0) with AppSpacing.screenPadding
    - [ ] Replace EdgeInsets.symmetric(vertical: 4.0) with AppSpacing.smallVertical
    - [ ] Replace EdgeInsets.symmetric(vertical: 8.0) with AppSpacing.mediumVertical
    - [ ] Replace hardcoded SizedBox values with AppSpacing constants
    - [ ] Replace icon sizing: Icons.error_outline, size: 64 with AppSizes.errorIconSize
    - [ ] Replace Icons.circle, size: 8 with AppSizes.bulletIconSize
    - [ ] Replace Container width: 24, height: 24 with AppSizes.stepNumberContainer
    - [ ] Replace TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.bold) with AppTextStyles.stepNumber
    - [ ] Update all existing `Theme.of(context)` calls to use `theme` variable

Task 6: Update recipe list screen error icon
  Action: MODIFY
  File: mobile/lib/features/recipe/recipe_list_screen.dart
  Changes:
    - [ ] Replace Icons.error_outline, size: 64, color: Colors.red with proper theme usage
    - [ ] Use AppSizes.errorIconSize and theme.colorScheme.error (using theme variable)
    - [ ] Maintain consistent error display pattern with recipe_detail_screen.dart

Task 7: Update loading widget to use const theme pattern
  Action: MODIFY
  File: mobile/lib/shared/loading_widget.dart
  Changes:
    - [ ] Add `const theme = Theme.of(context);` at the beginning of build method
    - [ ] Update existing `Theme.of(context).textTheme.bodyMedium` to use `theme` variable

Task 8: Validate all imports and theme access
  Action: MODIFY
  File: All modified files
  Changes:
    - [ ] Ensure proper import of '../core/theme.dart' in all files
    - [ ] Verify all widgets use `const theme = Theme.of(context);` pattern
    - [ ] Check that no hardcoded values remain using grep search
```

### Per Task Pseudocode

```dart
// Task 1: theme.dart structure
class AppTheme {
  static ThemeData get theme =>
      ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepOrange),
        // extend with custom properties
      );
}

class AppSpacing {
  static const EdgeInsets screenPadding = EdgeInsets.all(16.0);
  static const EdgeInsets cardMargin = EdgeInsets.symmetric(horizontal: 16.0, vertical: 4.0);
  static const EdgeInsets listTilePadding = EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0);
  static const EdgeInsets smallVertical = EdgeInsets.symmetric(vertical: 4.0);
  static const EdgeInsets mediumVertical = EdgeInsets.symmetric(vertical: 8.0);

  // SizedBox equivalents
  static const double small = 8.0;
  static const double medium = 16.0;
  static const double large = 24.0;
}

class AppSizes {
  static const double errorIconSize = 64.0;
  static const double bulletIconSize = 8.0;
  static const double stepNumberContainer = 24.0;
}

class AppTextStyles {
  static const TextStyle stepNumber = TextStyle(
    color: Colors.white,
    fontSize: 12,
    fontWeight: FontWeight.bold,
  );
}

// Task 3: Text style replacement pattern
// Before:
Text
('No recipes found
'
, style: TextStyle(fontSize: 18))

// After (following exact pattern from feature request):
@override
Widget build(BuildContext context) {
const theme = Theme.of(context);
return Text('No recipes found', style: theme.textTheme.labelMedium);
}
```

## Validation

Specify what can be used to check if the feature is complete and meets certain standards

### Syntax and Style

CLI commands used for validating syntax and style

```bash
# Run these FIRST - fix any errors before proceeding
cd mobile
flutter analyze
dart format --set-exit-if-changed lib/

# Expected: No errors. If errors, READ the error and fix.
```

### Unit Tests

CLI commands used for running unit tests

```bash
# Run and iterate until passing:
cd mobile
flutter test
# If failing: Read error, understand root cause, fix code, re-run (never mock to pass)
```

### Code Quality Validation

```bash
# Search for any remaining hardcoded values:
cd mobile
grep -r "EdgeInsets\." lib/ --exclude-dir=core
grep -r "TextStyle(" lib/ --exclude-dir=core  
grep -r "fontSize:" lib/ --exclude-dir=core
grep -r "size:" lib/ --exclude-dir=core
grep -r "Colors\." lib/ --exclude-dir=core
# Expected: No results found (all hardcoded values should be in theme.dart)
```

## Integration Points

- **No API changes required**: This is purely a UI refactoring task
- **No database changes**: Layout changes don't affect data layer
- **Theme integration**: New theme.dart must be properly imported in main.dart
- **Widget consistency**: All widgets must access theme through `Theme.of(context)` pattern

## Documentation

- **Update mobile/CLAUDE.md**: Add guideline to use prefer values from Theme.of() and if that's not possible or there is
  no good value then it should look for constant in theme.dart or create a new one
- **Update docs/mobile/ui.md**: Document the new centralized theming approach and available constants
- **No API documentation changes needed**: This is frontend-only change

## Final Validation Checklist

- [ ] Correct syntax (flutter analyze passes)
- [ ] Correct style (dart format passes)
- [ ] All tests pass (flutter test passes)
- [ ] Manual test successful (app runs and displays correctly)
- [ ] Error cases handled gracefully (error icons and messages display properly)
- [ ] Logs are informative but not verbose (no new logging added)
- [ ] Documentation updated if needed (CLAUDE.md and ui.md updated)
- [ ] No hardcoded values remain (grep searches return no results)
- [ ] Theme constants follow Material Design 3 8dp grid system
- [ ] All spacing is visually consistent across the app

## SIP Confidence Score: 9/10

**Justification**: This SIP provides comprehensive context with:

- Complete inventory of all hardcoded values (26 instances across 4 files)
- Exact file locations and line references
- Clear before/after examples matching the feature request
- Material Design 3 research and spacing guidelines
- Detailed task breakdown with specific file changes
- Multiple validation approaches (syntax, tests, manual, grep)
- Understanding of existing good patterns in the codebase
- Proper Flutter/Dart conventions and performance considerations (const constructors)

**Why not 10/10**: Minor uncertainty around edge cases in theme access patterns and potential Material 3 token
compatibility, but these are easily addressable during implementation.