# Documentation Index

**IMPORTANT**: Read this file at the beginning of any development task to understand available documentation and standards.

## Quick Reference

### Project Documentation
Project-level documentation covering vision, goals, architecture, and technology choices. Located in `docs/project/`.

### Technical Standards
Coding standards and conventions organized by domain. Located in `docs/backend/standards/` and `docs/mobile/standards/`.

---

## Project Documentation

Located in `docs/project/`

### PRD (`project/prd.md`)
Defines the product vision, the user problem being solved, functional requirements for each feature area, and the current MVP scope with what is and isn't included.

### Tech Stack (`project/tech-stack.md`)
Lists all languages, frameworks, libraries, build tools, infrastructure, and hosting choices for both the backend and mobile applications, including versions and rationale.

### Architecture (`project/architecture.md`)
Describes the structural design of both applications: backend module breakdown and layer structure, mobile feature organisation and layer responsibilities, authentication flow, data flow diagram, external integrations, and deployment topology.

### Local Development (`project/local-development.md`)
How to run the backend locally with `recipai.sh`, the dev-profile authentication bypass and how to
address callers with it, the environment variables a local run honours, the curl idioms for
calling the API, and the `backend/http/` `.http` request suite.

---

## Backend Documentation

Located in `docs/backend/`

### Module Documentation

Per-module documentation is in `docs/backend/modules/<module>/`. Each module directory contains up to three files:
- `codebase_structure.md` — file tree for that module
- `api.md` — REST API endpoints for that module
- `db.md` — database tables, relationships, and indexes for that module

Module descriptions (what each module does) are in `docs/project/architecture.md`.

#### Recipes & Collections (`backend/modules/recipes/`)
- `codebase_structure.md` — file tree for `recipes`, `recipes.collections`, `recipes.images`
- `api.md` — all `/recipes` and `/collections` endpoints including sharing and image upload
- `db.md` — `recipes`, `recipe_images`, `recipe_permission`, `recipes_collections`, `recipes_collection_permission` tables

#### Extraction (`backend/modules/extraction/`)
- `codebase_structure.md` — file tree
- `api.md` — `/extract/text`, `/extract/image` and `/extract/usage` endpoints

#### Shopping Lists (`backend/modules/shopping-lists/`)
- `codebase_structure.md` — file tree
- `api.md` — all `/shopping-lists` endpoints including item operations
- `db.md` — `shopping_lists`, `shopping_list_permission`, `shopping_list_items` tables

#### Planning (`backend/modules/planning/`)
- `codebase_structure.md` — file tree
- `api.md` — all `/meal-plans` endpoints including calendar view and shopping list generation
- `db.md` — `meal_plans`, `meal_plan_permissions`, `meal_plan_entries` tables

#### Limits (`backend/modules/limits/`)
- `codebase_structure.md` — file tree, module boundary, reserve/release/clear/resolution behaviour including the config-subject vs usage-subject split, the standing and cap reads, the 429 refusal contract, the `recipai.limits.enabled` kill-switch, and the consuming modules
- `api.md` — `GET /limits`, and the contract shared by the per-module `/usage` reads
- `db.md` — `limit_config`, `limit_usage` tables, the seeded defaults, and the repeatable recompute that rebuilds usage from the permission tables and, for shopping-list items, from the items themselves

#### Provisioning (`backend/modules/provisioning/`)
- `codebase_structure.md` — file tree (no HTTP endpoints, no DB tables)

#### Config (`backend/modules/config/`)
- `codebase_structure.md` — file tree for `config.s3`, `config.security`, `config.time`

---

## Backend Standards

Located in `docs/backend/standards/`

### Java Patterns (`backend/standards/java-patterns.md`)
Standards for how to write Java code in this project: DTO structure using records, JPA entity conventions, and class visibility rules.

### Module Structure (`backend/standards/module-structure.md`)
Standards for feature module organisation: how to expose cross-module access via facades, how to structure exception handlers, RESTful endpoint naming conventions, and SLF4J logging patterns.

### Configuration Profiles (`backend/standards/configuration-profiles.md`)
Standards for Spring Boot profile usage: which config file serves what purpose, profile activation, and rules about where secrets and environment-specific values belong.

### Integration Tests (`backend/standards/integration-tests.md`)
Standards for writing backend integration tests: required annotations, HTTP client choice, test data cleanup, assertion library, test method naming convention, reading otherwise-inaccessible state through a facade method rather than hand-written SQL, and how to test a suite whose module is capped by `limits`.

---

## Mobile Documentation

Located in `docs/mobile/`

### Module Documentation

Per-module documentation is in `docs/mobile/modules/<module>/`. Each module directory contains two files:
- `codebase_structure.md` — file tree for that module
- `ui.md` — screens, widgets, and user flows for that module

Module descriptions (what each module does) are in `docs/project/architecture.md`.

#### Core (`mobile/modules/core/`)
- `codebase_structure.md` — file tree for `main.dart`, `core/`, `shared/`
- `ui.md` — Main Screen, shared widgets, navigation route structure, bottom navigation bar, and authentication flow

#### Auth (`mobile/modules/auth/`)
- `codebase_structure.md` — file tree for `features/auth/`
- `ui.md` — Login Screen and authentication flow

#### Recipe & Collections (`mobile/modules/recipe/`)
- `codebase_structure.md` — file tree for `features/recipe/` including `collection/` sub-feature
- `ui.md` — all recipe and collection screens/widgets, recipe management flow, and collections management flow

#### Extraction (`mobile/modules/extraction/`)
- `codebase_structure.md` — file tree for `features/extraction/`
- `ui.md` — URL and image extraction screens and flow

#### Shopping Lists (`mobile/modules/shopping_list/`)
- `codebase_structure.md` — file tree for `features/shopping_list/`
- `ui.md` — shopping list screens/widgets and management flow

#### Planning (`mobile/modules/planning/`)
- `codebase_structure.md` — file tree for `features/planning/`
- `ui.md` — calendar and planning screens, shopping list generation wizard, and all planning flows

#### Limits (`mobile/modules/limits/`)
- `codebase_structure.md` — file tree for `features/limits/`, and where each capped resource's count lives instead
- `ui.md` — the `used / limit` counter widget, how caps and counts are loaded, the fail-open rule, and the surfaces that block at the cap

---

## Mobile Standards

Located in `docs/mobile/standards/`

### Architecture (`mobile/standards/architecture.md`)
Standards for the three-layer Repository-Service-View architecture: responsibilities of each layer, rules about cross-layer access, how narrow a widget's constructor inputs should be, feature-based directory layout, and file naming conventions.

### State Management (`mobile/standards/state-management.md`)
Standards for managing async state: `ValueNotifier<AsyncValue<T>>` as the default (not required) shape, how to expose state read-only to views, guarding against concurrent calls (boolean flags, per-key locks, single-flight drains), error handling via `AsyncValue.guardAsync()`, and the `dispose()` requirement on service classes.

### Dependency Injection (`mobile/standards/dependency-injection.md`)
Standards for using `get_it`: how to write per-feature setup functions, constructor-based injection rules, how to pass external dependencies to setup functions, and when to use singleton vs lazySingleton vs StatefulWidget for state scoping.

### Navigation (`mobile/standards/navigation.md`)
Standards for routing: how to define routes in the `AppRoute` enum, how to navigate using `context.goNamed()`, how to configure `go_router` auth guards, and how to inject services into screens via route builder closures.

### Theming (`mobile/standards/theming.md`)
Standards for styling: how to access the theme in build methods, the priority order for choosing styling values (`Theme.of(context)` → `AppSpacing`/`AppAnimations` constants → new constants → hardcoded), and the available `AppSpacing` and `AppAnimations` constants.

### Preferences Service (`mobile/standards/preferences-service.md`)
Standards for local persistence: `PreferencesService` is the only approved mechanism, how to register and inject it, the synchronous read / asynchronous write pattern, and how to add a new preference.

### Feature Flags (`mobile/standards/feature-flags.md`)
Standards for feature flags: how to define flags using `bool.fromEnvironment()`, how to use them to gate UI rendering only, when to remove them, and the table of currently active flags.

### Logging (`mobile/standards/logging.md`)
Standards for logging: hierarchical logger naming (`recipai.<feature>.<layer>`), level conventions, redaction rules (never log bearer tokens or request/response bodies), and the rotating file-sink + share-via-platform-channel pattern.

### Widget Testing (`mobile/standards/widget-testing.md`)
Standards for widget tests: repository-only mocking with mocktail (real services run on top), directory layout under `test/` mirroring `lib/`, `SharedPreferences.setMockInitialValues` and `PreferencesService` registration before `setup*()` calls, `GetIt.I.reset()` lifecycle and building a single-route `GoRouter` with a `NavigatorObserver` subclass for navigation assertions.

---

## Architecture Decision Records (ADRs)

Located in `docs/ADRs/`. See `ADRs/INDEX.md` for the full list of decisions and their status.

ADRs document significant architectural decisions: the context, the options considered, and the chosen approach.

---

## How to Use This Documentation

1. **Start Here**: Always read this INDEX.md first to understand what documentation exists
2. **Project Context**: Read relevant project documentation before starting work
3. **Standards**: Reference appropriate standards when writing code
4. **Keep Updated**: Update documentation when making significant changes
