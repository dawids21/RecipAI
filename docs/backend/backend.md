# Backend App Overview - RecipAI

## Modules

- `recipes` - manages user-scoped recipe CRUD operations with role-based sharing functionality
- `recipes.collections` - manages recipes collections with user-based permission control (CRUD operations with
  role-based access)
- `extraction` - extracts recipes from text/images using AI
- `security` - handles OAuth2 Resource Server authentication with JWT tokens
- `shoppinglists` - manages shopping lists with user-based permission control (CRUD operations with role-based access,
  optimistic locking with If-Match headers for all item operations, and comprehensive item management including update,
  move, check, and uncheck functionality)

## Codebase Structure

```
backend/
├── src/main/java/xyz/stasiak/recipai/
│   ├── RecipAiApplication.java          # Main Spring Boot application entry point
│   ├── recipes/                         # "recipes" module
│   │   ├── Recipe.java                  # Recipe entity
│   │   ├── RecipePermission.java        # User-Recipe association entity with roles
│   │   ├── RecipePermissionId.java      # Composite key for user-recipe associations
│   │   ├── UserRole.java                # Enum for OWNER/EDITOR roles
│   │   ├── RecipePermissionRepository.java # Role-based user-recipe data access
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
│   │   ├── RecipesExceptionHandler.java # Exception handling
│   │   └── collections/                 # "collections" submodule
│   │       ├── RecipesCollection.java           # RecipesCollection entity
│   │       ├── RecipesCollectionPermission.java # Collection permission association entity
│   │       ├── RecipesCollectionPermissionId.java # Composite key for collection permissions
│   │       ├── UserRole.java             # Enum for OWNER/EDITOR roles
│   │       ├── RecipesCollectionRepository.java # Collection data access
│   │       ├── RecipesCollectionPermissionRepository.java # Collection permission data access
│   │       ├── RecipesCollectionService.java    # Collection business logic
│   │       ├── RecipesCollectionController.java # Collection REST endpoints
│   │       ├── RecipesCollectionsExceptionHandler.java # Exception handling
│   │       ├── dto/                      # Data Transfer Objects
│   │       │   ├── RecipesCollectionListDto.java # Recipes collection list response DTO
│   │       │   ├── CreateRecipesCollectionRequest.java # Create recipes collection request DTO
│   │       │   └── UpdateRecipesCollectionRequest.java # Update recipes collection request DTO
│   │       └── exception/                # Custom exceptions
│   │           ├── RecipesCollectionNotFoundException.java # Collection not found exception
│   │           └── RecipesCollectionAccessDeniedException.java # Access denied exception
│   ├── extraction/                      # "extraction" module
│   ├── shoppinglists/                   # "shoppinglists" module
│   │   ├── ShoppingList.java            # Shopping list entity
│   │   ├── ShoppingListItem.java        # Shopping list item entity
│   │   ├── ShoppingListPermission.java  # Shopping list permission association entity
│   │   ├── ShoppingListPermissionId.java # Composite key for shopping list permissions
│   │   ├── UserRole.java                # Enum for OWNER/EDITOR roles
│   │   ├── ShoppingListRepository.java  # Shopping list data access
│   │   ├── ShoppingListItemRepository.java # Shopping list item data access
│   │   ├── ShoppingListPermissionRepository.java # Permission queries repository
│   │   ├── ShoppingListService.java     # Shopping list business logic with items and permissions
│   │   ├── ShoppingListController.java  # Shopping list REST endpoints with JWT authentication
│   │   ├── ShoppingListsExceptionHandler.java # Exception handling with ProblemDetail
│   │   ├── dto/                         # Data Transfer Objects
│   │   │   ├── ShoppingListListDto.java     # Shopping list list response DTO
│   │   │   ├── ShoppingListDto.java         # Shopping list detail response DTO with items
│   │   │   ├── ShoppingListItemDto.java     # Shopping list item response DTO
│   │   │   ├── CreateShoppingListRequest.java # Create shopping list request DTO
│   │   │   ├── UpdateShoppingListRequest.java # Update shopping list request DTO
│   │   │   ├── CreateShoppingListItemRequest.java # Create item request DTO
│   │   │   ├── UpdateShoppingListItemRequest.java # Update item request DTO
│   │   │   └── MoveShoppingListItemRequest.java # Move item request DTO
│   │   └── exception/                   # Custom exceptions
│   │       ├── ShoppingListNotFoundException.java # Shopping list not found exception
│   │       ├── ShoppingListAccessDeniedException.java # Access denied exception
│   │       ├── ShoppingListItemNotFoundException.java # Item not found exception
│   │       └── ShoppingListItemVersionMismatchException.java # Item version mismatch exception
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