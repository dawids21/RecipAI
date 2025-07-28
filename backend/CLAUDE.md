# AI Rules for {{project-name}}

{{project-description}}

## DEVOPS

### Guidelines for CONTAINERIZATION

#### DOCKER

- Use multi-stage builds to create smaller production images
- Implement layer caching strategies to speed up builds for {{dependency_types}}
- Use non-root users in containers for better security


## DATABASE

### Guidelines for SQL

#### POSTGRES

- Use connection pooling to manage database connections efficiently
- Implement JSONB columns for semi-structured data instead of creating many tables for {{flexible_data}}
- Use materialized views for complex, frequently accessed read-only data


## BACKEND

### Guidelines for JAVA

#### SPRING_BOOT

- Use Spring Boot for simplified configuration and rapid development with sensible defaults
- Prefer constructor-based dependency injection over `@Autowired`
- Avoid hardcoding values that may change externally, use configuration parameters instead
- For complex logic, use Spring profiles and configuration parameters to control which beans are injected instead of hardcoded conditionals
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

#### LOMBOK

- Use Lombok where it clearly simplifies the code
- Use constructor injection with `@RequiredArgsConstructor`
- Prefer Java `record` over Lombok’s `@Value` when applicable
- Avoid using `@Data` in non-DTO classes, instead, use specific annotations like `@Getter`, `@Setter`, and `@ToString`
- Don't use `@Data` in JPA classes
- Apply Lombok annotations to fields rather than the class if only some fields require them
- Use Lombok’s `@Slf4j` to generate loggers


## CODING_PRACTICES

### Guidelines for ARCHITECTURE

### Modular
- Modules (packages) should be split by feature not by layer (like controller, entity, repository).
- Each module should have all required classes to provide a single feature
- Most of the classes should have package-private visibility unless they need to be public

#### ADR

- Create ADRs in /docs/adr/{name}.md for:
- 1) Major dependency changes
- 2) Architectural pattern changes
- 3) New integration patterns
- 4) Database schema changes


