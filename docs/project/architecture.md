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

Module descriptions are in each module's `module.md` (see `docs/backend/modules/<module>/module.md`,
indexed in `docs/INDEX.md`); the role of each in the system:

- **`recipes`** — recipe CRUD, sharing, and collections, the primary content of the app
- **`recipes.images`** — recipe image storage and retrieval, backing `recipes`
- **`recipes.collections`** — grouping of recipes, part of the `recipes` module
- **`extraction`** — turns text/images into recipe data via AI, feeding `recipes` creation
- **`limits`** — the shared quota-enforcement module every other feature module consumes
- **`permissions`** — the shared access-control module owning granted permissions and pending invites for every shareable resource
- **`planning`** — meal plans that reference `recipes` and generate shopping lists
- **`shoppinglists`** — shopping lists, populated manually or generated from `planning`
- **`provisioning`** — ingredient-to-shopping-list-item transformation used by `planning`
- **`config.security`** — OAuth2 Resource Server / JWT validation for every request
- **`config.s3`** — AWS S3 client configuration backing `recipes.images`
- **`config.time`** — the `Clock` bean time-dependent services read instead of the system clock

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
- **Facade pattern** — `RecipeFacade`, `ProvisioningFacade`, `LimitsFacade`, `PermissionsFacade` for cross-module access
- **Event-driven cascade** — `RecipeDeleted` event triggers cascading cleanup in meal plans
- **Role-based access** — OWNER/EDITOR roles on every shareable resource, answered by the `permissions` module; sharing is a two-step handshake that grants nothing until the invitee accepts the pending invite (see `docs/backend/modules/permissions/module.md`)
- **Optimistic locking** — per-item `baseVersion` on shopping list item writes
- **Usage limits** — database-backed per-subject quotas resolved per request and enforced by a single conditional upsert; refusals surface as HTTP 429 with the subject's balance. Owner-scoped quotas reserve on create and release on delete; the shopping-list item quota counts against the list while resolving its value from the owner. The mobile app pairs the quotas read with each module's balance read to show `used / limit` at the point of action and disable it at the limit — a display and a pre-emptive block, never a second enforcement path

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
- **`limits`** — holds the session's quotas (one `GET /limits` on sign-in, cleared on sign-out) and the
  `used / limit` counter widget every limited surface renders. The count beside it comes from the
  feature that owns the resource, so each surface can disable its action at the quota; a missing quota or a
  failed count leaves the action enabled and the server the only thing that refuses
- **`invites`** — the invitee-facing surface for the backend's `permissions` module: a full-screen `/invites`
  list (accept/decline, with decline confirmed) reached from a dot-badged overflow icon and a counted menu
  row on the Main Screen. One notifier feeds both indicator and screen; accepting reloads the resource's own
  list so it appears in its tab immediately
- **`sharing`** — the sharer-facing counterpart to `invites`: one generic `SharingDialog` that recipes,
  collections, shopping lists, and meal plans all open, rendering granted users and pending invites in one
  list with a "Pending" marker, and cancelling an invite through the same `unshare` call that removes a
  granted user

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
