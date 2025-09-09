# SIP: API Recipes Per User

## Goal

- Transform recipe management from shared-among-all-users to user-specific recipes
- Implement many-to-many relationship between users and recipes using compound key
- Extract user email from JWT tokens at controller level for authentication-based operations
- Ensure users can only access/modify their own recipes
- Create separate Users module with minimal functionality
- Maintain existing API structure while adding user-based authorization

## Context

### Documentation and References

- [Spring Security JWT Principal Extraction](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [JPA Many-to-Many Best Practices](https://www.baeldung.com/jpa-many-to-many)
- [Spring Data JPA Compound Keys](https://www.baeldung.com/jpa-composite-primary-keys)
- Current codebase pattern: backend/src/main/java/xyz/stasiak/recipai/recipes/Recipe.java:1
- Test security setup: backend/src/test/java/xyz/stasiak/recipai/TestSecurityConfiguration.java:1
- Integration test pattern: backend/src/test/java/xyz/stasiak/recipai/recipes/RecipeIntegrationTest.java:1
- Backend coding guidelines: backend/CLAUDE.md

### Current Codebase Tree

```
backend/src/main/java/xyz/stasiak/recipai/
├── RecipAiApplication.java
├── extraction/
├── recipes/
│   ├── Recipe.java                  # Current entity without user association
│   ├── RecipeRepository.java        # JpaRepository<Recipe, UUID>
│   ├── RecipeService.java           # Business logic without user context
│   ├── RecipeController.java        # No user authentication extraction
│   ├── RecipeDto.java
│   ├── CreateRecipeRequest.java
│   ├── UpdateRecipeRequest.java
│   └── [other recipe-related classes]
└── security/
    └── SecurityConfig.java          # OAuth2 Resource Server configuration
```

### Desired Codebase Tree

```
backend/src/main/java/xyz/stasiak/recipai/
├── RecipAiApplication.java
├── extraction/
├── users/                           # NEW MODULE
│   ├── User.java                    # Entity with email as PK
│   ├── UserRepository.java          # JpaRepository with create method
│   └── UserController.java          # Register endpoint
├── recipes/
│   ├── Recipe.java                  # Unchanged entity
│   ├── UserRecipe.java              # NEW: Join entity with compound key
│   ├── UserRecipeId.java            # NEW: Composite key class
│   ├── UserRecipeRepository.java    # NEW: Repository for user-recipe associations
│   ├── RecipeRepository.java        # Updated with user-filtering queries
│   ├── RecipeService.java           # Updated with user context
│   ├── RecipeController.java        # Updated to extract JWT email
│   └── [existing DTOs and requests unchanged]
└── security/
    └── SecurityConfig.java          # Updated to secure /users endpoint
```

### Known Gotchas of Our Codebase and Library Quirks

- Use package-private visibility for most classes (following current pattern)
- Use `@RequiredArgsConstructor` for dependency injection (from backend/CLAUDE.md)
- Use `record` types for DTOs (from backend/CLAUDE.md)
- Use `@Valid` on `@RequestBody` parameters (from backend/CLAUDE.md)
- Use SLF4J `@Slf4j` for logging (from backend/CLAUDE.md)
- TestContainers setup requires `@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class})`
- Spring Security OAuth2 Resource Server extracts JWT claims via `@AuthenticationPrincipal Jwt jwt`
- JPA compound keys require `@Embeddable` and `@EmbeddedId` annotations
- Use `Set<>` instead of `List<>` for many-to-many relationships (JPA best practice)

## Implementation Plan

### Tasks

```
Task 1: Create Users module - User entity
  Action: CREATE
  File: backend/src/main/java/xyz/stasiak/recipai/users/User.java
  Changes:
    - [ ] Create package-private User entity class
    - [ ] Use email as @Id (String type, no UUID generation)
    - [ ] Use Lombok @Getter, @Setter, @ToString, @NoArgsConstructor
    - [ ] Follow Recipe.java pattern for equals/hashCode implementation
    - [ ] Use @Table(name = "users") annotation

Task 2: Create Users module - User repository
  Action: CREATE
  File: backend/src/main/java/xyz/stasiak/recipai/users/UserRepository.java
  Changes:
    - [ ] Create package-private interface extending JpaRepository<User, String>
    - [ ] Follow RecipeRepository.java pattern (minimal interface)

Task 3: Create Users module - User controller
  Action: CREATE
  File: backend/src/main/java/xyz/stasiak/recipai/users/UserController.java
  Changes:
    - [ ] Create package-private @RestController with @RequestMapping("/users")
    - [ ] Implement POST /users/register endpoint
    - [ ] Extract email from @AuthenticationPrincipal Jwt jwt
    - [ ] Use repository directly (no service class)
    - [ ] Return 200 OK even if user already exists
    - [ ] Follow RecipeController.java patterns (@Slf4j, @RequiredArgsConstructor)

Task 4: Update SecurityConfig for users endpoint
  Action: MODIFY
  File: backend/src/main/java/xyz/stasiak/recipai/security/SecurityConfig.java
  Changes:
    - [ ] Add "/users/**" to authenticated requestMatchers
    - [ ] Update line 24: .requestMatchers("/recipes/**", "/extract/**", "/users/**").authenticated()

Task 5: Create UserRecipe compound key class
  Action: CREATE
  File: backend/src/main/java/xyz/stasiak/recipai/recipes/UserRecipeId.java
  Changes:
    - [ ] Create package-private @Embeddable record
    - [ ] Include String email and UUID recipeId fields
    - [ ] Implement Serializable interface

Task 6: Create UserRecipe join entity
  Action: CREATE
  File: backend/src/main/java/xyz/stasiak/recipai/recipes/UserRecipe.java
  Changes:
    - [ ] Create package-private @Entity with @Table(name = "user_recipes")
    - [ ] Use @EmbeddedId with UserRecipeId
    - [ ] Use Lombok @Getter, @Setter, @ToString, @NoArgsConstructor
    - [ ] Implement proper equals/hashCode based on composite key

Task 7: Create UserRecipe repository
  Action: CREATE
  File: backend/src/main/java/xyz/stasiak/recipai/recipes/UserRecipeRepository.java
  Changes:
    - [ ] Create package-private interface extending JpaRepository<UserRecipe, UserRecipeId>
    - [ ] Add method: List<UserRecipe> findByIdEmail(String email)
    - [ ] Add method: boolean existsByIdEmailAndIdRecipeId(String email, UUID recipeId)

Task 8: Update RecipeRepository with user-filtering
  Action: MODIFY
  File: backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeRepository.java
  Changes:
    - [ ] Add custom query method for user-scoped recipes
    - [ ] Add: @Query("SELECT r FROM Recipe r INNER JOIN UserRecipe ur ON ur.id.recipeId = r.id WHERE ur.id.email = :email")
    - [ ] Add: List<Recipe> findAllByUserEmail(@Param("email") String email)

Task 9: Update RecipeService with user context
  Action: MODIFY
  File: backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeService.java
  Changes:
    - [ ] Inject UserRecipeRepository dependency
    - [ ] Update findAll(String userEmail) method signature
    - [ ] Update findById(UUID id, String userEmail) method signature  
    - [ ] Update save(CreateRecipeRequest request, String userEmail) method signature
    - [ ] Update updateById(UUID id, UpdateRecipeRequest request, String userEmail) method signature
    - [ ] Update deleteById(UUID id, String userEmail) method signature
    - [ ] Implement authorization checks using UserRecipeRepository.existsByIdEmailAndIdRecipeId
    - [ ] Create UserRecipe association when saving new recipes
    - [ ] Use RecipeRepository.findAllByUserEmail for user-scoped queries

Task 10: Update RecipeController with JWT email extraction
  Action: MODIFY
  File: backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeController.java
  Changes:
    - [ ] Add @AuthenticationPrincipal Jwt jwt parameter to all endpoint methods
    - [ ] Extract email using jwt.getClaimAsString("email") in each method
    - [ ] Pass userEmail to all RecipeService method calls
    - [ ] Maintain existing method signatures (add JWT parameter only)
    - [ ] Add proper error handling for missing email claim

Task 11: Update TestSecurityConfiguration with two example tokens
  Action: MODIFY
  File: backend/src/test/java/xyz/stasiak/recipai/TestSecurityConfiguration.java
  Changes:
    - [ ] Add AUTH_TOKEN_USER_1 and AUTH_TOKEN_USER_2 constants  
    - [ ] Configure mock JWT decoder to handle multiple tokens
    - [ ] Map AUTH_TOKEN_USER_1 to "user1@example.com"
    - [ ] Map AUTH_TOKEN_USER_2 to "user2@example.com"
    - [ ] Keep existing AUTH_TOKEN for backward compatibility

Task 12: Update RecipeIntegrationTest with user-scoped testing
  Action: MODIFY  
  File: backend/src/test/java/xyz/stasiak/recipai/recipes/RecipeIntegrationTest.java
  Changes:
    - [ ] Update restClient() method to accept different auth tokens
    - [ ] Test recipe isolation between users
    - [ ] Test that user A cannot access user B's recipes (should return empty list)
    - [ ] Test that user A cannot update/delete user B's recipes (should return 404)
    - [ ] Ensure existing CRUD test still passes with single user
```

### Per Task Pseudocode

```java
// Task 3: User Controller Register Endpoint
@PostMapping("/register")
public ResponseEntity<Void> register(@AuthenticationPrincipal Jwt jwt) {
    String email = jwt.getClaimAsString("email");
    
    // Check if user exists, if not create
    if (!userRepository.existsById(email)) {
        User user = new User();
        user.setEmail(email);
        userRepository.save(user);
    }
    
    return ResponseEntity.ok().build();
}

// Task 9: Recipe Service with User Context  
public List<RecipeListDto> findAll(String userEmail) {
    return recipeRepository.findAllByUserEmail(userEmail).stream()
            .map(this::toRecipeListDto)
            .toList();
}

// Task 10: Recipe Controller JWT Extraction
@GetMapping
public List<RecipeListDto> getAllRecipes(@AuthenticationPrincipal Jwt jwt) {
    String userEmail = jwt.getClaimAsString("email");
    log.debug("Getting all recipes for user: {}", userEmail);
    return recipeService.findAll(userEmail);
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

## Integration Points

- **Database Schema Changes**:
    - New `users` table with email as primary key
    - New `user_recipes` table with compound key (email, recipe_id)
    - Recipe queries now require JOIN with user_recipes table
- **API Changes**:
    - All recipe endpoints now user-scoped (no breaking changes to request/response format)
    - New `/users/register` endpoint for user creation
- **Security Integration**:
    - JWT email extraction at controller level for all recipe operations
    - Authorization checks ensure users only access their own data

## Documentation

- **Update docs/backend/db.md**: Add users and user_recipes table schemas
- **Update docs/backend/api.md**: Document user registration endpoint and user-scoped behavior
- **Update docs/backend/backend.md**: Add users module description

## Final Validation Checklist

- [ ] Correct syntax
- [ ] All tests pass
- [ ] User isolation verified (user A cannot access user B's recipes)
- [ ] Recipe CRUD operations work with user context
- [ ] User registration endpoint responds correctly
- [ ] JWT email extraction works in all controllers
- [ ] Authorization checks prevent unauthorized access
- [ ] Logs are informative but not verbose
- [ ] Database schema supports compound key queries efficiently

**SIP Confidence Score: 9/10** - High confidence for one-pass implementation due to comprehensive context, existing
patterns to follow, and clear step-by-step approach with established Spring Boot and JPA best practices.