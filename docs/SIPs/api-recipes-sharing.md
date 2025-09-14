# SIP: API Recipes Sharing Feature Implementation

## Goal

- Implement role-based recipe sharing API functionality in the Spring Boot backend
- Add ROLE column to user_recipes table to distinguish between OWNER and EDITOR roles
- Replace existing UserRecipesRepository methods with role-based access control methods
- Add role field to RecipeDto responses to indicate user's access level
- Create sharing endpoints to share/unshare recipes between users
- Ensure proper authorization checks for create/update/delete operations based on user roles

## Context

### Documentation and References

- **Spring Boot Role-Based Access Control**: https://blog.tericcabrel.com/role-base-access-control-spring-boot/
- **Spring Security Roles and Privileges**: https://www.baeldung.com/role-and-privilege-for-spring-security-registration
- **JPA Best Practices
  **: https://medium.com/@sharada.falane/journey-to-excellence-spring-boot-jpa-and-rest-api-best-practices-part-v-82c66c3153ed
- **Project Documentation**:
    - `docs/backend/backend.md` - Backend app overview with modular architecture
    - `docs/backend/api.md` - Current API endpoints and response formats
    - `docs/backend/db.md` - Database schema structure
- **Existing Codebase Patterns**:
    - `backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeService.java` - Business logic patterns
    - `backend/src/main/java/xyz/stasiak/recipai/recipes/UserRecipeRepository.java` - Repository query patterns
    - `backend/src/test/java/xyz/stasiak/recipai/recipes/RecipeIntegrationTest.java` - Testing patterns

### Current Codebase Tree

```
backend/src/main/java/xyz/stasiak/recipai/recipes/
├── Recipe.java                     # Recipe entity (recipes table)
├── UserRecipe.java                 # User-Recipe association (user_recipes table)
├── UserRecipeId.java               # Composite key for UserRecipe
├── UserRecipeRepository.java       # Data access for user-recipe associations
├── RecipeRepository.java           # Recipe data access
├── RecipeService.java              # Recipe business logic
├── RecipeController.java           # REST endpoints
├── RecipeDto.java                  # Recipe response DTO
├── RecipeListDto.java              # Recipe list response DTO  
├── CreateRecipeRequest.java        # Create recipe request DTO
├── UpdateRecipeRequest.java        # Update recipe request DTO
├── RecipeData.java                 # Recipe data structure
├── Ingredient.java                 # Ingredient model
├── Instruction.java                # Instruction model
├── RecipeNotFoundException.java    # Recipe not found exception
├── RecipeAccessDeniedException.java # Access denied exception
└── GlobalExceptionHandler.java     # Exception handling
```

### Desired Codebase Tree

```
backend/src/main/java/xyz/stasiak/recipai/recipes/
├── Recipe.java                     # Recipe entity (recipes table) - NO CHANGE
├── UserRecipe.java                 # User-Recipe association with ROLE column - MODIFIED
├── UserRecipeId.java               # Composite key for UserRecipe - NO CHANGE
├── UserRecipeRepository.java       # Role-based query methods - MODIFIED
├── RecipeRepository.java           # Recipe data access - NO CHANGE
├── RecipeService.java              # Enhanced with role checks & sharing - MODIFIED
├── RecipeController.java           # Enhanced with sharing endpoints - MODIFIED
├── RecipeDto.java                  # Enhanced with role field - MODIFIED
├── RecipeListDto.java              # Recipe list response DTO - NO CHANGE
├── CreateRecipeRequest.java        # Create recipe request DTO - NO CHANGE
├── UpdateRecipeRequest.java        # Update recipe request DTO - NO CHANGE
├── RecipeData.java                 # Recipe data structure - NO CHANGE
├── Ingredient.java                 # Ingredient model - NO CHANGE
├── Instruction.java                # Instruction model - NO CHANGE
├── RecipeNotFoundException.java    # Recipe not found exception - NO CHANGE
├── RecipeAccessDeniedException.java # Access denied exception - NO CHANGE
├── GlobalExceptionHandler.java     # Exception handling - NO CHANGE
├── UserRole.java                   # Enum for OWNER/EDITOR roles - NEW
├── ShareRecipeRequest.java         # Share recipe request DTO - NEW
└── UnshareRecipeRequest.java       # Unshare recipe request DTO - NEW
```

### Known Gotchas of Our Codebase and Library Quirks

- **JPA DDL Auto**: Project uses `hibernate.ddl-auto: update` so new ROLE column will be automatically added to database
- **Package-Private Visibility**: Following project conventions, most classes should be package-private unless they need
  to be public
- **Composite Keys**: UserRecipe uses @EmbeddedId with UserRecipeId composite key pattern
- **JWT Authentication**: Uses `@AuthenticationPrincipal Jwt jwt` and `jwt.getClaimAsString("email")` for user
  identification
- **Validation**: Uses `@Valid` on `@RequestBody` parameters with Bean Validation annotations
- **Testing**: Integration tests use `@SpringBootTest` with
  `@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class})`
- **Exception Handling**: Uses custom exceptions with `@ControllerAdvice` for centralized error handling
- **Enum Database Mapping**: JPA will automatically map Java enums to database using `@Enumerated(EnumType.STRING)` by
  default

## Implementation Plan

### Tasks

```
Task 1: Create UserRole enum for OWNER/EDITOR roles
  Action: CREATE
  File: backend/src/main/java/xyz/stasiak/recipai/recipes/UserRole.java
  Changes:
    - [ ] Create package-private enum with OWNER and EDITOR values
    - [ ] Follow existing code style patterns in the recipes package

Task 2: Add ROLE column to UserRecipe entity
  Action: MODIFY  
  File: backend/src/main/java/xyz/stasiak/recipai/recipes/UserRecipe.java
  Changes:
    - [ ] Add private UserRole role field with @Enumerated(EnumType.STRING) annotation
    - [ ] Add @Column annotation with nullable = false constraint
    - [ ] Update equals/hashCode to include role field
    - [ ] Add getter/setter methods using Lombok annotations

Task 3: Replace UserRecipeRepository methods with role-based queries
  Action: MODIFY
  File: backend/src/main/java/xyz/stasiak/recipai/recipes/UserRecipeRepository.java  
  Changes:
    - [ ] Remove existing existsByIdEmailAndIdRecipeId method
    - [ ] Remove existing doesNotExistByIdEmailAndIdRecipeId method
    - [ ] Add @Query("SELECT CASE WHEN COUNT(ur) > 0 THEN true ELSE false END FROM UserRecipe ur WHERE ur.id.email = ?1 AND ur.id.recipeId = ?2 AND ur.role = 'OWNER'") boolean isOwner(String email, UUID recipeId)
    - [ ] Add @Query("SELECT CASE WHEN COUNT(ur) > 0 THEN true ELSE false END FROM UserRecipe ur WHERE ur.id.email = ?1 AND ur.id.recipeId = ?2 AND ur.role = 'EDITOR'") boolean isEditor(String email, UUID recipeId)  
    - [ ] Add @Query("SELECT ur.role FROM UserRecipe ur WHERE ur.id.email = ?1 AND ur.id.recipeId = ?2") Optional<UserRole> getUserRole(String email, UUID recipeId)

Task 4: Add role field to RecipeDto
  Action: MODIFY
  File: backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeDto.java
  Changes:
    - [ ] Add UserRole role parameter to record constructor
    - [ ] Update record definition to include role field

Task 5: Create ShareRecipeRequest DTO
  Action: CREATE
  File: backend/src/main/java/xyz/stasiak/recipai/recipes/ShareRecipeRequest.java
  Changes:
    - [ ] Create package-private record with @Email annotated email field
    - [ ] Follow existing request DTO patterns from CreateRecipeRequest/UpdateRecipeRequest

Task 6: Create UnshareRecipeRequest DTO  
  Action: CREATE
  File: backend/src/main/java/xyz/stasiak/recipai/recipes/UnshareRecipeRequest.java
  Changes:
    - [ ] Create package-private record with @Email annotated email field
    - [ ] Follow existing request DTO patterns from CreateRecipeRequest/UpdateRecipeRequest

Task 7: Update RecipeService with role-based access control
  Action: MODIFY
  File: backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeService.java
  Changes:
    - [ ] Update findById to include role in RecipeDto response using getUserRole repository method
    - [ ] Update save method to set role = OWNER when creating UserRecipe association
    - [ ] Update updateById to check isOwner OR isEditor before allowing updates
    - [ ] Update deleteById to check isOwner only before allowing deletion  
    - [ ] Update toDto method to accept and include UserRole parameter
    - [ ] Add shareRecipe(String targetEmail, UUID recipeId, String ownerEmail) method
    - [ ] Add unshareRecipe(String targetEmail, UUID recipeId, String ownerEmail) method
    - [ ] Add proper error handling for sharing operations

Task 8: Add sharing endpoints to RecipeController
  Action: MODIFY
  File: backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeController.java
  Changes:
    - [ ] Add @PostMapping("/{id}/share") endpoint with @Valid ShareRecipeRequest
    - [ ] Add @PostMapping("/{id}/unshare") endpoint with @Valid UnshareRecipeRequest  
    - [ ] Extract user email from JWT token using existing pattern
    - [ ] Add proper logging for share/unshare operations
    - [ ] Return appropriate HTTP status codes (200 OK for success)

Task 9: Update integration tests
  Action: MODIFY
  File: backend/src/test/java/xyz/stasiak/recipai/recipes/RecipeIntegrationTest.java
  Changes:
    - [ ] Add test for sharing recipe functionality
    - [ ] Add test for unsharing recipe functionality
    - [ ] Add test for role-based access control (EDITOR can update but not delete)
    - [ ] Add test for owner-only delete operations
    - [ ] Use existing TestSecurityConfiguration.AUTH_TOKEN pattern for multi-user testing
    - [ ] Verify role field is present in RecipeDto responses
```

### Per Task Pseudocode

```java
// Task 7: RecipeService role-based access control pseudocode
public RecipeDto findById(UUID id, String userEmail) {
    Recipe recipe = findRecipeOrThrow(id);
    UserRole userRole = getUserRoleOrThrow(userEmail, id);
    return toDto(recipe, userRole);
}

public RecipeDto save(CreateRecipeRequest request, String userEmail) {
    Recipe recipe = createAndSaveRecipe(request);
    UserRecipe userRecipe = createUserRecipeAssociation(userEmail, recipe.getId(), UserRole.OWNER);
    userRecipeRepository.save(userRecipe);
    return toDto(recipe, UserRole.OWNER);
}

public void shareRecipe(String targetEmail, UUID recipeId, String ownerEmail) {
    validateOwnerAccess(ownerEmail, recipeId);
    validateRecipeExists(recipeId);
    validateTargetUserNotAlreadyShared(targetEmail, recipeId);

    UserRecipe sharedRecipe = createUserRecipeAssociation(targetEmail, recipeId, UserRole.EDITOR);
    userRecipeRepository.save(sharedRecipe);
}

public void unshareRecipe(String targetEmail, UUID recipeId, String ownerEmail) {
    validateOwnerAccess(ownerEmail, recipeId);
    validateTargetIsEditor(targetEmail, recipeId);

    UserRecipeId userRecipeId = new UserRecipeId(targetEmail, recipeId);
    userRecipeRepository.deleteById(userRecipeId);
}
```

## Validation

### Syntax and Style

```bash
# Run these FIRST - fix any errors before proceeding
mvn compile
# Expected: No compilation errors. If errors, READ the error and fix.
```

### Unit Tests

```bash
# Run and iterate until passing:
mvn test
# If failing: Read error, understand root cause, fix code, re-run (never mock to pass)
```

### Integration Tests

```bash
# Run and iterate until passing:
mvn test -Dtest=RecipeIntegrationTest
# If failing: Read error, understand root cause, fix code, re-run (never mock to pass)
```

## Integration Points

- **Database Schema**: New ROLE column will be automatically added to user_recipes table via Hibernate DDL auto-update
- **API Changes**:
    - RecipeDto responses will now include role field
    - Two new POST endpoints: /recipes/{id}/share and /recipes/{id}/unshare
- **Authorization**: Enhanced role-based access control for recipe operations:
    - OWNER: Can view, edit, delete, share, and unshare recipes
    - EDITOR: Can view and edit recipes only
- **Backwards Compatibility**: Existing recipes will need default OWNER role for current user associations

## Documentation

- **API Documentation**: `docs/backend/api.md` needs updating with:
    - Updated RecipeDto response format including role field
    - New POST /recipes/{id}/share endpoint documentation
    - New POST /recipes/{id}/unshare endpoint documentation
- **Database Documentation**: `docs/backend/db.md` needs updating with:
    - user_recipes table ROLE column (VARCHAR with OWNER/EDITOR values)

## Final Validation Checklist

- [ ] Correct syntax (mvn compile passes)
- [ ] All tests pass (mvn test passes)
- [ ] Manual test of sharing functionality successful
- [ ] Manual test of role-based access control successful
- [ ] Error cases handled gracefully (sharing to non-existent user, unsharing non-editor, etc.)
- [ ] Logs are informative but not verbose
- [ ] Documentation updated (api.md and db.md)
- [ ] Role field present in all RecipeDto responses

**SIP Confidence Score: 9/10** - Comprehensive context provided with existing code patterns, specific implementation
details, validation strategy, and clear integration points. Should enable one-pass implementation success.