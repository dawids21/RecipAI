# Auth — UI

## Screens

- Login Screen (`login_screen.dart`) - Welcome screen with Google Sign-In button, app branding (RecipAI logo and
  title), loading states during authentication, and error handling for sign-in failures
- Auth Service (`auth_service.dart`) - Abstract service interface defining authentication contracts with
  `isAuthenticated`, `email`, `idToken`, `signIn()`, and `signOut()` methods
- Firebase Auth Service (`firebase_auth_service.dart`) - Firebase implementation with Google Sign-In integration,
  user state management, and automatic token refresh

## Flow

#### Authentication Flow

1. **App Launch** → Authentication check:
    - **If unauthenticated** → Login Screen (`/login`)
    - **If authenticated** → Main Screen (`/`) showing Recipes tab
2. **Login Screen → Google Sign-In Tap** → Authentication process → Main Screen (`/`)
3. **Main Screen → Logout Tap** → Confirmation dialog → Sign out → Login Screen (`/login`)
