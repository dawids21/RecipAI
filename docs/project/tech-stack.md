# Technology Stack

## Overview

RecipAI is a monorepo containing two independent applications: a Spring Boot REST API (backend) and a Flutter mobile app (Android). They share documentation and git history but have separate build systems.

---

## Languages

### Java 25
- **Usage**: ~100% of backend codebase
- **Rationale**: Modern LTS with strong Spring Boot ecosystem; record types and pattern matching used for concise DTOs and clean code
- **Key Features Used**: Records for DTOs, sealed classes, text blocks, Lombok-reduced boilerplate

### Dart 3.8.1
- **Usage**: ~100% of mobile codebase
- **Rationale**: Required by Flutter; null-safe, strong typing, fast hot-reload for UI development
- **Key Features Used**: Sealed classes (`AsyncValue`), extension methods, async/await

---

## Frameworks

### Mobile — Flutter 3.32
- Cross-platform UI toolkit; targets Android only
- Material Design 3 theming via `ThemeData` with seed color
- flutter_localizations for i18n support (en_US, en_GB, pl)

### Backend — Spring Boot 3.5.10 (Spring Framework 6.2.x)
- Spring Data JPA — ORM and data access
- Spring Security + OAuth2 Resource Server — JWT authentication
- Spring AI 1.1.2 — AI recipe extraction via Google Genai
- Spring Boot Actuator — health monitoring and metrics
- Spring Boot Docker Compose — local dev database orchestration

### Testing
- **Backend**: JUnit Jupiter + Spring Boot Test + Testcontainers (integration tests against real PostgreSQL)
- **Mobile**: flutter_test (currently minimal coverage — smoke test only)

---

## Database

### PostgreSQL 17.5
- **Type**: Relational
- **ORM**: Spring Data JPA (Hibernate)
- **Migrations**: Flyway (core + PostgreSQL dialect)
- **Rationale**: Reliable, well-supported by Spring ecosystem; used in Docker Compose for local development

---

## Build Tools & Package Management

| Platform | Tool | Config File |
|----------|------|-------------|
| Backend  | Maven | `backend/pom.xml` |
| Mobile   | Dart pub | `mobile/pubspec.yaml` |

Maven plugins: `spring-boot-maven-plugin`, `maven-compiler-plugin`

---

## Infrastructure

### Containerization
- **Docker** — backend production container (built via GitHub Actions)
- **Docker Compose** — local development PostgreSQL database (`backend/compose.yaml`)

### CI/CD
- **GitHub Actions** — Docker image build and push to container registry on merge

### Hosting
- **Backend**: Self-hosted VPS
- **Mobile**: Google Play (distribution)
- **Assets**: AWS S3 (recipe image storage with presigned URLs)

---

## Key Dependencies

### Backend

| Dependency | Version | Purpose |
|-----------|---------|---------|
| Spring Boot | 3.5.10 | Application framework |
| Spring AI | 1.1.2 | AI recipe extraction (Google Genai) |
| Spring Security OAuth2 | — | JWT authentication |
| PostgreSQL driver | — | Database connectivity |
| Flyway | core | Database schema migrations |
| Lombok | 1.18.38 | Boilerplate code generation |
| AWS SDK for Java | 2.40.7 | S3 image storage |
| Thumbnailator | 0.4.21 | Recipe image thumbnail generation |
| Testcontainers | — | Integration test database |

### Mobile

| Dependency | Version | Purpose |
|-----------|---------|---------|
| go_router | 17.1.0 | Declarative navigation with enum-based routes |
| firebase_auth | 6.0.2 | User authentication |
| google_sign_in | 7.1.1 | OAuth2 sign-in |
| get_it | 9.2.0 | Service locator dependency injection |
| http | 1.1.0 | REST API client |
| shared_preferences | 2.2.2 | Local persistent storage |
| image_picker | 1.2.0 | Camera/gallery image selection |
| webview_flutter | 4.13.0 | In-app web content |
| photo_view | 0.15.0 | Zoomable recipe image viewer |
| flutter_speed_dial | 7.0.0 | Floating action button with sub-actions |
| fuzzy | 0.5.1 | Local fuzzy search |
| intl | — | Internationalization |
| flutter_lints | 6.0.0 | Linting |

---

## Development Tools

### Linting & Formatting
- **Mobile**: `flutter_lints 6.0.0` (`analysis_options.yaml`)
- **Backend**: Spring Boot defaults + Lombok annotation processing

### Type Checking
- Java: Compile-time type safety (javac)
- Dart: Null-safe compile-time type checking

---

## Version Management

- Semantic versioning on mobile (`pubspec.yaml`: `version: 1.0.0+14`)
- Backend versioned via Maven POM and Docker image tags
- Conventional commits enforced in git history (`feat:`, `fix:`, `build:`, `docs:`, `refactor:`)

---

*Last Updated*: 2026-04-09
*Auto-detected*: tech stack, dependencies, versions, build tools, CI/CD config, infrastructure
*User-provided*: hosting details (VPS for backend, AWS S3 for assets)
