# Backend App Overview - RecipAI

## Modules

- `recipes` - manages recipe CRUD operations and data persistence
- `extraction` - extracts recipes from text/images using AI
- `security` - handles OAuth2 Resource Server authentication with JWT tokens

## Codebase Structure

```
backend/
├── src/main/java/xyz/stasiak/recipai/
│   ├── RecipAiApplication.java          # Main Spring Boot application entry point
│   ├── extraction/                      # "extraction" module
│   ├── recipes/                         # "recipes" module
│   └── security/                        # "security" module
│       └── SecurityConfig.java          # OAuth2 Resource Server configuration
├── src/main/resources/
│   └── application.yml                  # Spring Boot configuration
└── src/test/java/xyz/stasiak/recipai/   # Integration and unit tests with Testcontainers setup
    ├── TestSecurityConfiguration.java  # Test JWT mocking configuration
    └── TestcontainersConfiguration.java
```
