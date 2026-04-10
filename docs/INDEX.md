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

---

## Backend Documentation

Located in `docs/backend/`

### Backend Overview (`backend/backend.md`)
Describes each backend feature module and its responsibilities, the full codebase directory structure with file-level annotations, Spring Boot configuration profiles, required environment variables, and the Docker-based build and deployment process.

### API Documentation (`backend/api.md`)
Documents all REST API endpoints including request/response formats and examples, organised by resource.

### Database Schema (`backend/db.md`)
Documents all database tables, their columns, types, constraints, and relationships. Schema is managed by Flyway migrations.

---

## Backend Standards

Located in `docs/backend/standards/`

### Java Patterns (`backend/standards/java-patterns.md`)
Standards for how to write Java code in this project: DTO structure using records, JPA entity conventions, and class visibility rules.

### Module Structure (`backend/standards/module-structure.md`)
Standards for feature module organisation: how to expose cross-module access via facades, how to structure exception handlers, RESTful endpoint naming conventions, and SLF4J logging patterns.

### Integration Tests (`backend/standards/integration-tests.md`)
Standards for writing backend integration tests: required annotations, HTTP client choice, test data cleanup, assertion library, and test method naming convention.

---

## Mobile Documentation

Located in `docs/mobile/`

### Mobile Overview (`mobile/mobile.md`)
Describes each mobile feature and its responsibilities, all data models used across features, and the full codebase directory structure with file-level annotations.

### Mobile UI (`mobile/ui.md`)
Documents all screens and reusable widgets per feature, the navigation route structure, bottom navigation bar tabs, authentication routing, and the full user flow for each feature area.

---

## Mobile Standards

Located in `docs/mobile/standards/`

### Architecture (`mobile/standards/architecture.md`)
Standards for the three-layer Repository-Service-View architecture: responsibilities of each layer, rules about cross-layer access, feature-based directory layout, and file naming conventions.

### State Management (`mobile/standards/state-management.md`)
Standards for managing async state: how to use `ValueNotifier<AsyncValue<T>>`, how to expose state read-only to views, how to prevent concurrent calls with boolean flags, error handling via `AsyncValue.guardAsync()`, and the `dispose()` requirement on service classes.

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

---

## Architecture Decision Records

Located in `docs/ADRs/`

### ADR 001 — Optimistic UI with Operation-Based Sync for Shopping Lists
Records the decision to use optimistic UI for shopping list item operations, including the chosen approach, alternatives considered, and consequences.

### ADR 002 — Selective Queue Clearing for Rejected Operations
Records the decision on how to handle sync conflicts when server rejects queued operations, including the chosen strategy, alternatives considered, and consequences.

---

## How to Use This Documentation

1. **Start Here**: Always read this INDEX.md first to understand what documentation exists
2. **Project Context**: Read relevant project documentation before starting work
3. **Standards**: Reference appropriate standards when writing code
4. **Keep Updated**: Update documentation when making significant changes