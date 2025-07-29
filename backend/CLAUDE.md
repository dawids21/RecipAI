# AI Rules for RecipAI backend

## Codebase Structure

```
backend/
├── src/main/java/xyz/stasiak/recipai/
│   ├── RecipAiApplication.java          # Main Spring Boot application entry point
│   ├── extraction/                      # Module for extracting recipes from text/images using AI
│   └── recipes/                         # Module for managing recipe CRUD operations and data persistence
├── src/main/resources/
│   └── application.yml                  # Spring Boot configuration
└── src/test/java/xyz/stasiak/recipai/   # Integration and unit tests with Testcontainers setup
```

## Tech Stack

- Java 24
- Spring Framework
    - Spring Core 6.2.9
    - Spring Boot 3.5.4
    - Spring Web 6.2.9
    - Spring Data JPA 3.5.2
    - Spring AI 1.0.0
    - Spring AI OpenAI 1.0.0
    - Spring AI PDF Document Reader 1.0.0
    - Spring Test 6.2.9
    - Spring Boot DevTools 3.5.4
    - Spring Boot Docker Compose 3.5.4
    - Spring Boot Testcontainers 3.5.4
    - Spring Boot Configuration Processor 3.5.4
- Lombok 1.18.38
- PostgreSQL 17.5

## Coding Practices

### Modular Architecture

- Modules (packages) should be split by feature not by layer (like controller, entity, repository).
- Each module should have all required classes to provide a single feature
- Most of the classes should have package-private visibility unless they need to be public

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
- Use `@SpringBootTest` for integration tests with `@Import(TestcontainersConfiguration.class)` when database is required

### Lombok

- Use Lombok where it clearly simplifies the code
- Use constructor injection with `@RequiredArgsConstructor`
- Prefer Java `record` over Lombok’s `@Value` when applicable
- Avoid using `@Data` in non-DTO classes, instead, use specific annotations like `@Getter`, `@Setter`, and `@ToString`
- Don't use `@Data` in JPA classes
- Apply Lombok annotations to fields rather than the class if only some fields require them
- Use Lombok’s `@Slf4j` to generate loggers
