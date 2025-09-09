# Backend App Overview - RecipAI

## Modules

- `users` - manages user registration and user data persistence
- `recipes` - manages user-scoped recipe CRUD operations and data persistence
- `extraction` - extracts recipes from text/images using AI
- `security` - handles OAuth2 Resource Server authentication with JWT tokens

## Codebase Structure

```
backend/
├── src/main/java/xyz/stasiak/recipai/
│   ├── RecipAiApplication.java          # Main Spring Boot application entry point
│   ├── users/                           # "users" module
│   │   ├── User.java                    # User entity
│   │   ├── UserRepository.java          # User data access
│   │   └── UserController.java          # User registration endpoint
│   ├── recipes/                         # "recipes" module
│   │   ├── Recipe.java                  # Recipe entity
│   │   ├── UserRecipe.java              # User-Recipe association entity
│   │   ├── UserRecipeId.java            # Composite key for user-recipe associations
│   │   ├── UserRecipeRepository.java    # User-Recipe association data access
│   │   ├── RecipeRepository.java        # Recipe data access with user filtering
│   │   ├── RecipeService.java           # Recipe business logic with user context
│   │   └── RecipeController.java        # Recipe REST endpoints with JWT extraction
│   ├── extraction/                      # "extraction" module
│   └── security/                        # "security" module
│       └── SecurityConfig.java          # OAuth2 Resource Server configuration
├── src/main/resources/
│   └── application.yml                  # Spring Boot configuration
└── src/test/java/xyz/stasiak/recipai/   # Integration and unit tests with Testcontainers setup
    ├── TestSecurityConfiguration.java  # Test JWT mocking configuration (multi-user support)
    └── TestcontainersConfiguration.java
```
