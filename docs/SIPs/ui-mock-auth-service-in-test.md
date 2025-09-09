# SIP: UI Mock Auth Service in Test

## Goal

- Create an interface for AuthService to enable mocking in tests without Firebase dependencies
- Implement InheritedAuthService to provide AuthService down the widget tree using dependency injection
- Enable smoke test to run successfully without Firebase authentication
- Maintain existing functionality in production while making the auth system fully testable

## Context

### Documentation and References

- `docs/mobile/mobile.md` - Mobile app overview with codebase structure
- `docs/feature-requests/ui-mock-auth-service-in-test.md` - Original feature requirements
- Flutter InheritedWidget
  pattern: https://docs.flutter.dev/development/data-and-backend/state-mgmt/options#inheritedwidget--inheritedmodel
- Flutter testing patterns: https://docs.flutter.dev/cookbook/testing/unit/mocking
- Abstract interface testing
  pattern: https://medium.com/coding-with-flutter/take-your-flutter-tests-to-the-next-level-e2fb15641809

### Current Codebase Tree

```
mobile/
├── lib/
│   ├── main.dart                       # RecipAI app entry point - uses global authService
│   ├── core/
│   │   ├── routes.dart                 # Go router with authService.isAuthenticated 
│   │   ├── api_service.dart           # API service for backend communication
│   │   ├── app_config.dart            # Application configuration
│   │   └── theme.dart                 # App theme and spacing constants
│   ├── features/
│   │   └── auth/
│   │       ├── auth_service.dart      # Current Firebase AuthService (global instance)
│   │       └── login_screen.dart      # Uses global authService
│   └── features/recipe/
│       └── recipe_list_model.dart     # Shows InheritedNotifier pattern
├── test/
│   └── widget_test.dart               # Smoke test that fails due to Firebase
└── pubspec.yaml                       # Flutter dependencies with Firebase
```

### Desired Codebase Tree

```
mobile/
├── lib/
│   ├── main.dart                       # Updated to accept AuthService parameter
│   ├── core/
│   │   └── routes.dart                 # Uses InheritedAuthService.of(context)
│   ├── features/
│   │   └── auth/
│   │       ├── auth_service.dart      # Abstract AuthService interface + InheritedAuthService
│   │       ├── firebase_auth_service.dart # Firebase implementation
│   │       └── login_screen.dart      # Uses InheritedAuthService.of(context)
├── test/
│   └── widget_test.dart               # Uses MockAuthService, no Firebase
└── pubspec.yaml                       # No new dependencies needed
```

### Known Gotchas of Our Codebase and Library Quirks

- Current global `authService` instance used throughout app needs to be replaced with inherited access
- Firebase initialization in `main.dart` prevents test execution - needs conditional initialization
- `routes.dart` uses `refreshListenable: authService` which needs to use inherited service
- `InheritedNotifier<AuthService>` pattern already used for `RecipeListModel` - follow same pattern
- Auth service extends `ChangeNotifier` for state management - preserve this in interface

## Implementation Plan

### Tasks

```
Task 1: Create abstract AuthService interface
  Action: MODIFY
  File: mobile/lib/features/auth/auth_service.dart
  Changes:
    - [ ] Extract abstract class AuthService extending ChangeNotifier
    - [ ] Define interface methods: isAuthenticated, idToken, signIn, signOut, dispose
    - [ ] Create InheritedAuthService class following InheritedRecipeListModel pattern
    - [ ] Move current implementation to FirebaseAuthService class
    - [ ] Remove global authService instance

Task 2: Create Firebase implementation
  Action: CREATE
  File: mobile/lib/features/auth/firebase_auth_service.dart
  Changes:
    - [ ] Move current AuthService logic to FirebaseAuthService
    - [ ] Implement abstract AuthService interface
    - [ ] Preserve all existing Firebase/Google Sign In functionality
    - [ ] Export FirebaseAuthService for production use

Task 3: Update main app for dependency injection
  Action: MODIFY
  File: mobile/lib/main.dart
  Changes:
    - [ ] Accept AuthService parameter in RecipAIApp constructor
    - [ ] Wrap app with InheritedAuthService provider
    - [ ] Create production main() with FirebaseAuthService
    - [ ] Conditionally initialize Firebase only in production
    - [ ] Remove global authService disposal

Task 4: Update router configuration
  Action: MODIFY
  File: mobile/lib/core/routes.dart
  Changes:
    - [ ] Replace global authService with InheritedAuthService.of(context)
    - [ ] Update redirect logic to use inherited auth service
    - [ ] Ensure refreshListenable works with inherited service
    - [ ] Remove direct authService import

Task 5: Update login screen
  Action: MODIFY
  File: mobile/lib/features/auth/login_screen.dart
  Changes:
    - [ ] Replace global authService with InheritedAuthService.of(context)
    - [ ] Update ListenableBuilder to use inherited service
    - [ ] Update sign-in handler to use inherited service
    - [ ] Remove direct authService import

Task 6: Create mock auth service in test
  Action: MODIFY
  File: mobile/test/widget_test.dart
  Changes:
    - [ ] Create MockAuthService class implementing AuthService interface
    - [ ] Add controllable authentication state for testing
    - [ ] Implement all required interface methods with test-friendly behavior
    - [ ] Create test-specific main app wrapper with MockAuthService

Task 7: Fix smoke test
  Action: MODIFY
  File: mobile/test/widget_test.dart
  Changes:
    - [ ] Update test to use RecipAIApp with MockAuthService parameter
    - [ ] Remove Firebase initialization from test environment
    - [ ] Ensure test passes with mock authentication
    - [ ] Verify 'RecipAI' text is found after authentication flow
```

### Per Task Pseudocode

```dart
// Task 1: Abstract AuthService interface
abstract class AuthService extends ChangeNotifier {
  bool get isAuthenticated;
  Future<String?> get idToken;
  Future<void> signIn();
  Future<void> signOut();
  @override
  void dispose();
}

class InheritedAuthService extends InheritedNotifier<AuthService> {
  const InheritedAuthService({
    required super.notifier,
    required super.child,
  });
  
  static AuthService of(BuildContext context) {
    final result = context.dependOnInheritedWidgetOfExactType<InheritedAuthService>();
    assert(result != null, 'No InheritedAuthService found in context');
    return result!.notifier!;
  }
}

// Task 6: MockAuthService in test
class MockAuthService extends ChangeNotifier implements AuthService {
  bool _isAuthenticated;
  
  MockAuthService({bool isAuthenticated = false}) : _isAuthenticated = isAuthenticated;
  
  @override
  bool get isAuthenticated => _isAuthenticated;
  
  void setAuthenticated(bool value) {
    _isAuthenticated = value;
    notifyListeners();
  }
  
  // Implement other interface methods...
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
# Run smoke test specifically:
cd mobile
flutter test test/widget_test.dart
# Expected: Test passes without Firebase initialization
# If failing: Check MockAuthService setup and InheritedAuthService access
```

## Integration Points

- **Routes Configuration**: `core/routes.dart` must access AuthService through inherited widget instead of global
  instance
- **Authentication Flow**: All auth-dependent screens must use `InheritedAuthService.of(context)` pattern
- **State Management**: AuthService continues to extend ChangeNotifier for reactive UI updates
- **Test Environment**: Tests can run independently without Firebase initialization
- **Production Environment**: Firebase authentication remains fully functional

## Documentation

- Update `docs/mobile/mobile.md` to reflect new auth service architecture and dependency injection pattern
- Add testing section explaining MockAuthService usage
- Document InheritedAuthService access pattern for future development

## Final Validation Checklist

- [ ] Correct syntax (flutter analyze passes)
- [ ] All tests pass including smoke test
- [ ] Production app still authenticates with Firebase/Google
- [ ] Login screen works identically to current implementation
- [ ] Router redirect logic unchanged in behavior
- [ ] MockAuthService enables full test control over auth state
- [ ] No Firebase dependencies in test environment
- [ ] Error cases handled gracefully in both implementations
- [ ] InheritedAuthService provides auth service throughout widget tree
- [ ] Documentation updated to reflect new patterns

**SIP Confidence Score: 9/10** - High confidence for one-pass implementation success due to:

- Clear existing InheritedNotifier pattern to follow in codebase
- Well-defined abstract interface requirements
- Existing AuthService functionality preservation
- Simple MockAuthService without external dependencies
- Comprehensive validation approach with specific test commands