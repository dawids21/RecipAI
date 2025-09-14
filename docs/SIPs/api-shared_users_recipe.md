# SIP: API Endpoint for Shared Users of Recipe

## Goal

- Add a new REST API endpoint `GET /recipes/{id}/shared_users` that returns all users that a recipe is shared with
- Include roles (OWNER or EDITOR) for each user in the response, with OWNER appearing first in the list
- Ensure proper authentication and authorization using existing JWT patterns
- Follow existing codebase patterns for consistency and maintainability
- Success criteria: Endpoint returns JSON list with email and role fields, accessible only to users with recipe access,
  OWNER listed first

## Context

### Documentation and References

- **Feature Request**: `docs/feature-requests/api-shared_users_recipe.md` - Complete feature specification and JSON
  response format
- **Backend Overview**: `docs/backend/backend.md` - Understanding of modular architecture and role-based sharing
  functionality
- **API Documentation**: `docs/backend/api.md` - Current REST endpoints pattern and authentication requirements
- **Database Schema**: `docs/backend/db.md` - user_recipes table structure with email, recipe_id, role columns
- **Spring Boot REST Best Practices**: https://amigoscode.com/blogs/top-10-spring-boot-rest-api-best-practices - DTOs,
  exception handling
- **Baeldung JPA Relationships**: https://www.baeldung.com/spring-data-rest-relationships - Repository query patterns

### Current Codebase Tree

```
backend/src/main/java/xyz/stasiak/recipai/recipes/
├── RecipeController.java          # REST endpoints - pattern to follow
├── RecipeService.java             # Business logic - add new method here
├── UserRecipeRepository.java      # Data access - add new query method
├── RecipeDto.java                 # Existing response DTO pattern
├── UserRole.java                  # OWNER/EDITOR enum - use in response
├── UserRecipe.java                # Entity mapping user_recipes table
└── [Other DTOs and entities]      # Pattern references
```

### Desired Codebase Tree

```
backend/src/main/java/xyz/stasiak/recipai/recipes/
├── RecipeController.java          # [MODIFY] Add GET /{id}/shared_users endpoint
├── RecipeService.java             # [MODIFY] Add getSharedUsers method
├── UserRecipeRepository.java      # [MODIFY] Add findAllByRecipeId query method
├── SharedUserDto.java             # [CREATE] New DTO record for response
├── RecipeDto.java                 # [UNCHANGED] Reference pattern
├── UserRole.java                  # [UNCHANGED] Use in response
├── UserRecipe.java                # [UNCHANGED] Source entity
└── [Other files unchanged]
```

### Known Gotchas of Our Codebase and Library Quirks

- **Package-private visibility**: Most classes use package-private visibility unless they need to be public
- **Constructor injection**: Use `@RequiredArgsConstructor` with private final fields for dependency injection
- **DTO as records**: All DTOs are immutable Java records, not classes with getters/setters
- **JWT authentication**: Use `@AuthenticationPrincipal Jwt jwt` and `jwt.getClaimAsString("email")` pattern
- **Exception handling**: Use existing `RecipeNotFoundException` and `RecipeAccessDeniedException` patterns
- **Logging**: Use SLF4J with `@Slf4j` and debug/info levels appropriately
- **Repository queries**: Use `@Query` annotations for custom JPA queries when needed
- **Access control**: Always validate user has recipe access before returning any related data

## Implementation Plan

### Tasks

```
Task 1: Create SharedUserDto record
  Action: CREATE
  File: backend/src/main/java/xyz/stasiak/recipai/recipes/SharedUserDto.java
  Changes:
    - [ ] Create package-private record with email (String) and role (UserRole) fields
    - [ ] Follow existing DTO patterns like RecipeDto.java
    - [ ] Use UserRole enum directly for type safety

Task 2: Add repository method for querying shared users
  Action: MODIFY  
  File: backend/src/main/java/xyz/stasiak/recipai/recipes/UserRecipeRepository.java
  Changes:
    - [ ] Add @Query method to find all UserRecipe entities by recipe ID
    - [ ] Follow existing query patterns like getUserRole method
    - [ ] Return List<UserRecipe> to get both email and role information

Task 3: Add service method for business logic
  Action: MODIFY
  File: backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeService.java
  Changes:
    - [ ] Add getSharedUsers(UUID recipeId, String userEmail) method
    - [ ] Validate recipe exists using existing recipeRepository.existsById pattern
    - [ ] Validate user has recipe access using userRecipeRepository.getUserRole pattern
    - [ ] Map UserRecipe entities to SharedUserDto records
    - [ ] Add debug logging following existing patterns

Task 4: Add controller endpoint
  Action: MODIFY
  File: backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeController.java
  Changes:
    - [ ] Add @GetMapping("/{id}/shared_users") method
    - [ ] Use @PathVariable UUID id and @AuthenticationPrincipal Jwt jwt parameters
    - [ ] Extract userEmail from JWT using jwt.getClaimAsString("email") pattern
    - [ ] Call recipeService.getSharedUsers and return List<SharedUserDto>
    - [ ] Add debug logging following existing patterns

Task 5: Add endpoint testing to existing shouldShareAndUnshareRecipes test
  Action: MODIFY
  File: backend/src/test/java/xyz/stasiak/recipai/recipes/RecipeIntegrationTest.java
  Changes:
    - [ ] Add shared users endpoint test within existing shouldShareAndUnshareRecipes method
    - [ ] Test shared users list after sharing (should show OWNER first, then EDITOR)
    - [ ] Test shared users list after unsharing (should show only OWNER)
    - [ ] Use existing test setup and multi-user authentication tokens
    - [ ] Verify JSON response format matches feature specification
    - [ ] Verify OWNER appears first in the returned list
```

### Per Task Pseudocode

```java
// Task 1: SharedUserDto.java
public record SharedUserDto(String email, UserRole role) {
}

// Task 2: UserRecipeRepository.java addition
@Query("SELECT ur FROM UserRecipe ur WHERE ur.id.recipeId = ?1 ORDER BY ur.role DESC")
List<UserRecipe> findAllByRecipeId(UUID recipeId);

// Task 3: RecipeService.java addition
public List<SharedUserDto> getSharedUsers(UUID recipeId, String userEmail) {
    log.debug("Getting shared users for recipe: {} by user: {}", recipeId, userEmail);

    // Validate recipe exists
    if (!recipeRepository.existsById(recipeId)) {
        throw new RecipeNotFoundException(recipeId);
    }

    // Validate user has access
    userRecipeRepository.getUserRole(userEmail, recipeId)
            .orElseThrow(() -> new RecipeAccessDeniedException(recipeId));

    // Get all users with access to this recipe (OWNER first due to ORDER BY role DESC)
    return userRecipeRepository.findAllByRecipeId(recipeId).stream()
            .map(userRecipe -> new SharedUserDto(userRecipe.getId().getEmail(), userRecipe.getRole()))
            .toList();
}

// Task 4: RecipeController.java addition
@GetMapping("/{id}/shared_users")
public List<SharedUserDto> getSharedUsers(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    String userEmail = jwt.getClaimAsString("email");
    log.debug("Getting shared users for recipe: {} by user: {}", id, userEmail);
    return recipeService.getSharedUsers(id, userEmail);
}
```

## Validation

### Syntax and Style

```bash
# Run these FIRST - fix any errors before proceeding
cd backend
mvn compile

# Expected: No compilation errors. If errors, READ the error and fix.
```

### Unit Tests

```bash
# Run and iterate until passing:
cd backend  
mvn test

# If failing: Read error, understand root cause, fix code, re-run (never mock to pass)
```

### Integration Tests

```bash
# Run and iterate until passing:
cd backend
mvn test -Dtest=RecipeIntegrationTest

# If failing: Read error, understand root cause, fix code, re-run (never mock to pass)
```

## Integration Points

- **API Addition**: New endpoint `GET /recipes/{id}/shared_users` added to recipes resource
- **Database Query**: New query to user_recipes table via UserRecipeRepository
- **Authentication**: Uses existing JWT authentication with email claim extraction
- **Authorization**: Leverages existing recipe access control patterns
- **JSON Response**: Returns array of objects with email (string) and role (OWNER/EDITOR) fields
- **Error Handling**: Reuses existing RecipeNotFoundException and RecipeAccessDeniedException

## Documentation

- **API Documentation**: Update `docs/backend/api.md` to include new endpoint specification
- **Add endpoint description, authentication requirements, example request/response**
- **Document success (200 OK) and error responses (403 Forbidden, 404 Not Found)**
- **No CLAUDE.md updates needed** - endpoint follows existing patterns

## Final Validation Checklist

- [ ] Correct syntax (mvn compile passes)
- [ ] All tests pass (mvn test passes)
- [ ] Manual test successful (endpoint returns expected JSON format)
- [ ] Error cases handled gracefully (403 for no access, 404 for missing recipe)
- [ ] Logs are informative but not verbose (debug level for operations)
- [ ] API documentation updated with new endpoint details

---

**SIP Confidence Score: 9/10** - High confidence for one-pass implementation success. Clear patterns to follow, minimal
complexity, comprehensive context provided. Only minor risk is potential edge cases in multi-user testing scenarios.