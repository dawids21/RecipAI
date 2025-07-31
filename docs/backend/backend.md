# Backend App Overview - RecipAI

## Modules

- `recipes` - manages recipe CRUD operations and data persistence
- `extraction` - extracts recipes from text/images using AI

## Codebase Structure

```
backend/
├── src/main/java/xyz/stasiak/recipai/
│   ├── RecipAiApplication.java          # Main Spring Boot application entry point
│   ├── extraction/                      # "extraction" module
│   └── recipes/                         # "recipes" module
├── src/main/resources/
│   └── application.yml                  # Spring Boot configuration
└── src/test/java/xyz/stasiak/recipai/   # Integration and unit tests with Testcontainers setup
```
