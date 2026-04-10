# Mobile Architecture

### Repository-Service-View Three-Layer Architecture

All features follow a strict three-layer architecture with unidirectional data flow:

- **Repository** (`*_repository.dart`): Stateless data access — HTTP calls, local storage. Returns raw types. No
  business logic. No state.
- **Service** (`*_service.dart`): Application state with `ValueNotifier<AsyncValue<T>>`. Coordinates repositories,
  manages side effects.
- **View** (`*_screen.dart`): UI rendering. Receives services via constructor. Uses `ValueListenableBuilder` for
  reactive rebuilds.

**Rules**:

- Views cannot access repositories directly — only through services
- Repositories have no dependencies on services or views
- Services cannot access other services' private state — cross-service coordination goes through repositories or
  explicit method calls on the other service's public API
- Data flows unidirectionally: Repository → Service → View

### Feature-Based Directory Structure

Code is organized by feature under `lib/features/`. Each feature directory contains all layers flat (no sub-folders).
Shared/reusable code goes in `lib/core/` or `lib/shared/`.

```
lib/
  features/
    recipe/
      recipe_repository.dart
      recipe_list_service.dart
      recipe_detail_service.dart
      recipe_list_screen.dart
      recipe_setup.dart
    planning/
      ...
  core/
    routes.dart
    theme.dart
    get_it.dart
  shared/
    ...
```

### File Naming Conventions

- Repositories: `*_repository.dart`
- Services: `*_service.dart`
- Screens: `*_screen.dart`
- DI setup: `*_setup.dart`
- Models: `*_model.dart` or just `*.dart` (snake_case)
