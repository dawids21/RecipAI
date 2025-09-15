# SIP: UI Refactor - Sharing Feature

## Goal

- Fix incorrect usage of ApiService in didChangeDependencies where data fetching occurs instead of just service
  caching (except RecipeSharingDialog which is already correct)
- Replace isAuthenticated boolean flag in AuthService with String? email field, maintaining backward compatibility
- Remove unshare button for current user in recipe sharing dialog to prevent self-unsharing
- Update documentation to reflect these architectural and UI improvements
- Ensure all changes follow existing Flutter patterns and maintain code consistency

## Context

### Documentation and References

- `docs/mobile/mobile.md` - Mobile app overview with current usage patterns and service access methods
- `docs/mobile/ui.md` - UI components overview including RecipeSharingDialog description
- `mobile/lib/features/auth/auth_service.dart` - Abstract AuthService interface that needs modification
- `mobile/lib/features/auth/firebase_auth_service.dart` - Concrete implementation requiring email support
- `mobile/lib/features/recipe/recipe_sharing_dialog.dart` - Dialog that needs AuthService integration for self-unshare
  prevention
- `mobile/lib/features/recipe/recipe_detail_screen.dart` - Screen with data fetching in didChangeDependencies
- `mobile/lib/features/recipe/edit_recipe_screen.dart` - Screen with data fetching in didChangeDependencies
- Flutter documentation: https://api.flutter.dev/flutter/widgets/State/didChangeDependencies.html -
  didChangeDependencies should not perform expensive operations
- Firebase Auth documentation: https://firebase.google.com/docs/reference/js/v8/firebase.User - User object contains
  email property

### Current Codebase Tree

```
mobile/
├── lib/
│   ├── main.dart                           # App entry point with DI setup
│   ├── core/
│   │   ├── api_service.dart               # API service with InheritedApiService
│   │   ├── app_config.dart                # Application configuration
│   │   ├── routes.dart                    # Go router configuration
│   │   └── theme.dart                     # App theme and spacing constants
│   ├── features/
│   │   ├── auth/
│   │   │   ├── auth_service.dart          # Abstract AuthService (needs email support)
│   │   │   ├── firebase_auth_service.dart # Firebase implementation (needs email getter)
│   │   │   └── login_screen.dart          # Login screen
│   │   ├── recipe/
│   │   │   ├── recipe_sharing_dialog.dart # Needs AuthService for self-unshare prevention
│   │   │   ├── recipe_detail_screen.dart  # ISSUE: Data fetch in didChangeDependencies
│   │   │   ├── edit_recipe_screen.dart    # ISSUE: Data fetch in didChangeDependencies
│   │   │   ├── shared_user.dart           # SharedUser data model
│   │   │   └── [other recipe files]       # Other recipe-related files
│   │   └── extraction/
│   │       └── [extraction files]         # Extraction feature files
│   └── shared/
│       ├── user_role.dart                 # UserRole enum
│       └── [other shared widgets]         # Other shared components
└── test/
    └── widget_test.dart                   # Basic widget tests
```

### Desired Codebase Tree

```
mobile/
├── lib/
│   ├── main.dart                           # Unchanged
│   ├── core/
│   │   ├── api_service.dart               # Unchanged
│   │   ├── app_config.dart                # Unchanged  
│   │   ├── routes.dart                    # Unchanged
│   │   └── theme.dart                     # Unchanged
│   ├── features/
│   │   ├── auth/
│   │   │   ├── auth_service.dart          # MODIFIED: Add String? email getter
│   │   │   ├── firebase_auth_service.dart # MODIFIED: Implement email getter
│   │   │   └── login_screen.dart          # Unchanged
│   │   ├── recipe/
│   │   │   ├── recipe_sharing_dialog.dart # MODIFIED: Add AuthService + hide self-unshare
│   │   │   ├── recipe_detail_screen.dart  # MODIFIED: Fix didChangeDependencies pattern
│   │   │   ├── edit_recipe_screen.dart    # MODIFIED: Fix didChangeDependencies pattern
│   │   │   ├── shared_user.dart           # Unchanged
│   │   │   └── [other recipe files]       # Unchanged
│   │   └── extraction/
│   │       └── [extraction files]         # Unchanged
│   └── shared/
│       ├── user_role.dart                 # Unchanged
│       └── [other shared widgets]         # Unchanged
└── test/
    └── widget_test.dart                   # Unchanged
```

### Known Gotchas of Our Codebase and Library Quirks

- **didChangeDependencies lifecycle**: Can be called multiple times during widget lifecycle, so expensive operations
  like API calls should be avoided
- **InheritedWidget pattern**: Services must be accessed using `InheritedApiService.of(context)` and cached as late
  fields
- **Firebase User email**: Firebase User.email can be null even when authenticated (e.g., anonymous auth), so proper
  null handling is required
- **UserRole enum**: Already exists with owner/editor roles - owners cannot be unshared, only editors can be removed
- **Material Design 3**: Consistent with existing theming using Theme.of(context) pattern
- **Flutter state management**: setState triggers rebuilds, so careful timing of state updates is needed

## Implementation Plan

### Tasks

```
Task 1: Update AuthService interface to support email access
  Action: MODIFY
  File: mobile/lib/features/auth/auth_service.dart
  Changes:
    - [ ] Add abstract String? get email; getter to interface
    - [ ] Keep existing bool get isAuthenticated; getter for backward compatibility
    - [ ] Maintain existing signIn(), signOut(), and idToken getters
    - [ ] Follow existing abstract class pattern

Task 2: Implement email support in FirebaseAuthService  
  Action: MODIFY
  File: mobile/lib/features/auth/firebase_auth_service.dart
  Changes:
    - [ ] Implement String? get email => _currentUser?.email; getter
    - [ ] Ensure null safety for cases where _currentUser is null
    - [ ] Keep existing bool get isAuthenticated => _currentUser != null; implementation
    - [ ] Follow existing service patterns and error handling

Task 3: Update RecipeSharingDialog to prevent self-unsharing
  Action: MODIFY  
  File: mobile/lib/features/recipe/recipe_sharing_dialog.dart
  Changes:
    - [ ] Keep existing didChangeDependencies pattern (already correct with _initSharedUsers flag)
    - [ ] Add late AuthService _authService; field for current user access
    - [ ] Cache AuthService in didChangeDependencies (_authService = InheritedAuthService.of(context))
    - [ ] Hide unshare button when user.email matches authenticated user email
    - [ ] Update _buildSharedUsersList method to conditionally show unshare button
    - [ ] Handle null email cases gracefully

Task 4: Fix RecipeDetailScreen data fetching pattern
  Action: MODIFY
  File: mobile/lib/features/recipe/recipe_detail_screen.dart  
  Changes:
    - [ ] Add boolean flag to prevent repeated data fetching (follow RecipeSharingDialog pattern)
    - [ ] Keep _apiService caching in didChangeDependencies
    - [ ] Wrap data fetching in flag check similar to _initSharedUsers pattern
    - [ ] Follow existing error handling and loading patterns
    - [ ] Ensure no breaking changes to existing functionality

Task 5: Fix EditRecipeScreen data fetching pattern
  Action: MODIFY
  File: mobile/lib/features/recipe/edit_recipe_screen.dart
  Changes:
    - [ ] Add boolean flag to prevent repeated data fetching (follow RecipeSharingDialog pattern)
    - [ ] Keep _apiService caching in didChangeDependencies
    - [ ] Wrap data fetching in flag check similar to _initSharedUsers pattern
    - [ ] Follow existing error handling and loading patterns
    - [ ] Maintain existing form and navigation behavior

Task 6: Update mobile UI documentation
  Action: MODIFY
  File: docs/mobile/ui.md
  Changes:
    - [ ] Update Recipe Sharing Dialog description to reflect self-unshare prevention
    - [ ] Update Auth Service description to mention email support
    - [ ] Keep existing formatting and style consistency

Task 7: Update mobile architecture documentation  
  Action: MODIFY
  File: docs/mobile/mobile.md
  Changes:
    - [ ] Update usage patterns section to clarify correct didChangeDependencies usage
    - [ ] Maintain existing code example formatting
```

### Per Task Pseudocode

```dart
// Task 1 & 2: AuthService email support
abstract class AuthService {
  bool get isAuthenticated;

  String? get email; // New getter
// ... existing methods
}

class FirebaseAuthService extends AuthService {
  @override
  String? get email => _currentUser?.email; // New implementation
// ... existing implementation
}

// Task 3: RecipeSharingDialog fixes  
class _RecipeSharingDialogState extends State<RecipeSharingDialog> {
  late ApiService _apiService;
  late AuthService _authService; // New field
  late Future<List<SharedUser>> futureSharedUsers;
  bool _initSharedUsers = false; // Existing flag pattern

  @override
  void didChangeDependencies() {
    _apiService = InheritedApiService.of(context);
    _authService = InheritedAuthService.of(context); // Cache auth service
    if (!_initSharedUsers) {
      futureSharedUsers = _apiService.fetchSharedUsers(widget.recipeId);
      _initSharedUsers = true;
    }
    super.didChangeDependencies();
  }

  Widget _buildSharedUsersList(List<SharedUser> sharedUsers) {
    final currentUserEmail = _authService.email;
    return Column(
      children: [
        ...sharedUsers.map((user) =>
            ListTile(
              title: Text(user.email),
              subtitle: Text(user.role.displayName),
              trailing: (user.role == UserRole.editor &&
                  currentUserEmail != null &&
                  user.email != currentUserEmail) // Hide for current user
                  ? IconButton(
                onPressed: () => _unshareRecipe(user.email),
                icon: const Icon(Icons.remove_circle_outline),
              )
                  : null,
            )),
      ],
    );
  }
}

// Task 4 & 5: Screen data fetching fixes (follow RecipeSharingDialog pattern)
class _RecipeDetailScreenState extends State<RecipeDetailScreen> {
  late ApiService _apiService;
  late Future<RecipeDetail> futureRecipeDetail;
  bool _initRecipeDetail = false; // New flag like _initSharedUsers

  @override
  void didChangeDependencies() {
    _apiService = InheritedApiService.of(context);
    if (!_initRecipeDetail) {
      futureRecipeDetail = _apiService.fetchRecipeDetail(widget.recipeId);
      _initRecipeDetail = true;
    }
    super.didChangeDependencies();
  }
}

class _EditRecipeScreenState extends State<EditRecipeScreen> {
  late ApiService _apiService;
  late Future<RecipeDetail> futureRecipeDetail;
  bool _initRecipeDetail = false; // New flag like _initSharedUsers

  @override
  void didChangeDependencies() {
    _apiService = InheritedApiService.of(context);
    if (!_initRecipeDetail) {
      futureRecipeDetail = _apiService.fetchRecipeDetail(widget.recipeId);
      _initRecipeDetail = true;
    }
    super.didChangeDependencies();
  }
}
```

## Validation

### Syntax and Style

```bash
# Run these FIRST - fix any errors before proceeding
flutter analyze

# Expected: No issues found! If errors, READ the error and fix.
```

### Unit Tests

```bash
# Run and iterate until passing:
flutter test
# If failing: Read error, understand root cause, fix code, re-run (never mock to pass)
```

### Manual Testing

```bash
# Build and run the app to test functionality:
flutter run
# Test scenarios:
# 1. Open RecipeSharingDialog and verify no unshare button for current user
# 2. Verify screens load properly without didChangeDependencies data fetching
# 3. Verify AuthService email getter returns correct email
# 4. Verify no performance issues from repeated service access
```

## Integration Points

- No changes to API endpoints or backend integration
- No changes to database schema or data models
- AuthService interface changes are backward compatible (only additions)
- UI changes are isolated to RecipeSharingDialog unshare button visibility
- No changes to navigation flow or route structure

## Documentation

- `docs/mobile/ui.md` - Update RecipeSharingDialog and AuthService descriptions
- `docs/mobile/mobile.md` - Update usage patterns and add didChangeDependencies best practices
- No changes needed to `docs/prd.md` or backend documentation
- No changes needed to `CLAUDE.md` files

## Final Validation Checklist

- [ ] Correct syntax (flutter analyze passes)
- [ ] Correct style (flutter analyze passes)
- [ ] All tests pass (flutter test passes)
- [ ] Manual test successful (app runs and sharing dialog works correctly)
- [ ] Error cases handled gracefully (null email, service access failures)
- [ ] Logs are informative but not verbose (existing logging patterns maintained)
- [ ] Documentation updated if needed (mobile docs reflect changes)
- [ ] AuthService email getter works correctly
- [ ] Current user cannot unshare themselves
- [ ] Data fetching in didChangeDependencies is properly protected with boolean flags
- [ ] Service caching still works properly in didChangeDependencies
- [ ] No breaking changes to existing functionality
- [ ] Performance improved (no repeated API calls from didChangeDependencies)

**SIP Confidence Score: 9/10** - High confidence for one-pass implementation success due to comprehensive context, clear
patterns to follow, specific file locations, detailed pseudocode, and well-defined validation steps. All screens will
follow the same proven boolean flag pattern already working in RecipeSharingDialog, ensuring consistency and
reliability.