# Backend App Overview - RecipAI

## Modules

- `recipes` - manages user-scoped recipe CRUD operations with role-based sharing functionality
- `extraction` - extracts recipes from text/images using AI
- `security` - handles OAuth2 Resource Server authentication with JWT tokens

## Codebase Structure

```
backend/
├── src/main/java/xyz/stasiak/recipai/
│   ├── RecipAiApplication.java          # Main Spring Boot application entry point
│   ├── recipes/                         # "recipes" module
│   │   ├── Recipe.java                  # Recipe entity
│   │   ├── UserRecipe.java              # User-Recipe association entity with roles
│   │   ├── UserRecipeId.java            # Composite key for user-recipe associations
│   │   ├── UserRole.java                # Enum for OWNER/EDITOR roles
│   │   ├── UserRecipeRepository.java    # Role-based user-recipe data access
│   │   ├── RecipeRepository.java        # Recipe data access with user filtering
│   │   ├── RecipeService.java           # Recipe business logic with role-based sharing
│   │   ├── RecipeController.java        # Recipe REST endpoints with sharing support
│   │   ├── RecipeDto.java               # Recipe response DTO with role information
│   │   ├── RecipeListDto.java           # Recipe list response DTO
│   │   ├── CreateRecipeRequest.java     # Create recipe request DTO
│   │   ├── UpdateRecipeRequest.java     # Update recipe request DTO
│   │   ├── ShareRecipeRequest.java      # Share recipe request DTO
│   │   ├── UnshareRecipeRequest.java    # Unshare recipe request DTO
│   │   ├── RecipeData.java              # Recipe data structure
│   │   ├── Ingredient.java              # Ingredient model
│   │   ├── Instruction.java             # Instruction model
│   │   ├── RecipeNotFoundException.java # Recipe not found exception
│   │   ├── RecipeAccessDeniedException.java # Access denied exception
│   │   ├── ErrorResponse.java           # Error response DTO
│   │   └── GlobalExceptionHandler.java  # Exception handling
│   ├── extraction/                      # "extraction" module
│   └── security/                        # "security" module
│       └── SecurityConfig.java          # OAuth2 Resource Server configuration
├── src/main/resources/
│   └── application.yml                  # Spring Boot configuration
└── src/test/java/xyz/stasiak/recipai/   # Integration and unit tests with Testcontainers setup
    ├── TestSecurityConfiguration.java  # Test JWT mocking configuration (multi-user support)
    └── TestcontainersConfiguration.java
```
