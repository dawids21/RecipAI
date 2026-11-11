# Auth Module

Signs the user in and supplies the bearer token every repository sends. The default build uses Firebase
Authentication with Google Sign-In; a `devAuthEnabled` build (see the Feature Flags standard) signs in by name against
a `dev`-profile backend instead (see `docs/project/local-development.md`).

## Boundary

- **Backend endpoints**: none. The token from `AuthService.idToken` is sent as `Authorization: Bearer` by every
  feature's repository.
- **Uses**: no other feature.
- **Used by**: every feature's services (token, `email`), `limits` (loads quotas on sign-in), `core` (router
  redirect), and the screens that pass the current user's email to `sharing`.

## File Map

```
mobile/lib/features/auth/
├── auth_repository.dart        # AuthRepository interface and FirebaseAuthRepository (Google Sign-In)
├── auth_service.dart           # Session state over an AuthRepository (see Services and State)
├── auth_setup.dart             # Picks the repository implementation (see Services and State)
├── auth_user.dart              # Provider-agnostic signed-in user, decoupled from any auth SDK type
├── dev_auth_repository.dart    # Dev-only AuthRepository backed by a name in PreferencesService
├── dev_auth_service.dart       # Dev sign-in entry point for the Login Screen
└── login_screen.dart
```

## Services and State

- **`AuthService`** — app-lifetime singleton. Holds `isAuthenticated` and `email`, both driven only by the
  repository's auth-state stream; `signIn()` and `signOut()` return before the state flips, and listeners react to the
  stream. `idToken` is fetched from the repository on every call, so Firebase's token refresh is never cached around.
- **Repository choice** (`setupAuth`): an injected repository (tests) wins; otherwise a `devAuthEnabled` build
  registers `DevAuthRepository` plus a `DevAuthService`, and any other build registers `FirebaseAuthRepository`. The
  Login Screen renders the dev name field instead of the Google button exactly when a `DevAuthService` is registered.
- **Dev identity.** The persisted name *is* the bearer token, and the user's email is `<name>@local.test`, matching
  the backend's `dev` profile. A name outside the RFC 6750 bearer-token grammar (`[a-zA-Z0-9-._~+/]`) is refused on
  sign-in, and one already persisted is cleared on start-up, so the token sent to the backend is always legal. The
  persisted name survives a restart (the session is restored on launch) and is cleared on sign-out.
