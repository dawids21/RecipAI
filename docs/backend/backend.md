# Backend App Overview - RecipAI

## Modules

- `recipes` - manages user-scoped recipe CRUD operations with role-based sharing functionality
- `extraction` - extracts recipes from text/images using AI
- `security` - handles OAuth2 Resource Server authentication with JWT tokens
- `shoppinglists` - manages shopping lists (basic CRUD operations, no user association in this iteration)

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
│   │   └── RecipesExceptionHandler.java # Exception handling
│   ├── extraction/                      # "extraction" module
│   ├── shoppinglists/                   # "shoppinglists" module
│   │   ├── ShoppingList.java            # Shopping list entity
│   │   ├── ShoppingListPermission.java  # Shopping list permission association entity
│   │   ├── ShoppingListPermissionId.java # Composite key for shopping list permissions
│   │   ├── UserRole.java                # Enum for OWNER/EDITOR roles
│   │   ├── ShoppingListItem.java        # Shopping list item entity
│   │   ├── ShoppingListRepository.java  # Shopping list data access
│   │   ├── ShoppingListPermissionRepository.java # Permission queries repository
│   │   ├── ShoppingListItemRepository.java # Shopping list item data access
│   │   ├── ShoppingListService.java     # Shopping list business logic with permission checks
│   │   ├── ShoppingListController.java  # Shopping list REST endpoints with JWT authentication
│   │   ├── ShoppingListListDto.java     # Shopping list list response DTO
│   │   ├── ShoppingListDto.java         # Shopping list detail response DTO with items
│   │   ├── ShoppingListItemDto.java     # Shopping list item DTO
│   │   ├── CreateShoppingListRequest.java # Create shopping list request DTO
│   │   ├── ShoppingListNotFoundException.java # Shopping list not found exception
│   │   └── ShoppingListsExceptionHandler.java # Exception handling with ProblemDetail
│   └── security/                        # "security" module
│       └── SecurityConfig.java          # OAuth2 Resource Server configuration
├── src/main/resources/
│   ├── application.yml                  # Common Spring Boot configuration (shared across all profiles)
│   ├── application-dev.yml              # Development profile configuration
│   ├── application-prod.yml             # Production profile configuration
│   └── db/migration/                    # Flyway database migrations
└── src/test/java/xyz/stasiak/recipai/   # Integration and unit tests with Testcontainers setup
    ├── TestSecurityConfiguration.java  # Test JWT mocking configuration (multi-user support)
    └── TestcontainersConfiguration.java
```

## Configuration Profiles

The application uses Spring Boot profiles to support different deployment environments.
By default, the application is configured to run in **production** mode.

### Configuration Files

- **`application.yml`** - Common configuration shared across all environments
- **`application-dev.yml`** - Development profile configuration
- **`application-prod.yml`** - Production profile configuration

### Environment Variables for Production

Production deployments require the following environment variables:

- `SPRING_DATASOURCE_URL` - Database connection URL
- `SPRING_DATASOURCE_USERNAME` - Database username
- `SPRING_DATASOURCE_PASSWORD` - Database password
- `SPRING_AI_API_KEY` - API key for Spring AI Gemini integration

## Database

- The application uses Flyway for database migrations
- All changes to the database schema should be implemented as migrations in `src/main/resources/db/migration/`

## Building and Deploying the API

### Docker Deployment

The RecipAI backend API is containerized using Docker.
The Dockerfile uses a multi-stage build process:

1. **Build stage**: Uses `eclipse-temurin:24-jdk-alpine` with Maven to compile the application
2. **Runtime stage**: Uses `eclipse-temurin:24-jre-alpine` for a smaller, production-ready image

### GitHub Container Registry

Docker images are automatically built and published to GitHub Container Registry (GHCR) via GitHub Actions. Repository:
`ghcr.io/dawids21/recipai/api`

### CI/CD Pipeline

GitHub Actions automatically:

1. Builds the Spring Boot application with Maven
2. Creates Docker images on every push to `main` branch
3. Publishes images to GitHub Container Registry
4. Supports manual workflow triggers