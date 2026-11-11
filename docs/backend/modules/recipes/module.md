# Recipes Module

Manages user-scoped recipe CRUD, optional collection assignment, filtering by collection or
unassigned status, and image management (upload, reorder, delete). Also manages recipe collections:
CRUD with role-based access, sharing, and automatic removal of user-owned recipes from a collection
when unshared. Publishes a `RecipeDeleted` event when a recipe is deleted.

Access control and sharing for both recipes and collections are owned by the `permissions` module
(`docs/backend/modules/permissions/`). For a recipe, this module asks `PermissionsFacade` for the
direct role and composes it with collection-derived access itself — the one exception to that module
answering every access question (see `docs/ADRs/0007-shared-permissions-module.md`). For a collection,
every access question goes to `PermissionsFacade` directly.

## Codebase Structure

```
backend/src/main/java/xyz/stasiak/recipai/
└── recipes/
    ├── Recipe.java                          # Recipe entity
    ├── RecipeRepository.java                # Recipe data access over the caller's accessible ids (see Access Composition)
    ├── RecipeService.java                   # Recipe logic and images; resolveAccess (see Access Composition); owns the RECIPE key (see Limits)
    ├── RecipeController.java                # Recipe REST endpoints, including sharing, filtering and multipart image upload
    ├── RecipeFacade.java                    # Public facade — recipe lookups and the composed accessible ids (see Access Composition)
    ├── RecipeInfoResult.java                # Result record holding the list of RecipeInfo and the names of inaccessible recipes
    ├── RecipeInfo.java                      # Recipe snapshot record handed to other modules by RecipeFacade
    ├── RecipeDetailsDto.java                # Recipe details response DTO with images array and the caller's ResourceRole
    ├── RecipeListDto.java                   # Recipe list response DTO with thumbnail URL
    ├── CreateRecipeRequest.java             # Create recipe request DTO
    ├── UpdateRecipeRequest.java             # Update recipe request DTO with optional images list
    ├── RecipeData.java                      # Recipe data structure with optional sourceUrl
    ├── Ingredient.java                      # Ingredient model
    ├── Instruction.java                     # Instruction model
    ├── RecipeDeleted.java                   # Recipe deleted event record
    ├── RecipeNotFoundException.java         # Recipe not found exception
    ├── RecipesExceptionHandler.java         # 404 only; 403 for recipe access comes from PermissionsExceptionHandler
    ├── images/
    │   ├── RecipeImages.java                # Recipe images entity — stores image metadata, applies update/reorder/delete
    │   ├── Images.java                      # Value object for image metadata list with add/delete/reorder operations
    │   ├── RecipeImagesUpdated.java         # Value object for tracking image changes (toAdd and toDelete sets)
    │   ├── RecipeImagesRepository.java      # Recipe images data access
    │   ├── RecipeImagesService.java         # Image management service; catches config.s3.S3StorageException from ImageService
    │   ├── ImageService.java                # Image object-storage logic (keys under recipes/<recipeId>/) over the config.s3.S3Service seam
    │   ├── ImageProcessingService.java      # Image validation and thumbnail generation
    │   ├── ContentType.java                 # Content type value object
    │   ├── ImageMetadata.java               # Image metadata value object (id and extension)
    │   ├── dto/
    │   │   └── RecipeImageDto.java          # Recipe image response DTO with presigned URLs
    │   └── exception/
    │       ├── InvalidImageException.java
    │       ├── ImageLimitExceededException.java
    │       └── RecipeImagesExceptionHandler.java
    └── collections/
        ├── RecipesCollection.java                       # RecipesCollection entity
        ├── RecipesCollectionRepository.java             # Collection data access
        ├── RecipesCollectionsFacade.java                # Public facade — the account-deletion purge (see settings/module.md)
        ├── RecipesCollectionService.java                # Collection logic, sharing and unshare cleanup; owns the RECIPES_COLLECTION key (see Limits)
        ├── RecipesCollectionController.java             # Collection REST endpoints
        ├── RecipesCollectionsExceptionHandler.java      # 404 only; 403 for collection access comes from PermissionsExceptionHandler
        ├── dto/
        │   ├── RecipesCollectionListDto.java            # Recipes collection list response DTO
        │   ├── CreateRecipesCollectionRequest.java
        │   ├── UpdateRecipesCollectionRequest.java
        │   └── RecipesCollectionUnshared.java           # Event published when a user is unshared from a collection
        └── exception/
            └── RecipesCollectionNotFoundException.java
```

Sharing types for recipes and collections (`ResourceRole`, `PermissionDto`, `ShareRequest`,
`UnshareRequest`) and the access-denied exception live in `permissions` — see
`docs/backend/modules/permissions/module.md`.

## Access Composition

`RecipeService.resolveAccess` asks `PermissionsFacade.roleOf` for a direct permission first; a direct
row (`OWNER` or `EDITOR`) always wins outright. Absent one, a recipe assigned to a collection falls
back to asking `RecipesCollectionService.roleOf` for that collection — the
`RECIPES_COLLECTION_RESOURCE` key never leaves `collections` — and a caller who holds any role there
is given a synthetic `EDITOR`, never materialised as a row. A caller who reaches neither is refused.
Composition never lowers an answer: a direct `EDITOR` on a recipe in a collection the caller also owns
still reads `EDITOR`, not the composed answer. Listing applies the same rule in bulk: a recipe is
reachable when the caller holds a direct permission on it or any role on its collection. That
composed set of recipe ids is what `RecipeFacade` exposes to other modules and filters its own
lookups on. `getPermissions` only ever asks the direct half — a collection-derived reader never appears in a
recipe's permissions list, and inviting someone who already reaches a recipe through a shared
collection still creates a direct invite, since the refusal rules see only granted rows. See
`docs/ADRs/0007-shared-permissions-module.md`.

## Limits

Creating a recipe or a collection consumes one unit of the owner's `RECIPE` or `RECIPES_COLLECTION`
budget, reserved before anything is written and keyed by the `email` claim of the JWT. Deleting one
returns the unit. Both are stock quotas: a refusal does not resolve itself by waiting, and only
creation is blocked — reading, editing and sharing keep working while the owner is over the quota.
Sharing never charges the recipient (including one who has merely been invited to a recipe or
collection, but not yet accepted), and editing a shared record spends nothing; a recipe an EDITOR
creates in someone else's collection is charged to the EDITOR, who owns it. See
`docs/backend/modules/limits/` for how the quotas are configured and changed.
