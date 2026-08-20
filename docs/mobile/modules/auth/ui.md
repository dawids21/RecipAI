# Auth — UI

## Screens

- Login Screen (`login_screen.dart`) - Welcome screen with Google Sign-In button, app branding (RecipAI logo and
  title), loading states during authentication, and error handling for sign-in failures. Takes an optional
  `DevAuthService`; when one is injected it renders a "Dev user name" field and a "Login" button **instead of** the
  Google button, since a dev-auth build never initialises Firebase. The Login button stays disabled until the field
  is non-empty, and a name outside the RFC 6750 bearer-token grammar is rejected by the repository, which the screen
  reports as "Dev user name must contain only letters, digits, and -._~+/"
- Auth Service (`auth_service.dart`) - Auth business logic over an injected `AuthRepository`, exposing
  `isAuthenticated`, `email`, `idToken`, `signIn()`, and `signOut()`
- Auth Repository (`auth_repository.dart`) - `AuthRepository` interface (`watchAuthState()`, `getIdToken()`,
  `signIn()`, `signOut()`, all in terms of `AuthUser`) plus `FirebaseAuthRepository`, the Google Sign-In
  implementation with automatic token refresh
- Dev Auth Repository (`dev_auth_repository.dart`) - Dev-only `AuthRepository` (see the `devAuthEnabled` flag in the
  Feature Flags standard); `signInAs(name)` persists the name, which is the bearer token, making the caller
  `<name>@local.test` to match the backend's `dev` profile. `signInAs()` rejects a name outside the RFC 6750
  bearer-token grammar (`[a-zA-Z0-9-._~+/]`) with an `ArgumentError`, and the constructor clears a persisted name
  that fails the same check, so the token handed to every API call is always legal. Its `AuthRepository.signIn()` is
  unsupported: the dev identity comes from the caller
- Dev Auth Service (`dev_auth_service.dart`) - Forwards the typed name to `DevAuthRepository.signInAs()`; registered
  by `setupAuth()` only when `devAuthEnabled` is set, so its presence is what the Login Screen renders against

## Flow

#### Authentication Flow

1. **App Launch** → Authentication check:
    - **If unauthenticated** → Login Screen (`/login`)
    - **If authenticated** → Main Screen (`/`) showing Recipes tab
2. **Login Screen → Google Sign-In Tap** (default build) → Authentication process → Main Screen (`/`)
3. **Login Screen → Login Tap** (dev-auth build) → name persisted as the bearer token → Main Screen (`/`)
4. **Main Screen → Logout Tap** → Confirmation dialog → Sign out → Login Screen (`/login`)
