# Backend App Overview - RecipAI

## Modules

- `recipes` - manages user-scoped recipe CRUD operations with role-based sharing functionality, optional collection
  assignment, collection-based access control within that collection), filtering capabilities (by collection or
  unassigned status), and support for recipe images (with update, reorder, and delete operations) and source URLs
- `recipes.collections` - manages recipes collections with user-based permission control (CRUD operations with
  role-based access, sharing functionality with OWNER/EDITOR roles, automatic removal of user-owned recipes from
  collection when unshared)
- `recipes.images` - manages recipe image storage and retrieval with S3 integration, automatic thumbnail generation, and
  presigned URL generation for secure image access (maximum 2 images per recipe)
- `extraction` - extracts recipes from text/images using AI
- `shoppinglists` - manages shopping lists with user-based permission control (CRUD operations with role-based access,
  optimistic locking with If-Match headers for all item operations, and comprehensive item management including update,
  move, check, and uncheck functionality)
- `planning` - manages meal plans with user-based permission control (CRUD operations with role-based access, meal plan
  entries with recipe or placeholder support, limit for owner plans)
- `config.s3` - provides S3 client configuration for AWS SDK integration with presigned URL support
- `config.security` - handles OAuth2 Resource Server authentication with JWT tokens

## Codebase Structure

```
backend/
├── src/main/java/xyz/stasiak/recipai/
│   ├── RecipAiApplication.java          # Main Spring Boot application entry point
│   ├── config/                          # Configuration modules
│   │   ├── s3/                          # S3 configuration
│   │   │   ├── S3Config.java            # S3 client and presigner bean configuration
│   │   │   └── S3Properties.java        # S3 configuration properties (bucket name, region, presigned URL expiration)
│   │   └── security/                    # Security configuration
│   │       └── SecurityConfig.java      # OAuth2 Resource Server configuration
│   ├── recipes/                         # "recipes" module
│   │   ├── Recipe.java                  # Recipe entity
│   │   ├── RecipePermission.java        # User-Recipe association entity with roles
│   │   ├── RecipePermissionId.java      # Composite key for user-recipe associations
│   │   ├── UserRole.java                # Enum for OWNER/EDITOR roles
│   │   ├── RecipePermissionRepository.java # Role-based user-recipe data access
│   │   ├── RecipeRepository.java        # Recipe data access with user filtering (all, by collection, unassigned, accessible)
│   │   ├── RecipeService.java           # Recipe business logic with role-based sharing, collection assignment validation, collection-based access control, and image management (upload, update, reorder, delete)
│   │   ├── RecipeController.java        # Recipe REST endpoints with sharing, filtering, multipart image upload support, and JSON/multipart update endpoints
│   │   ├── RecipeDetailsDto.java        # Recipe details response DTO with images array
│   │   ├── RecipeListDto.java           # Recipe list response DTO with thumbnail URL
│   │   ├── CreateRecipeRequest.java     # Create recipe request DTO
│   │   ├── UpdateRecipeRequest.java     # Update recipe request DTO with optional images list
│   │   ├── ShareRecipeRequest.java      # Share recipe request DTO
│   │   ├── UnshareRecipeRequest.java    # Unshare recipe request DTO
│   │   ├── RecipeData.java              # Recipe data structure with optional sourceUrl
│   │   ├── Ingredient.java              # Ingredient model
│   │   ├── Instruction.java             # Instruction model
│   │   ├── RecipeNotFoundException.java # Recipe not found exception
│   │   ├── RecipeAccessDeniedException.java # Access denied exception
│   │   ├── ErrorResponse.java           # Error response DTO
│   │   ├── RecipesExceptionHandler.java # Exception handling (404, 403, 400 errors)
│   │   ├── images/                      # "images" submodule
│   │   │   ├── RecipeImages.java        # Recipe images entity (stores image metadata, handles update/reorder/delete operations)
│   │   │   ├── Images.java              # Value object for image metadata list with add/delete/reorder operations
│   │   │   ├── RecipeImagesUpdated.java # Value object for tracking image changes (toAdd and toDelete sets)
│   │   │   ├── RecipeImagesRepository.java # Recipe images data access
│   │   │   ├── RecipeImagesService.java # Image management service (upload, update with add/delete/reorder, retrieve, delete all)
│   │   │   ├── S3Service.java           # S3 operations service (upload, presigned URLs, delete single/all images)
│   │   │   ├── ImageProcessingService.java # Image validation and thumbnail generation
│   │   │   ├── ContentType.java         # Content type value object
│   │   │   ├── ImageMetadata.java       # Image metadata value object (id and extension)
│   │   │   ├── dto/                     # Data Transfer Objects
│   │   │   │   └── RecipeImageDto.java  # Recipe image response DTO with presigned URLs
│   │   │   └── exception/               # Custom exceptions
│   │   │       ├── InvalidImageException.java # Invalid image exception
│   │   │       ├── ImageLimitExceededException.java # Image limit exceeded exception
│   │   │       ├── S3StorageException.java # S3 storage exception
│   │   │       └── RecipeImagesExceptionHandler.java # Exception handling for images
│   │   └── collections/                 # "collections" submodule
│   │       ├── RecipesCollection.java           # RecipesCollection entity
│   │       ├── RecipesCollectionPermission.java # Collection permission association entity
│   │       ├── RecipesCollectionPermissionId.java # Composite key for collection permissions
│   │       ├── UserRole.java             # Enum for OWNER/EDITOR roles
│   │       ├── RecipesCollectionRepository.java # Collection data access
│   │       ├── RecipesCollectionPermissionRepository.java # Collection permission data access
│   │       ├── RecipesCollectionService.java    # Collection business logic with automatic removal of user-owned recipes when unshared
│   │       ├── RecipesCollectionController.java # Collection REST endpoints
│   │       ├── RecipesCollectionsExceptionHandler.java # Exception handling
│   │       ├── dto/                      # Data Transfer Objects
│   │       │   ├── RecipesCollectionListDto.java # Recipes collection list response DTO
│   │       │   ├── CreateRecipesCollectionRequest.java # Create recipes collection request DTO
│   │       │   ├── UpdateRecipesCollectionRequest.java # Update recipes collection request DTO
│   │       │   ├── ShareRecipesCollectionRequest.java # Share recipes collection request DTO
│   │       │   ├── UnshareRecipesCollectionRequest.java # Unshare recipes collection request DTO
│   │       │   └── SharedUserDto.java # Shared user response DTO with role
│   │       └── exception/                # Custom exceptions
│   │           ├── RecipesCollectionNotFoundException.java # Collection not found exception
│   │           └── RecipesCollectionAccessDeniedException.java # Access denied exception
│   ├── planning/                        # "planning" module
│   │   ├── MealPlan.java              # Meal plan entity
│   │   ├── MealPlanEntry.java         # Meal plan entry entity
│   │   ├── MealPlanPermission.java    # Meal plan permission association entity
│   │   ├── MealPlanPermissionId.java  # Composite key for meal plan permissions
│   │   ├── UserRole.java             # Enum for OWNER/EDITOR roles
│   │   ├── MealPlanRepository.java   # Meal plan data access
│   │   ├── MealPlanEntryRepository.java # Meal plan entry data access
│   │   ├── MealPlanPermissionRepository.java # Permission queries repository
│   │   ├── MealPlanService.java      # Meal plan business logic with entries and permissions
│   │   ├── MealPlanController.java   # Meal plan REST endpoints with JWT authentication
│   │   ├── PlanningExceptionHandler.java # Exception handling with ProblemDetail
│   │   ├── dto/                       # Meal Planning Data Transfer Objects
│   │   └── exception/                 # Meal Planning custom exceptions
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
- `AWS_ACCESS_KEY_ID` - AWS access key ID for S3 operations
- `AWS_SECRET_ACCESS_KEY` - AWS secret access key for S3 operations

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