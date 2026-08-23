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
- **View** (`*_screen.dart`): UI rendering. Receives services via constructor (see Widget Inputs for how far down
  the widget tree a service should travel). Uses `ValueListenableBuilder` for reactive rebuilds.

**Rules**:

- Views cannot access repositories directly — only through services
- Repositories have no dependencies on services or views
- Services cannot access other services' private state — cross-service coordination goes through repositories or
  explicit method calls on the other service's public API
- Data flows unidirectionally: Repository → Service → View

### Widget Inputs: The Narrowest Thing That Works

A widget's constructor should ask for what the widget actually uses, not for the object that happens to hold it.

- A widget that **drives** a service — calls its methods, triggers a load, owns an action — takes the service.
  Screens do, and so do the dialogs, FABs and form widgets that own one interaction
  (`ShoppingListCreateDialog` loads the usage and creates the list, so it takes `ShoppingListListService`).
- A widget that only **renders or gates on** a value takes the value itself — a `ValueListenable<T>` when it has to
  rebuild, a plain `T` when it does not, plus a callback for anything it triggers. It never takes the service the
  value came from.

```dart
// Correct — LimitGate depends on one cap, so it says so
class LimitGate extends StatelessWidget {
  final ValueListenable<AsyncValue<LimitUsage>> usage;
  final ValueListenable<LimitCap?>? cap;
  ...
}
LimitGate(
  usage: widget.recipeListService.recipeUsage,
  cap: widget.limitsService.capFor(LimitResources.recipe),
  builder: ...,
);

// Wrong — takes the whole service plus a key to look the value up with
class LimitGate extends StatelessWidget {
  final LimitsService? limitsService;
  final String resource;
  ...
}
```

Why: a widget holding a service can reach anything on it, so its real dependencies are invisible both at the call
site and in tests, and the caller has to have the service even when one number would do. A widget taking
`ValueListenable<LimitCap?>` states its dependency in its signature and can be driven from a bare `ValueNotifier`.

When a display-only widget would need several members of one service, that is the signal to either keep the
composition in the parent that already owns the service, or have the service expose a narrower view — as
`LimitsService.capFor(resource)` hands out one resource's cap instead of the whole caps map.

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
