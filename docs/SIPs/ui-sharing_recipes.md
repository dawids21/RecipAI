# SIP: UI Sharing Recipes Feature Implementation

## Goal

- Implement recipe sharing UI functionality in the Flutter mobile app
- Add share option to AppBar on RecipeDetailScreen with popup showing shared users and roles
- Allow users to add new users by email and share recipes with them (EDITOR role)
- Enable unsharing from users (except OWNER) with confirmation
- Conditionally render delete recipe button only for OWNER role users
- Update RecipeDetail model to include role field from API response
- Add API service methods for sharing operations using existing backend endpoints

## Context

### Documentation and References

- **Flutter AlertDialog Documentation**: https://api.flutter.dev/flutter/material/AlertDialog-class.html
- **Flutter showDialog Documentation**: https://api.flutter.dev/flutter/material/showDialog.html
- **Material Design 3 Dialogs**: https://m3.material.io/components/dialogs/overview
- **Material Design 3 for Flutter**: https://m3.material.io/develop/flutter
- **Flutter Form Validation Best Practices**: https://docs.flutter.dev/cookbook/forms/validation
- **Project Documentation**:
    - `docs/mobile/mobile.md` - Mobile app overview with data models and usage patterns
    - `docs/mobile/ui.md` - UI components, navigation flow, and theme system
    - `docs/backend/api.md` - API endpoints including sharing endpoints
    - `docs/feature-requests/ui-sharing_recipes.md` - Original feature requirements
- **Existing Codebase Patterns**:
    - `mobile/lib/features/recipe/recipe_detail_screen.dart` - AppBar with PopupMenuButton pattern
    - `mobile/lib/features/extraction/extraction_dialog.dart` - AlertDialog implementation pattern
    - `mobile/lib/core/api_service.dart` - API service pattern with InheritedWidget
    - `mobile/lib/core/theme.dart` - Theme constants and spacing guidelines
    - `mobile/test/widget_test.dart` - Testing patterns with MockAuthService

### Current Codebase Tree

```
mobile/lib/features/recipe/
├── recipe.dart                     # Basic recipe model (id, name)
├── recipe_detail.dart              # RecipeDetail model - NEEDS role field
├── recipe_detail_screen.dart       # Main screen with AppBar - NEEDS share button
├── recipe_list_item.dart           # List item widget
├── recipe_list_screen.dart         # List screen
├── recipe_form_widget.dart         # Form for create/edit
├── create_recipe_screen.dart       # Create screen
├── edit_recipe_screen.dart         # Edit screen
├── ingredient_input_widget.dart    # Ingredient input component
├── ingredient_bullet.dart          # Bullet point component
├── step_number_badge.dart          # Step number component
└── recipe_list_model.dart          # List model
```

### Desired Codebase Tree

```
mobile/lib/features/recipe/
├── recipe.dart                     # Basic recipe model - NO CHANGE
├── recipe_detail.dart              # RecipeDetail model - ADD role field
├── recipe_detail_screen.dart       # Main screen - ADD share button, UPDATE delete visibility
├── recipe_sharing_dialog.dart      # NEW: Sharing dialog with user list and input
├── shared_user.dart                # NEW: SharedUser model for API response
├── recipe_list_item.dart           # List item widget - NO CHANGE
├── recipe_list_screen.dart         # List screen - NO CHANGE
├── recipe_form_widget.dart         # Form for create/edit - NO CHANGE
├── create_recipe_screen.dart       # Create screen - NO CHANGE
├── edit_recipe_screen.dart         # Edit screen - NO CHANGE
├── ingredient_input_widget.dart    # Ingredient input component - NO CHANGE
├── ingredient_bullet.dart          # Bullet point component - NO CHANGE
├── step_number_badge.dart          # Step number component - NO CHANGE
└── recipe_list_model.dart          # List model - NO CHANGE
```

### Known Gotchas of Our Codebase and Library Quirks

- **Theme Access Pattern**: Always use `final theme = Theme.of(context);` at the beginning of build methods
- **Spacing Constants**: Use `AppSpacing` constants from `core/theme.dart` (screenPadding, cardMargin, small, medium,
  large)
- **API Service Access**: Use `InheritedApiService.of(context)` in `didChangeDependencies()` and cache reference
- **Navigation Pattern**: Use `go_router` with `AppRoute.routeName.name` for type-safe navigation
- **Error Handling**: Use try-catch blocks with user-friendly SnackBar messages
- **Form Validation**: Use Flutter's built-in form validation with `GlobalKey<FormState>`
- **Modular Architecture**: All recipe-related files go directly in `features/recipe/` directory
- **Email Validation**: Use `@Email` pattern or RegExp for email validation in forms
- **PopupMenuButton Pattern**: Already used in RecipeDetailScreen for delete option
- **Material Design 3 Dialogs**: App uses Material 3 by default (Flutter 3.16+), follow M3 dialog specifications
- **AlertDialog Pattern**: Use `showDialog<T>` with proper `Navigator.of(context).pop()` and M3 dialog constraints (max
  width 560dp)
- **Dialog Sizing**: M3 dialogs should use proper constraints and scrollable content for large content

## Implementation Plan

### Tasks

```
Task 1: Create SharedUser model for API response
  Action: CREATE
  File: mobile/lib/features/recipe/shared_user.dart
  Changes:
    - [ ] Create immutable class with email and role fields
    - [ ] Add fromJson factory constructor to parse API response  
    - [ ] Follow existing model patterns from recipe.dart

Task 2: Update RecipeDetail model to include role field
  Action: MODIFY
  File: mobile/lib/features/recipe/recipe_detail.dart
  Changes:
    - [ ] Add String role field to RecipeDetail class constructor
    - [ ] Update fromJson method to parse role from API response
    - [ ] Update toJson method to include role field
    - [ ] Follow existing model patterns in the file

Task 3: Add sharing API methods to ApiService
  Action: MODIFY
  File: mobile/lib/core/api_service.dart
  Changes:
    - [ ] Add fetchSharedUsers(String recipeId) -> Future<List<SharedUser>>
    - [ ] Add shareRecipe(String recipeId, String email) -> Future<void>
    - [ ] Add unshareRecipe(String recipeId, String email) -> Future<void>
    - [ ] Follow existing API method patterns with proper error handling
    - [ ] Use _getAuthHeaders() for authentication
    - [ ] Map to appropriate SharedUser model from JSON response

Task 4: Create RecipeSharingDialog widget
  Action: CREATE  
  File: mobile/lib/features/recipe/recipe_sharing_dialog.dart
  Changes:
    - [ ] Create StatefulWidget with recipeId parameter
    - [ ] Use Material Design 3 AlertDialog with proper constraints (max width 560dp)
    - [ ] Implement scrollable content using SingleChildScrollView for user list
    - [ ] Add email TextField with validation for adding new users
    - [ ] Display shared users with roles using ListTile pattern
    - [ ] Add unshare IconButton for EDITOR users (not OWNER)
    - [ ] Include proper loading states and error handling
    - [ ] Follow M3 design system with theme.colorScheme colors
    - [ ] Follow theme and spacing patterns from core/theme.dart
    - [ ] Use existing error display patterns from shared widgets

Task 5: Add share button to RecipeDetailScreen AppBar
  Action: MODIFY
  File: mobile/lib/features/recipe/recipe_detail_screen.dart  
  Changes:
    - [ ] Add 'share' option to existing PopupMenuButton in AppBar actions
    - [ ] Create _showSharingDialog() method to display RecipeSharingDialog
    - [ ] Handle dialog result and refresh data if needed
    - [ ] Follow existing PopupMenuButton pattern with Icon and Text

Task 6: Update delete button visibility based on role
  Action: MODIFY
  File: mobile/lib/features/recipe/recipe_detail_screen.dart
  Changes:
    - [ ] Check recipeDetail.role == 'OWNER' before showing delete option in PopupMenuButton
    - [ ] Update existing PopupMenuButton itemBuilder to conditionally include delete
    - [ ] Maintain existing delete confirmation dialog functionality

```

### Per Task Pseudocode

```dart
// Task 4: RecipeSharingDialog implementation (Material Design 3)
class RecipeSharingDialog extends StatefulWidget {
  final String recipeId;

  // Material Design 3 AlertDialog with proper constraints
  AlertDialog build(BuildContext context) {
    final theme = Theme.of(context);

    return AlertDialog(
      // M3 dialog should have max width of 560dp
      insetPadding: const EdgeInsets.symmetric(horizontal: 40.0, vertical: 24.0),
      title: Text('Share Recipe'),
      content: ConstrainedBox(
        constraints: BoxConstraints(
          maxWidth: 560, // Material 3 specification
          maxHeight: MediaQuery
              .of(context)
              .size
              .height * 0.7,
        ),
        child: SingleChildScrollView( // Scrollable content for M3
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              // Email input section
              _buildEmailInput(),
              const SizedBox(height: AppSpacing.medium),
              // Shared users list
              _buildSharedUsersList(),
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: Text('Close'),
        ),
      ],
    );
  }

  Future<void> _loadSharedUsers() async {
    setState(() => _isLoading = true);
    try {
      final users = await _apiService.fetchSharedUsers(widget.recipeId);
      setState(() {
        _sharedUsers = users;
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _error = e.toString();
        _isLoading = false;
      });
    }
  }
}

// Task 5: Share button in RecipeDetailScreen
void _showSharingDialog() async {
  await showDialog<void>(
    context: context,
    builder: (context) => RecipeSharingDialog(recipeId: widget.recipeId),
  );
  // Potentially refresh recipe data if sharing affects it
}

// Task 6: Conditional delete button
PopupMenuButton<String>
(
itemBuilder: (BuildContext context) {
final items = <PopupMenuItem<String>>[];

// Only show delete for OWNER
if (recipeDetail.role == 'OWNER') {
items.add(PopupMenuItem<String>(
value: 'delete',
child: Row(children: [
Icon(Icons.delete),
const SizedBox(width: AppSpacing.small),
Text('Delete Recipe'),
]),
));
}

// Always show share option
items.add(PopupMenuItem<String>(
value: 'share',
child: Row(children: [
Icon(Icons.share),
const SizedBox(width: AppSpacing.small),
Text('Share Recipe'),
]),
));

return items;
},
)
```

## Validation

### Syntax and Style

```bash
# Run these FIRST - fix any errors before proceeding
cd mobile
flutter analyze
# Expected: No issues found. If issues, READ the error and fix.
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
# Manual testing approach - no automated tests need to be updated:
cd mobile
flutter run
# Manual test steps:
# 1. Navigate to recipe detail screen
# 2. Verify share button appears in AppBar menu
# 3. Test sharing dialog opens and displays current shared users
# 4. Test adding user by email with validation
# 5. Test unsharing functionality with confirmation
# 6. Verify delete button only shows for OWNER role
```

## Integration Points

- **API Integration**: Uses existing backend sharing endpoints:
    - `GET /recipes/{uuid}/shared_users` - Fetch shared users list
    - `POST /recipes/{uuid}/share` - Share recipe with user (grants EDITOR access)
    - `POST /recipes/{uuid}/unshare` - Remove shared access from user
- **Model Updates**: RecipeDetail model now includes role field from API responses
- **Navigation**: No changes to routing, uses existing dialog pattern
- **State Management**: Dialog manages its own state, refreshes parent data as needed
- **Authentication**: Uses existing JWT token authentication via ApiService

## Documentation

- **Mobile UI Documentation**: `docs/mobile/ui.md` needs updating with:
    - New RecipeSharingDialog component documentation
    - Updated RecipeDetailScreen AppBar actions
    - Role-based conditional rendering patterns
- **Mobile Data Models**: `docs/mobile/mobile.md` needs updating with:
    - SharedUser model documentation
    - Updated RecipeDetail model with role field
    - New API service methods for sharing operations

## Final Validation Checklist

- [ ] Correct syntax (flutter analyze passes)
- [ ] All tests pass (flutter test passes)
- [ ] Manual test of sharing dialog successful
- [ ] Manual test of role-based delete button visibility successful
- [ ] Email validation working in sharing dialog
- [ ] Unshare confirmation dialog working
- [ ] Error cases handled gracefully (network errors, invalid email, etc.)
- [ ] Loading states display properly during API calls
- [ ] SnackBar messages are user-friendly and informative
- [ ] Theme and spacing constants used consistently
- [ ] Documentation updated if needed

**SIP Confidence Score: 9/10** - Comprehensive context provided with existing code patterns, specific API endpoints,
detailed implementation plan, and clear integration points. Backend sharing API is already implemented and documented.
Should enable one-pass implementation success with Flutter best practices.