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

| Module | Responsibility |
|--------|---------------|
| `recipes` | Recipe CRUD, role-based sharing (OWNER/EDITOR), collection assignment, image management |
| `recipes.images` | S3 image storage, thumbnail generation, presigned URLs |
| `recipes.collections` | Recipe collection management with permission control |
| `extraction` | AI-powered recipe extraction from text/images (Google Genai via Spring AI) |
| `planning` | Meal plan calendar management, entry CRUD, shopping list generation |
| `shoppinglists` | Shopping list CRUD with optimistic locking (If-Match headers) |
| `provisioning` | Ingredient-to-shopping-list-item transformation (no HTTP layer; used as facade) |
| `config.security` | OAuth2 Resource Server — JWT token validation |
| `config.s3` | AWS S3 client configuration with presigned URL support |

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
- **Facade pattern** — `RecipeFacade`, `ProvisioningFacade` for cross-module access
- **Event-driven cascade** — `RecipeDeleted` event triggers cascading cleanup in meal plans
- **Role-based access** — OWNER/EDITOR roles for recipe sharing
- **Optimistic locking** — If-Match headers on shopping list operations

---

## Mobile Architecture

**Pattern**: Three-layer architecture (Repository → Service → View) split by feature

Files are organized by feature directory (`features/auth/`, `features/recipe/`, `features/planning/`, etc.). Within each feature, all three layers live in the same directory — there are no layer sub-folders.

### Layers

| Layer | Files | Responsibility |
|-------|-------|---------------|
| Repository | `*_repository.dart` | Stateless data access — HTTP calls, local storage. Returns raw types. No business logic. |
| Service | `*_service.dart` | Application state with `ValueNotifier<AsyncValue<T>>`. Coordinates repositories, manages side effects. |
| View | `*_screen.dart` | UI rendering. Receives services via constructor. Uses `ValueListenableBuilder` for reactive rebuilds. |

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

| Feature | Responsibility |
|---------|---------------|
| `auth` | Firebase authentication with Google Sign-In |
| `recipe` | Recipe list, detail, create/edit forms, sharing, collections |
| `extraction` | Extract recipes from URLs and photos |
| `planning` | Meal plan calendar UI, entry management |
| `shopping_list` | Shopping list display and item management |

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

| Service | Purpose |
|---------|---------|
| Firebase Auth | User identity and Google Sign-In on mobile |
| Google Genai (via Spring AI) | Recipe text/image extraction |
| AWS S3 | Recipe image storage; presigned URLs for direct mobile access |
| PostgreSQL | Primary relational database |

---

## Database Schema

Managed by Flyway migrations in `backend/src/main/resources/db/migration/`. See `docs/backend/db.md` for full schema documentation.

---

## Configuration

- **Backend**: Spring Boot `application.properties` / environment variables (JWT issuer, S3 config, DB URL, Google AI key)
- **Mobile**: Firebase config (`google-services.json`), API base URL in app config

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

GitHub Actions builds and pushes the Docker image on merge to main.

---

*Based on codebase analysis performed 2026-04-09*
