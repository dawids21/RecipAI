# Recipes Module — Codebase Structure

```
backend/src/main/java/xyz/stasiak/recipai/
└── recipes/
    ├── Recipe.java                          # Recipe entity
    ├── RecipePermission.java                # User-Recipe association entity with roles
    ├── RecipePermissionId.java              # Composite key for user-recipe associations
    ├── UserRole.java                        # Enum for OWNER/EDITOR roles
    ├── RecipePermissionRepository.java      # Role-based user-recipe data access
    ├── RecipeRepository.java                # Recipe data access with user filtering (all, by collection, unassigned, accessible)
    ├── RecipeService.java                   # Recipe business logic with role-based sharing, collection assignment validation, collection-based access control, and image management (upload, update, reorder, delete); owns the RECIPE resource key, reserving one unit on create and releasing one on delete
    ├── RecipeController.java                # Recipe REST endpoints with sharing, filtering, multipart image upload support, and JSON/multipart update endpoints
    ├── RecipeFacade.java                    # Public facade for use by other modules
    ├── RecipeIngredientsResult.java         # Result record holding list of RecipeWithIngredients and names of inaccessible recipes
    ├── RecipeWithIngredients.java           # Record holding recipeId, servingSize, and ingredients for a single recipe
    ├── RecipeDetailsDto.java                # Recipe details response DTO with images array
    ├── RecipeListDto.java                   # Recipe list response DTO with thumbnail URL
    ├── CreateRecipeRequest.java             # Create recipe request DTO
    ├── UpdateRecipeRequest.java             # Update recipe request DTO with optional images list
    ├── ShareRecipeRequest.java              # Share recipe request DTO
    ├── UnshareRecipeRequest.java            # Unshare recipe request DTO
    ├── RecipeData.java                      # Recipe data structure with optional sourceUrl
    ├── Ingredient.java                      # Ingredient model
    ├── Instruction.java                     # Instruction model
    ├── RecipeDeleted.java                   # Recipe deleted event record
    ├── RecipeNotFoundException.java         # Recipe not found exception
    ├── RecipeAccessDeniedException.java     # Access denied exception
    ├── ErrorResponse.java                   # Error response DTO
    ├── RecipesExceptionHandler.java         # Exception handling (404, 403, 400 errors)
    ├── images/
    │   ├── RecipeImages.java                # Recipe images entity (stores image metadata, handles update/reorder/delete operations)
    │   ├── Images.java                      # Value object for image metadata list with add/delete/reorder operations
    │   ├── RecipeImagesUpdated.java         # Value object for tracking image changes (toAdd and toDelete sets)
    │   ├── RecipeImagesRepository.java      # Recipe images data access
    │   ├── RecipeImagesService.java         # Image management service (upload, update with add/delete/reorder, retrieve, delete all)
    │   ├── S3Service.java                   # S3 operations service (upload, presigned URLs, delete single/all images)
    │   ├── ImageProcessingService.java      # Image validation and thumbnail generation
    │   ├── ContentType.java                 # Content type value object
    │   ├── ImageMetadata.java               # Image metadata value object (id and extension)
    │   ├── dto/
    │   │   └── RecipeImageDto.java          # Recipe image response DTO with presigned URLs
    │   └── exception/
    │       ├── InvalidImageException.java
    │       ├── ImageLimitExceededException.java
    │       ├── S3StorageException.java
    │       └── RecipeImagesExceptionHandler.java
    └── collections/
        ├── RecipesCollection.java                       # RecipesCollection entity
        ├── RecipesCollectionPermission.java             # Collection permission association entity
        ├── RecipesCollectionPermissionId.java           # Composite key for collection permissions
        ├── UserRole.java                                # Enum for OWNER/EDITOR roles
        ├── RecipesCollectionRepository.java             # Collection data access
        ├── RecipesCollectionPermissionRepository.java   # Collection permission data access
        ├── RecipesCollectionService.java                # Collection business logic with automatic removal of user-owned recipes when unshared; owns the RECIPES_COLLECTION resource key, reserving one unit on create and releasing one on delete
        ├── RecipesCollectionController.java             # Collection REST endpoints
        ├── RecipesCollectionsExceptionHandler.java      # Exception handling
        ├── dto/
        │   ├── RecipesCollectionListDto.java            # Recipes collection list response DTO
        │   ├── CreateRecipesCollectionRequest.java
        │   ├── UpdateRecipesCollectionRequest.java
        │   ├── ShareRecipesCollectionRequest.java
        │   ├── UnshareRecipesCollectionRequest.java
        │   └── SharedUserDto.java                       # Shared user response DTO with role
        └── exception/
            ├── RecipesCollectionNotFoundException.java
            └── RecipesCollectionAccessDeniedException.java
```
