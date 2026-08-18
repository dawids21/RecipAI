# System Architecture

## Overview

RecipAI is a monorepo containing two independent applications that communicate over HTTP/REST:

- **Backend** — Spring Boot REST API, feature-driven modular architecture
- **Mobile** — Flutter Android app, three-layer architecture split by feature

Authentication uses OAuth2/JWT: the mobile app authenticates via Firebase (Google Sign-In), and the backend validates JWT tokens as an OAuth2 Resource Server.

---

## Backend Architecture

**Pattern**: Feature-driven modular monolith with layered internals per feature

Code is organized by feature (not by layer). Each feature package contains its own controllers, services, repositories, entities, DTOs, and exceptions. Internal classes use package-private visibility; cross-feature access goes through explicit facades.

### Feature Modules

- **`recipes`** — manages user-scoped recipe CRUD with role-based sharing (OWNER/EDITOR), optional collection assignment, collection-based access control, filtering by collection or unassigned status, image management (upload, reorder, delete), source URLs; publishes a `RecipeDeleted` event when a recipe is deleted
- **`recipes.images`** — manages recipe image storage and retrieval with S3 integration, automatic thumbnail generation, and presigned URL generation (maximum 2 images per recipe)
- **`recipes.collections`** — manages recipes collections with user-based permission control (CRUD with role-based access, sharing with OWNER/EDITOR roles, automatic removal of user-owned recipes from a collection when unshared)
- **`extraction`** — extracts recipes from text/images using AI (Spring AI Gemini integration); identifies the caller from the JWT and reserves one unit of that user's `EXTRACTION` budget before calling the provider, so a failed extraction still consumes its unit
- **`limits`** — owns per-subject usage caps for every capped resource: configuration and recorded usage in the database, override-then-default resolution read on every check (so a limit raised by SQL applies on the next request, with no restart), check-and-reserve as one indivisible conditional upsert, stock versus flow caps with lazy period restart, and the shared HTTP 429 refusal. Holds no domain knowledge — callers pass an opaque subject and resource key (see ADR-0006). `extraction` is its only consumer today
- **`planning`** — manages meal plans with user-based permission control (CRUD with role-based access, sharing, meal plan entries with recipe or placeholder support, configurable owner plan limit, automatic conversion of recipe entries to placeholders on `RecipeDeleted` event, calendar view grouped by date, shopping list generation with serving size scaling and inaccessible recipe warnings)
- **`shoppinglists`** — manages shopping lists with user-based permission control (CRUD with role-based access, per-item `baseVersion` optimistic locking on item writes — a body field on update, a query param on delete — with update covering edits, reorders, and check-state as one version-gated write)
- **`provisioning`** — transformation module that converts ingredients (with quantity multipliers) into shopping list items; exposes a `ProvisioningFacade` (no HTTP controller) for use by other modules; appends ingredient comments in parentheses to item names (e.g. `"salt (to taste)"`)
- **`config.security`** — OAuth2 Resource Server — JWT token validation; under the `dev` profile a bypass decoder takes the bearer token as the caller instead (see `docs/backend/standards/configuration-profiles.md`)
- **`config.s3`** — AWS S3 client configuration with presigned URL support
- **`config.time`** — supplies the `Clock` bean that time-dependent services read instead of the system clock

### Layer Structure (within each feature)

```
Controller → Service → Repository → Entity
                ↓
              DTOs / Exceptions
```

- **Controllers**: REST endpoints, JWT auth via `@AuthenticationPrincipal`
- **Services**: Business logic, transaction boundaries
- **Repositories**: Spring Data JPA with custom queries
- **Entities**: JPA entities with relationships
- **DTOs**: Java Records for request/response structures

### Key Patterns

- **Package-private visibility** — internal classes not accessible outside feature package
- **Facade pattern** — `RecipeFacade`, `ProvisioningFacade`, `LimitsFacade` for cross-module access
- **Event-driven cascade** — `RecipeDeleted` event triggers cascading cleanup in meal plans
- **Role-based access** — OWNER/EDITOR roles for recipe sharing
- **Optimistic locking** — per-item `baseVersion` on shopping list item writes
- **Usage limits** — database-backed per-user caps resolved per request and enforced by a single conditional upsert; refusals surface as HTTP 429 with the subject's standing

---

## Mobile Architecture

**Pattern**: Three-layer architecture (Repository → Service → View) split by feature

Files are organized by feature directory (`features/auth/`, `features/recipe/`, `features/planning/`, etc.). Within each feature, all three layers live in the same directory — there are no layer sub-folders.

### Layers

| Layer      | Files               | Responsibility                                                                                         |
|------------|---------------------|--------------------------------------------------------------------------------------------------------|
| Repository | `*_repository.dart` | Data access — HTTP calls, local storage. Returns raw types. No business logic. May hold a local cache or persistence state (e.g. in-memory cache + `ValueNotifier` over a local DB). |
| Service    | `*_service.dart`    | Application state with `ValueNotifier<AsyncValue<T>>`. Coordinates repositories, manages side effects. |
| View       | `*_screen.dart`     | UI rendering. Receives services via constructor. Uses `ValueListenableBuilder` for reactive rebuilds.  |

### State Management

State uses Flutter's built-in `ValueNotifier<AsyncValue<T>>`:

```
AsyncValue<T> = Loading | Data(T) | Error(Object, StackTrace)
```

- Services expose `ValueListenable` getters (read-only to views)
- Boolean flags prevent concurrent method calls on the same service
- `AsyncValue.guardAsync()` wraps async operations, catching errors automatically

### Dependency Injection

`get_it` service locator — registered via `*_setup.dart` files per feature. No code generation required.

### Features

- **`auth`** — user authentication using Firebase Authentication with Google Sign-In, or a dev repository signing in against a `dev`-profile backend when the `devAuthEnabled` flag is set
- **`recipe`** — recipe list and detail display with 3-column grid, horizontal chip-based collection filtering ("All
  Recipes", "Unassigned", specific collection), client-side fuzzy search, recipe create/edit forms, image carousel,
  sharing (OWNER/EDITOR roles), and "Add to Shopping List" flow; includes the `collection` sub-feature for managing
  recipe collections with CRUD and sharing
- **`extraction`** — recipe extraction from URLs (WebView-based with smart URL/search detection) and images
  (camera/gallery); extracted data is passed to the recipe create screen via InitialRecipeFormData. Also reachable as an
  Android share target, which extracts the URL from the shared text and pre-fills the extraction screen
- **`planning`** — meal plan calendar with weekly agenda view across multiple plans; plan management drawer with
  create/edit/delete and role-based actions (delete requires OWNER); local visibility toggles; meal entry management
  supporting recipe entries (with serving size) and placeholder entries (text-only); shopping list generation wizard
  (3-step: select plans → select dates → review items)
- **`shopping_list`** — shopping list management with list creation and display, offline-first inline item management
  with smart text parsing, drag-and-drop reordering within active/done sections, and bulk operations (delete all
  checked, uncheck all). The three destructive actions offer a single-step snackbar undo, scoped to the detail
  screen's lifetime and replayed as an ordinary local mutation. Item state lives in a local sqflite store (in-memory cache + `ValueNotifier` over the DB) with
  an append-only outbox; edits render instantly and persist across restarts while offline. Item state syncs to the
  server by pushing the outbox (FIFO drain with version-gated conflict resolution) and pulling via a background
  full-list poll that diffs others' changes into the local store

### Routing

GoRouter with enum-based type-safe navigation:

```dart
enum AppRoute { login, recipes, recipeDetail, recipeEdit, ... }
// Navigation: context.goNamed(AppRoute.recipeDetail.name, pathParameters: {'id': id})
```
---

## Data Flow

```
User (Android App)
  │
  ├─ Firebase Auth → JWT token
  │
  ▼
Flutter App (mobile/)
  │  HTTP + Bearer JWT
  ▼
Spring Boot API (backend/)
  │
  ├─ OAuth2 Resource Server validates JWT
  ├─ Business logic in feature services
  ├─ PostgreSQL (primary data store)
  └─ AWS S3 (recipe image storage)
```

---

## External Integrations

| Service                      | Purpose                                                       |
|------------------------------|---------------------------------------------------------------|
| Firebase Auth                | User identity and Google Sign-In on mobile                    |
| Google Genai (via Spring AI) | Recipe text/image extraction                                  |
| AWS S3                       | Recipe image storage; presigned URLs for direct mobile access |
| PostgreSQL                   | Primary relational database                                   |

---

## Database Schema

Managed by Flyway migrations in `backend/src/main/resources/db/migration/`. Per-module schema documentation is in `docs/backend/modules/<module>/db.md`.

---

## Configuration

### Backend

Spring Boot profiles (`dev` / `prod`). See `docs/backend/standards/configuration-profiles.md` for profile conventions.

Production deployments require these environment variables:

| Variable                     | Purpose                                  |
|------------------------------|------------------------------------------|
| `SPRING_DATASOURCE_URL`      | Database connection URL                  |
| `SPRING_DATASOURCE_USERNAME` | Database username                        |
| `SPRING_DATASOURCE_PASSWORD` | Database password                        |
| `SPRING_AI_API_KEY`          | API key for Spring AI Gemini integration |
| `AWS_ACCESS_KEY_ID`          | AWS access key ID for S3 operations      |
| `AWS_SECRET_ACCESS_KEY`      | AWS secret access key for S3 operations  |

### Mobile

Firebase config (`google-services.json`), API base URL in app config.

---

## Deployment Architecture

```
┌─────────────────────────────────────┐
│  VPS                                │
│  ┌───────────────────────────────┐  │
│  │  Docker container             │  │
│  │  Spring Boot API              │  │
│  └───────────────┬───────────────┘  │
│                  │                  │
│  ┌───────────────▼───────────────┐  │
│  │  PostgreSQL 17.5              │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘
         │ presigned URLs
         ▼
    AWS S3 (recipe images)

    Google Play → Android APK/AAB
```

### Docker

The backend is containerized using a multi-stage Dockerfile:

1. **Build stage** — `eclipse-temurin:25-jdk-alpine` with Maven compiles the application
2. **Runtime stage** — `eclipse-temurin:25-jre-alpine` for a smaller production image

### CI/CD

GitHub Actions runs on every push to `main`:

1. Builds the Spring Boot application with Maven
2. Builds the Docker image
3. Publishes to GitHub Container Registry (`ghcr.io/dawids21/recipai/api`)
4. Supports manual workflow triggers

---

*Based on codebase analysis performed 2026-04-09*
