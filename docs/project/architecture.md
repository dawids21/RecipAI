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
- **`settings`** — account deletion, self-service and by an administrator, ordering every module's per-account purge
- **`provisioning`** — ingredient-to-shopping-list-item transformation used by `planning`
- **`config.security`** — OAuth2 Resource Server / JWT validation for every request, and the admin authority from a configured allowlist
- **`config.s3`** — AWS S3 client configuration and the object-storage seam backing `recipes.images`
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
- **Facade pattern** — `RecipeFacade`, `RecipesCollectionsFacade`, `ShoppingListsFacade`, `PlanningFacade`, `ProvisioningFacade`, `LimitsFacade`, `PermissionsFacade` for cross-module access
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

Module descriptions are in each module's `module.md` (see `docs/mobile/modules/<module>/module.md`, indexed in
`docs/INDEX.md`); the role of each in the app:

- **`core`** — the app shell (start-up, routing and the auth redirect, Main Screen tabs) and shared infrastructure
- **`auth`** — signs the user in (Firebase with Google Sign-In, or a dev identity) and supplies the bearer token
- **`recipe`** — recipes and their collections, the app's main content, with sharing and hand-offs to plans and lists
- **`extraction`** — turns a web page, a photo or an Android share into recipe data for `recipe` creation
- **`planning`** — meal plan calendar of recipe and note entries, and shopping list generation from it
- **`shopping_list`** — shopping lists with an offline-first item store that syncs in the background
- **`limits`** — the session's quotas and the `used / limit` counter every limited surface renders
- **`invites`** — the invitee's pending invites from the backend's `permissions` module, accepted or declined
- **`sharing`** — the sharer's generic dialog every shareable resource opens

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

| Variable                        | Purpose                                                                      |
|---------------------------------|------------------------------------------------------------------------------|
| `SPRING_DATASOURCE_URL`         | Database connection URL                                                      |
| `SPRING_DATASOURCE_USERNAME`    | Database username                                                            |
| `SPRING_DATASOURCE_PASSWORD`    | Database password                                                            |
| `SPRING_AI_API_KEY`             | API key for Spring AI Gemini integration                                     |
| `AWS_ACCESS_KEY_ID`             | AWS access key ID for S3 operations                                          |
| `AWS_SECRET_ACCESS_KEY`         | AWS secret access key for S3 operations                                      |
| `RECIPAI_SECURITY_ADMIN_EMAILS` | Comma-separated administrator emails; optional, empty means no administrator |

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

1. **Build stage** — `eclipse-temurin:27-jdk-alpine` with Maven compiles the application
2. **Runtime stage** — `eclipse-temurin:27-jre-alpine` for a smaller production image

### CI/CD

A single GitHub Actions workflow, `.github/workflows/docker-build-api.yml`, builds the backend image. It runs only
when triggered manually (`workflow_dispatch`); merging to `main` does not trigger it. It builds `backend/` with its
Dockerfile (the Maven build runs in the Docker build stage) and pushes the image to GitHub Container Registry as
`ghcr.io/dawids21/recipai/api`, tagged with the triggering branch name and `latest`.
