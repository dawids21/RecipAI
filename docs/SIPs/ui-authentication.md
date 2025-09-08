# SIP - UI Authentication with Firebase

## Goal

- Implement user authentication for the Flutter app using Firebase Authentication with Google Sign-In
- Show login screen when user is not authenticated using Go Router guarded routes
- Navigate to recipe list screen after successful login
- Store authentication token using ChangeNotifier pattern with global object (no Provider)
- Use authentication token in API requests to backend
- Add logout action to app bar on recipe list screen

### User-visible behavior

- Users see login screen immediately if not authenticated
- After successful Google Sign-In, users are navigated to recipe list
- Users can log out from the app bar on recipe list screen
- All API requests include authentication headers automatically
- Users remain logged in across app restarts (Firebase Auth persistence)

### Technical requirements

- Firebase Authentication integration with existing setup
- Go Router redirect/guard implementation for protected routes
- Global AuthService object (ChangeNotifier without Provider)
- API service integration with authentication headers
- UI components for login screen and logout functionality

### Success criteria

- App shows login screen for unauthenticated users
- Successful login redirects to recipe list screen
- All API calls include proper authentication headers
- Users can log out and return to login screen
- Authentication state persists across app restarts
- All existing functionality remains intact

## Context

### Documentation and References

- **Feature Requirements**: `/home/dawid/Projects/RecipAI/docs/feature-requests/ui-authentication.md`
- **Example Implementation**: `/home/dawid/Projects/RecipAI/docs/examples/ui-authentication.dart` - Shows global
  AuthChangeNotifier pattern
- **Mobile App Overview**: `/home/dawid/Projects/RecipAI/docs/mobile/mobile.md`
- **Mobile UI Documentation**: `/home/dawid/Projects/RecipAI/docs/mobile/ui.md`
- **Backend API Documentation**: `/home/dawid/Projects/RecipAI/docs/backend/api.md` - All recipe endpoints require
  authentication
- **Firebase Auth Documentation**: https://firebase.google.com/docs/auth/flutter/start
- **Go Router Guards Best Practices**: https://dev.to/dinko7/guarding-routes-in-flutter-with-gorouter-and-riverpod-40h4
- **Flutter Firebase Auth Login
  **: https://medium.com/@dev.lens/flutter-google-sign-in-using-firebase-authentication-step-by-step-ef2ddfb84a2c

### Current Codebase Tree

```
mobile/
├── lib/
│   ├── main.dart                       # RecipAI app entry point, Firebase already initialized
│   ├── core/
│   │   ├── routes.dart                 # Go router with AppRoute enum, needs auth guards
│   │   ├── api_service.dart           # API service, needs auth headers integration
│   │   ├── app_config.dart            # Application configuration
│   │   └── theme.dart                 # App theme and spacing constants
│   ├── shared/
│   │   ├── loading_widget.dart        # Reusable loading indicator
│   │   ├── api_error_widget.dart      # API error display widget
│   │   ├── error_message_widget.dart  # General error message widget
│   │   └── error_icon.dart           # Error icon widget
│   └── features/
│       ├── recipe/                     # Recipe feature with screens that need protection
│       └── extraction/                 # Extraction feature with screens that need protection
├── lib/firebase_options.dart          # Firebase config (Android configured)
└── pubspec.yaml                       # Dependencies: firebase_core, firebase_auth, google_sign_in
```

### Desired Codebase Tree

```
mobile/
├── lib/
│   ├── main.dart                       # Initialize global auth service
│   ├── core/
│   │   ├── routes.dart                 # Updated with auth guards and login route
│   │   ├── api_service.dart           # Updated with auth headers
│   │   ├── app_config.dart            # Application configuration
│   │   └── theme.dart                 # App theme and spacing constants
│   ├── shared/
│   │   ├── loading_widget.dart        # Reusable loading indicator
│   │   ├── api_error_widget.dart      # API error display widget
│   │   ├── error_message_widget.dart  # General error message widget
│   │   └── error_icon.dart           # Error icon widget
│   ├── features/
│   │   ├── auth/                       # NEW: Authentication feature
│   │   │   ├── auth_service.dart      # Global ChangeNotifier for auth state
│   │   │   └── login_screen.dart      # Login screen with Google Sign-In
│   │   ├── recipe/                     # Recipe feature (protected routes)
│   │   └── extraction/                 # Extraction feature (protected routes)
│   └── lib/firebase_options.dart      # Firebase config (Android configured)
└── pubspec.yaml                       # Dependencies already available
```

### Known Gotchas of Our Codebase and Library Quirks

- **Firebase Already Initialized**: Firebase Core and Auth are already set up in main.dart with proper initialization
- **Go Router Enum Pattern**: Must follow existing AppRoute enum pattern for type-safe navigation
- **Theme Access**: Use `final theme = Theme.of(context);` pattern for consistent theme access
- **Feature Organization**: All auth-related files should be in `features/auth/` directory following codebase patterns
- **API Service Singleton**: ApiService uses static methods, need to add auth headers to all HTTP requests
- **Firebase Auth Persistence**: Firebase Auth automatically persists authentication state
- **Google Sign-In Initialization**: Already initialized in main.dart
- **Material Design 3**: App uses Material Design 3 with ColorScheme.fromSeed pattern
- **Recipe List Model**: Uses InheritedWidget pattern for state management in recipes
- **Global Object Pattern**: Use global AuthService instance (no Provider), similar to how example shows global
  AuthChangeNotifier

## Implementation Plan

### Tasks

```
Task 1: Create Global Authentication Service with ChangeNotifier
  Action: CREATE
  File: mobile/lib/features/auth/auth_service.dart
  Changes:
    - [ ] Create AuthService extending ChangeNotifier
    - [ ] Create global authService instance (final authService = AuthService();)
    - [ ] Listen to Firebase Auth userChanges() stream (most comprehensive)
    - [ ] Provide authentication state and token access methods
    - [ ] Handle Google Sign-In authentication flow
    - [ ] Handle sign-out functionality
    - [ ] Follow pattern from docs/examples/ui-authentication.dart

Task 2: Create Login Screen with Google Sign-In
  Action: CREATE
  File: mobile/lib/features/auth/login_screen.dart
  Changes:
    - [ ] Create login screen following existing screen patterns
    - [ ] Use Material Design 3 theme system from core/theme.dart
    - [ ] Implement Google Sign-In button with proper error handling
    - [ ] Add loading states and error messages using shared widgets
    - [ ] Use ListenableBuilder to listen to global authService
    - [ ] Follow UI patterns from existing screens in recipe feature

Task 3: Update Go Router with Authentication Guards
  Action: MODIFY
  File: mobile/lib/core/routes.dart
  Changes:
    - [ ] Import global authService from auth/auth_service.dart
    - [ ] Add login route to AppRoute enum
    - [ ] Add global redirect function for authentication checking
    - [ ] Implement refreshListenable with global authService
    - [ ] Protect existing routes (recipes, extraction) with auth guards
    - [ ] Follow Go Router guard patterns from research

Task 4: Update API Service with Authentication Headers
  Action: MODIFY
  File: mobile/lib/core/api_service.dart
  Changes:
    - [ ] Import global authService from auth/auth_service.dart
    - [ ] Add method to get auth headers with Firebase ID token
    - [ ] Update all HTTP requests (GET, POST, PUT, DELETE) to include auth headers
    - [ ] Handle token refresh and authentication errors
    - [ ] Maintain existing error handling patterns

Task 5: Initialize Global Auth Service in Main
  Action: MODIFY
  File: mobile/lib/main.dart
  Changes:
    - [ ] Import AuthService and initialize the global instance
    - [ ] No Provider wrapper needed - just initialize global object
    - [ ] Ensure proper disposal in app lifecycle if needed
    - [ ] Keep existing InheritedRecipeListModel pattern unchanged

Task 6: Add Logout Functionality to Recipe List Screen
  Action: MODIFY
  File: mobile/lib/features/recipe/recipe_list_screen.dart
  Changes:
    - [ ] Import global authService from auth/auth_service.dart
    - [ ] Add logout action to existing app bar
    - [ ] Use existing app bar pattern and theme system
    - [ ] Handle logout confirmation if needed
    - [ ] Call authService.signOut() directly (no Provider needed)
```

### Per Task Pseudocode

```
# Task 1 - Global AuthService Pseudocode
class AuthService extends ChangeNotifier {
  User? _currentUser;
  
  AuthService() {
    // Listen to Firebase Auth changes
    FirebaseAuth.instance.userChanges().listen((user) => {
      _currentUser = user;
      notifyListeners();
    });
  }
  
  bool get isAuthenticated => _currentUser != null;
  
  Future<String?> get idToken => _currentUser?.getIdToken();
  
  Future<void> signInWithGoogle() {
    // Implement Google Sign-In flow
    // Handle errors and notify listeners
  }
  
  Future<void> signOut() {
    // Sign out from Firebase and Google
    // notify listeners
  }
}

// Global instance
final authService = AuthService();

# Task 2 - Login Screen with ListenableBuilder
class LoginScreen extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: ListenableBuilder(
        listenable: authService,
        builder: (context, child) {
          // UI that reacts to auth state changes
          // Use authService.isAuthenticated, etc.
        },
      ),
    );
  }
}

# Task 3 - Router Guards Pseudocode
final GoRouter appRouter = GoRouter(
  refreshListenable: authService, // Global authService instance
  redirect: (context, state) async {
    final isAuthenticated = authService.isAuthenticated;
    final isLoginRoute = state.matchedLocation == '/login';
    
    if (!isAuthenticated && !isLoginRoute) return '/login';
    if (isAuthenticated && isLoginRoute) return '/recipes';
    return null; // No redirect needed
  },
  routes: [
    // Add login route
    // Update existing routes (no changes needed, global redirect handles protection)
  ]
);

# Task 4 - API Headers Pseudocode
static Future<Map<String, String>> _getAuthHeaders() async {
  final token = await authService.idToken;
  return {
    'Content-Type': 'application/json',
    if (token != null) 'Authorization': 'Bearer $token',
  };
}

static Future<List<Recipe>> fetchRecipes() async {
  final headers = await _getAuthHeaders();
  final response = await _client.get(uri, headers: headers);
  // Rest of implementation unchanged
}

# Task 6 - Logout in Recipe List Screen
class RecipeListScreen extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        actions: [
          IconButton(
            icon: Icon(Icons.logout),
            onPressed: () => authService.signOut(), // Direct call to global object
          ),
        ],
      ),
      // Rest of UI unchanged
    );
  }
}
```

## Validation

### Syntax and Style

```bash
# Run from mobile directory - fix any errors before proceeding
cd /home/dawid/Projects/RecipAI/mobile
flutter analyze

# Expected: No issues found!
```

### Unit Tests

```bash
# Run from mobile directory - iterate until passing
cd /home/dawid/Projects/RecipAI/mobile
flutter test

# If failing: Read error, understand root cause, fix code, re-run
```

### Integration Tests

```bash
# Manual testing approach since no integration tests exist
cd /home/dawid/Projects/RecipAI/mobile
flutter run

# Test scenarios:
# 1. App opens to login screen when not authenticated
# 2. Google Sign-In works and navigates to recipe list
# 3. Recipe list loads (API calls include auth headers)
# 4. Logout button works and returns to login
# 5. Authentication persists across app restarts
```

## Integration Points

- **Firebase Authentication**: Already configured in firebase_options.dart for Android
- **Google Sign-In**: Already initialized in main.dart
- **API Backend**: All recipe endpoints (GET /recipes, POST /recipes, etc.) require authentication per backend API docs
- **Go Router Navigation**: Integration with existing routing system and AppRoute enum pattern
- **Global State Management**: Simple global object pattern, no Provider dependency

## Documentation

- **Mobile App Overview** (`docs/mobile/mobile.md`): Add authentication feature to Features section
- **Mobile UI Documentation** (`docs/mobile/ui.md`): Add login screen and auth flow to navigation section
- **Mobile CLAUDE.md** (`mobile/CLAUDE.md`): Add global authentication service pattern

## Final Validation Checklist

- [ ] Correct syntax with `flutter analyze`
- [ ] All tests pass with `flutter test`
- [ ] Manual test: Login screen appears for unauthenticated users
- [ ] Manual test: Google Sign-In works and navigates to recipe list
- [ ] Manual test: Recipe list loads (API calls authenticated)
- [ ] Manual test: Logout works and returns to login
- [ ] Manual test: Authentication persists across app restarts
- [ ] Error cases handled gracefully (network errors, sign-in failures)
- [ ] Loading states displayed during authentication operations
- [ ] Logs are informative but not verbose (no sensitive token logging)
- [ ] Documentation updated if needed

## Score: 9/10

**Confidence Level for One-Pass Implementation Success: 9/10**

**Reasoning:**

- **High Score Factors:**
    - Firebase and dependencies already properly configured and initialized
    - Clear existing patterns in codebase to follow (AppRoute enum, theme system, feature organization)
    - Example in docs/examples/ui-authentication.dart shows exact global AuthChangeNotifier pattern
    - Detailed research on Go Router auth guards and ChangeNotifier patterns
    - All API endpoints clearly documented as requiring authentication
    - Global object pattern is simpler than Provider integration
    - ListenableBuilder pattern matches example implementation

- **Risk Factors (preventing 10/10):**
    - Go Router redirect logic can be tricky to get right on first implementation
    - Firebase Auth token refresh handling might need iteration

**Mitigation:**
The comprehensive context provided, existing working Firebase setup, global object pattern from example, and detailed
pseudocode should enable successful one-pass implementation. The global AuthService approach simplifies state management
compared to Provider pattern.