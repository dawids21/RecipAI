# Recipes Module

Manages user-scoped recipe CRUD, optional collection assignment, filtering by collection or
unassigned status, and image management (upload, reorder, delete). Also manages recipe collections:
CRUD with role-based access, sharing, and automatic removal of user-owned recipes from a collection
when unshared. Publishes a `RecipeDeleted` event when a recipe is deleted.

Direct recipe access control and sharing are owned by the `permissions` module
(`docs/backend/modules/permissions/`); this module asks `PermissionsFacade` for the direct role and
composes it with collection-derived access itself — the one exception to that module answering every
access question (see `docs/ADRs/0007-shared-permissions-module.md`). Recipe collections keep their own
permission table and access checks, separate from `permissions`.

## Codebase Structure

```
backend/src/main/java/xyz/stasiak/recipai/
└── recipes/
    ├── Recipe.java                          # Recipe entity
    ├── RecipeRepository.java                # Recipe data access with user filtering (all, by collection, unassigned, accessible) — findAllByUserEmail/findAllUnassignedByUserEmail take accessible recipe ids plus the surviving RecipesCollectionPermission join
    ├── RecipeService.java                   # Recipe business logic with collection assignment validation, image management (upload, update, reorder, delete), and resolveAccess() composing PermissionsFacade's direct role with collection-derived access; owns the RECIPE resource key, reserving one unit on create and releasing one on delete
    ├── RecipeController.java                # Recipe REST endpoints with sharing, filtering, multipart image upload support, and JSON/multipart update endpoints
    ├── RecipeFacade.java                    # Public facade for use by other modules
    ├── RecipeIngredientsResult.java         # Result record holding list of RecipeWithIngredients and names of inaccessible recipes
    ├── RecipeWithIngredients.java           # Record holding recipeId, servingSize, and ingredients for a single recipe
    ├── RecipeDetailsDto.java                # Recipe details response DTO with images array and the caller's ResourceRole
    ├── RecipeListDto.java                   # Recipe list response DTO with thumbnail URL
    ├── CreateRecipeRequest.java             # Create recipe request DTO
    ├── UpdateRecipeRequest.java             # Update recipe request DTO with optional images list
    ├── RecipeData.java                      # Recipe data structure with optional sourceUrl
    ├── Ingredient.java                      # Ingredient model
    ├── Instruction.java                     # Instruction model
    ├── RecipeDeleted.java                   # Recipe deleted event record
    ├── RecipeNotFoundException.java         # Recipe not found exception
    ├── RecipesExceptionHandler.java         # Exception handling (404 only; 403 for recipe access comes from PermissionsExceptionHandler)
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

Sharing types for recipes (`ResourceRole`, `PermissionDto`, `ShareRequest`, `UnshareRequest`) and the
access-denied exception live in `permissions` — see `docs/backend/modules/permissions/module.md`.
Recipe collections carry their own `UserRole` and `SharedUserDto`, separate from `permissions`.

## Access Composition

`RecipeService.resolveAccess` asks `PermissionsFacade.roleOf` for a direct permission first; a direct
row (`OWNER` or `EDITOR`) always wins outright. Absent one, a recipe assigned to a collection falls
back to asking `RecipesCollectionService.findById` whether the caller can reach that collection — a
caller who can is given a synthetic `EDITOR`, never materialised as a row. A caller who can reach
neither is refused. Composition never lowers an answer: a direct `EDITOR` on a recipe in a collection
the caller also owns still reads `EDITOR`, not the composed answer. `getPermissions` and the
`findAll`/`findAllUnassigned` list queries only ever ask the direct half through the facade — a
collection-derived reader never appears in a recipe's permissions list, and inviting someone who
already reaches a recipe through a shared collection still creates a direct invite, since the refusal
rules see only granted rows. See `docs/ADRs/0007-shared-permissions-module.md`.

## Limits

Creating a recipe or a collection consumes one unit of the owner's `RECIPE` or `RECIPES_COLLECTION`
budget, reserved before anything is written and keyed by the `email` claim of the JWT. Deleting one
returns the unit. Both are stock quotas: a refusal does not resolve itself by waiting, and only
creation is blocked — reading, editing and sharing keep working while the owner is over the quota.
Sharing never charges the recipient (including one who has merely been invited to a recipe, but not
yet accepted), and editing a shared record spends nothing; a recipe an EDITOR creates in someone
else's collection is charged to the EDITOR, who owns it. See `docs/backend/modules/limits/` for how
the quotas are configured and changed.
