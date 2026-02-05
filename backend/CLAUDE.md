# AI Rules for RecipAI backend

## Tech Stack

- Java 24
- Spring Boot 3.5.10
- Spring Framework 6.2.x
  - Spring Data JPA
  - Spring Web
  - Spring Validation
  - Spring Security + OAuth2 Resource Server
  - Spring Actuator
  - Spring Test
  - Spring Boot DevTools
  - Spring Boot Docker Compose
  - Spring Boot Testcontainers
  - Spring Boot Configuration Processor
- Spring AI 1.1.2
  - Spring AI Google Genai
  - Spring AI PDF Document Reader
  - Spring AI Docker Compose
  - Spring AI Testcontainers
- Lombok 1.18.38
- PostgreSQL 17.5
- Flyway (core + PostgreSQL)
- AWS SDK for Java 2.40.7 (S3)
- Thumbnailator 0.4.21 (image processing)
- Apache Commons IO 2.21.0
- Testcontainers (JUnit Jupiter, PostgreSQL)

## Documentation

- `./docs/backend.md` - **Backend App Overview** - Provides an overview of the backend modules and codebase structure.
- `./docs/api.md` - **API Documentation** - Contains API endpoints, request/response formats, and examples.
- `./docs/db.md` - **Database Schema** - Describes the database structure, tables

## Coding Practices

### Modular Architecture

- Modules (packages) should be split by feature not by layer (like controller, entity, repository).
- Each module should have all required classes to provide a single feature
- Most of the classes should have package-private visibility unless they need to be public
- DTOs should be placed in a `dto` subpackage within the module
- Custom exceptions should be placed in an `exception` subpackage within the module

### Testing

- Write unit tests for methods with complex logic
- Write integration tests for methods that interact with external systems (e.g., database, external APIs)

## Langauge Guidelines

### Spring Boot

- Use Spring Boot for simplified configuration and rapid development with sensible defaults
- Prefer constructor-based dependency injection over `@Autowired`
- Avoid hardcoding values that may change externally, use configuration parameters instead
- For complex logic, use Spring profiles and configuration parameters to control which beans are injected instead of
  hardcoded conditionals
- If a well-known library simplifies the solution, suggest using it instead of generating a custom implementation
- Use DTOs as immutable `record` types
- Use Bean Validation annotations (e.g., `@Size`, `@Email`, etc.) instead of manual validation logic
- Use `@Valid` on request parameters annotated with `@RequestBody`
- Use custom exceptions for business-related scenarios
- Centralize exception handling with `@ControllerAdvice` and return a consistent error DTO: `{{error_dto}}`
- REST controllers should handle only routing and I/O mapping, not business logic
- Use SLF4J for logging instead of `System.out.println`
- Prefer using lambdas and streams over imperative loops and conditionals where appropriate
- Use `Optional` to avoid `NullPointerException`
- Use `@SpringBootTest` for integration tests with `@Import(TestcontainersConfiguration.class)` when database is
  required

### Configuration Profiles

- When adding new configuration, consider if it should be shared across all environments (put in `application.yml`) or
  environment-specific (put in `applcation-dev.yml` and `application-prod.yml`)
- Use environment variables for sensitive production settings (database credentials, API keys)

### Lombok

- Use Lombok where it clearly simplifies the code
- Use constructor injection with `@RequiredArgsConstructor`
- Prefer Java `record` over Lombok’s `@Value` when applicable
- Avoid using `@Data` in non-DTO classes, instead, use specific annotations like `@Getter`, `@Setter`, and `@ToString`
- Don't use `@Data` in JPA classes
- Apply Lombok annotations to fields rather than the class if only some fields require them
- Use Lombok’s `@Slf4j` to generate loggers
