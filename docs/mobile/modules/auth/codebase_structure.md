# Auth — Codebase Structure

```
mobile/lib/features/auth/
├── auth_repository.dart        # Abstract auth repository interface with FirebaseAuthRepository implementation
├── auth_service.dart           # Auth business logic with ValueNotifier for state management
├── auth_setup.dart             # Dependency injection setup for auth module, incl. the dev-auth build's registrations
├── auth_user.dart              # Provider-agnostic authenticated user, decoupled from any auth SDK type
├── dev_auth_repository.dart    # Dev-only AuthRepository backed by a name persisted in PreferencesService
├── dev_auth_service.dart       # Dev sign-in entry point for the Login Screen, registered only in a dev-auth build
└── login_screen.dart           # Login UI with constructor injection
```
