# Auth — Codebase Structure

```
mobile/lib/features/auth/
├── auth_repository.dart    # Abstract auth repository interface with FirebaseAuthRepository implementation
├── auth_service.dart       # Auth business logic with ValueNotifier for state management
├── auth_setup.dart         # Dependency injection setup for auth module
└── login_screen.dart       # Login UI with constructor injection
```
