# Mobile Architecture

### Repository-Service-View Three-Layer Architecture

All features follow a strict three-layer architecture with unidirectional data flow:

- **Repository** (`*_repository.dart`): Data access — HTTP calls, local storage (DAOs). Returns raw types to
  services. No business logic. May hold a persistence handle (an `http.Client`, a DAO) — but must not depend on
  services or views.
- **Service** (`*_service.dart`): Owns application state — any state beyond an individual widget's screen state —
  and exposes methods that mutate it. Coordinates repositories, manages side effects, and may communicate with other
  services through their public API. Async-loaded state exposed to views is typically held as
  `ValueNotifier<AsyncValue<T>>` (see State Management), but a service is free to hold whatever state shape the domain
  needs and to expose it however suits — `AsyncValue` is a convention, not a requirement. A service may also own a
  local cache and coordinate persistence directly: the shopping-list item **store service** owns the item cache,
  per-list `ValueNotifier<List<T>>`, and a per-list lock, serialising every read-modify-write through one boundary
  (ADR-0004).
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
