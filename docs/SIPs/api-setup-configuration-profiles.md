# SIP: API Setup Configuration Profiles

## Goal

- Split current Spring Boot configuration into PROD and DEV profiles to support different deployment environments
- Leave common configuration shared in the main `application.yml` file
- Activate `prod` profile by default for production deployments
- Move current configuration to development profile (`application-dev.yml`)
- Create production configuration (`application-prod.yml`) with externalized database settings and optimized logging
- Update project documentation to reflect the new profile-based configuration approach
- Success criteria: Application can be deployed with environment-specific configurations while maintaining current
  functionality

## Context

### Documentation and References

- Spring Boot Profiles Official Documentation: https://docs.spring.io/spring-boot/reference/features/profiles.html
- Externalized Configuration: https://docs.spring.io/spring-boot/reference/features/external-config.html
- Environment Variables in Properties: https://www.baeldung.com/spring-boot-properties-env-variables
- Current backend documentation: `docs/backend/backend.md`
- Current backend AI rules: `backend/CLAUDE.md`
- Existing configuration file: `backend/src/main/resources/application.yml`
- Existing Docker Compose setup: `backend/compose.yaml`

### Current Codebase Tree

```
backend/
├── src/main/resources/
│   ├── application.yml                  # Single configuration file (all environments)
│   └── db/migration/                    # Flyway database migrations
├── compose.yaml                         # Docker Compose for local development
├── pom.xml                             # Maven configuration (no profiles defined)
└── CLAUDE.md                           # Backend development guidelines
```

### Desired Codebase Tree

```
backend/
├── src/main/resources/
│   ├── application.yml                  # Common configuration (shared across all profiles)
│   ├── application-dev.yml              # Development profile configuration
│   ├── application-prod.yml             # Production profile configuration
│   └── db/migration/                    # Flyway database migrations (unchanged)
├── compose.yaml                         # Docker Compose for local development (unchanged)
├── pom.xml                             # Maven configuration (unchanged - no profiles needed)
└── CLAUDE.md                           # Updated with profile information
```

### Known Gotchas of Our Codebase and Library Quirks

- Spring Boot auto-configuration currently handles database connection via Docker Compose support
- No explicit datasource configuration exists - relies on Spring Boot's auto-configuration
- Tests use Testcontainers with separate PostgreSQL container setup
- Current logging configuration includes debug level for `xyz.stasiak` package
- Application uses Spring AI with Gemini model requiring API key from environment variable
- OAuth2 Resource Server configuration uses Google Firebase issuer URI
- Flyway migrations are enabled and need to work across all profiles
- File upload limits are configured for multipart requests

## Implementation Plan

### Tasks

```
Task 1: Create production profile configuration file
  Action: CREATE
  File: backend/src/main/resources/application-prod.yml
  Changes:
    - [ ] Create production-specific configuration
    - [ ] Configure database using environment variables (SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD)
    - [ ] Remove debug logging configuration (use default INFO level)
    - [ ] Keep essential configurations: JPA validation, Flyway, multipart settings
    - [ ] Maintain OAuth2 and Spring AI configurations

Task 2: Create development profile configuration file
  Action: CREATE
  File: backend/src/main/resources/application-dev.yml
  Changes:
    - [ ] Move current development-specific configurations from main application.yml
    - [ ] Keep debug logging configuration for development
    - [ ] Maintain Spring DevTools settings
    - [ ] Keep show-sql: true for development debugging
    - [ ] Preserve Docker Compose auto-configuration support

Task 3: Update main configuration file with common settings
  Action: MODIFY
  File: backend/src/main/resources/application.yml
  Changes:
    - [ ] Keep only configuration common to all environments
    - [ ] Add spring.profiles.active: prod to activate production profile by default
    - [ ] Retain: application name, multipart settings, JPA hibernate ddl-auto, Flyway, Spring AI, OAuth2 configurations
    - [ ] Remove environment-specific settings (logging levels, show-sql, devtools)

Task 4: Update backend CLAUDE.md documentation
  Action: MODIFY
  File: backend/CLAUDE.md
  Changes:
    - [ ] Add guidelines for splitting new configuration between profile files (when to split DEV/PROD and when to share in common file)

Task 5: Update backend overview documentation
  Action: MODIFY
  File: docs/backend/backend.md
  Changes:
    - [ ] Add information about profile-based configuration
    - [ ] Document the three configuration files and their purposes
    - [ ] Note environment variable requirements for production
```

### Per Task Pseudocode

```yaml
# Task 1: Production Configuration (application-prod.yml)
spring:
  application:
    name: RecipAI
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/recipai}
    username: ${SPRING_DATASOURCE_USERNAME:recipai}
    password: ${SPRING_DATASOURCE_PASSWORD:changeme}
  jpa:
    hibernate:
      ddl-auto: validate
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
  flyway:
    enabled: true
  ai:
    openai:
      api-key: ${SPRING_AI_API_KEY}
      # ... rest of AI config
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://securetoken.google.com/recipai-751ae

# Task 2: Development Configuration (application-dev.yml)
spring:
  devtools:
    restart:
      enabled: false
  jpa:
    show-sql: true
logging:
  level:
    xyz.stasiak: DEBUG

# Task 3: Common Configuration (application.yml)
spring:
  profiles:
    active: prod
  application:
    name: RecipAI
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
  ai:
    openai:
      api-key: ${SPRING_AI_API_KEY}
      base-url: https://generativelanguage.googleapis.com/v1beta/openai/
      chat:
        completions-path: /chat/completions
        options:
          model: gemini-2.5-flash
          reasoning-effort: none
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://securetoken.google.com/recipai-751ae
```

## Validation

### Syntax and Style

```bash
# Run these FIRST - fix any errors before proceeding
mvn compile

# Expected: No compilation errors. If errors, READ the error and fix.
```

### Unit Tests

```bash
# Run and iterate until passing:
mvn test
# If failing: Read error, understand root cause, fix code, re-run (never mock to pass)
```

### Integration Tests

```bash
# Run and iterate until passing:
mvn test
# If failing: Read error, understand root cause, fix code, re-run (never mock to pass)
```

### Manual Testing

```bash
# Test development profile activation
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Test production profile activation (default)
mvn spring-boot:run

# Test with environment variables
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/testdb
export SPRING_DATASOURCE_USERNAME=testuser
export SPRING_DATASOURCE_PASSWORD=testpass
mvn spring-boot:run

# Verify profile-specific configurations are loaded correctly
# Check logs for profile activation messages
# Verify database connections work in both profiles
```

## Integration Points

- Database connection: Production profile requires environment variables for database configuration
- Docker Compose: Development profile maintains auto-configuration support for local development
- Testing: Tests continue using Testcontainers - no changes required to test configuration
- Deployment: Production deployments need to set SPRING_DATASOURCE_* environment variables
- CI/CD: Build process remains unchanged, profile activation handled at runtime

## Documentation

- `backend/CLAUDE.md` - Add guidelines for splitting new configuration between profile files (when to split DEV/PROD and
  when to share in common file)
- `docs/backend/backend.md` - Add information about profile-based configuration, document the three configuration files
  and their purposes, note environment variable requirements for production

## Final Validation Checklist

- [ ] Correct syntax - application compiles without errors
- [ ] Correct style - follows existing YAML formatting conventions
- [ ] All tests pass - unit and integration tests successful
- [ ] Manual test successful - application starts with both dev and prod profiles
- [ ] Error cases handled gracefully - missing environment variables have sensible defaults
- [ ] Logs are informative but not verbose - production profile uses appropriate log levels
- [ ] Documentation updated - both CLAUDE.md and backend.md reflect new configuration approach

## Confidence Score: 9/10

This SIP provides comprehensive context for implementing Spring Boot profiles with high confidence for one-pass
implementation. The research includes current Spring Boot best practices, existing codebase analysis, and detailed
implementation steps. The validation approach ensures correct functionality across different deployment scenarios.