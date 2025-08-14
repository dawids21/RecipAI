# SIP: API Update and Delete Recipe Endpoints

## Goal

- Add PUT /recipes/{id} endpoint to update existing recipes by ID with complete recipe object
- Add DELETE /recipes/{id} endpoint to delete recipes by ID
- Follow existing codebase patterns for consistent API design
- Add comprehensive integration tests in RecipeIntegrationTest
- Success criteria: Both endpoints work correctly, handle errors properly, and pass all tests

## Context

### Documentation and References

- `docs/backend/api.md` - Current API documentation showing existing GET and POST endpoints
- `docs/backend/backend.md` - Backend module structure and architecture overview
- `docs/prd.md` - Product requirements, specifically US-003 for update/delete functionality
- `backend/CLAUDE.md` - Backend coding practices and tech stack guidelines
- **Spring Boot REST API Best Practices 2025**: https://amigoscode.com/blogs/top-10-spring-boot-rest-api-best-practices
- **DELETE Endpoints Best Practices
  **: https://www.codementor.io/@noelkamphoa/creating-delete-endpoints-with-spring-boot-a-quick-guide-2bwafy87rf
- **REST API Best Practices**: https://stackoverflow.blog/2020/03/02/best-practices-for-rest-api-design/

### Current Codebase Tree

```
backend/src/main/java/xyz/stasiak/recipai/recipes/
├── RecipeController.java          # Has GET and POST endpoints
├── RecipeService.java             # Has findAll, findById, save methods
├── RecipeRepository.java          # Extends JpaRepository<Recipe, UUID>
├── Recipe.java                    # Entity with UUID id, String name, JsonNode data
├── CreateRecipeRequest.java       # Record for POST requests
├── RecipeDto.java                 # Record for responses
├── RecipeListDto.java             # Record for list responses
├── RecipeData.java               # Record containing ingredients and instructions
├── Ingredient.java               # Record for ingredient data
├── Instruction.java              # Record for instruction data
├── RecipeNotFoundException.java   # Custom exception for 404 cases
├── GlobalExceptionHandler.java    # @ControllerAdvice for exception handling
└── ErrorResponse.java            # Record for error responses

backend/src/test/java/xyz/stasiak/recipai/recipes/
└── RecipeIntegrationTest.java     # Integration tests using RestClient
```

### Desired Codebase Tree

```
backend/src/main/java/xyz/stasiak/recipai/recipes/
├── RecipeController.java          # ADD: PUT /{id} and DELETE /{id} endpoints
├── RecipeService.java             # ADD: updateById and deleteById methods
├── RecipeRepository.java          # No changes needed (JPA handles update/delete)
├── UpdateRecipeRequest.java       # NEW: Record for PUT requests (same as CreateRecipeRequest)
└── [all other files unchanged]

backend/src/test/java/xyz/stasiak/recipai/recipes/
└── RecipeIntegrationTest.java     # ADD: Tests for update and delete endpoints
```

### Known Gotchas of Our Codebase and Library Quirks

**Spring Boot & JPA Patterns:**

- Constructor-based dependency injection with `@RequiredArgsConstructor`
- DTOs as immutable `record` types with Bean Validation annotations
- Custom exceptions handled via `@ControllerAdvice` returning `ErrorResponse`
- UUID primary keys generated with `GenerationType.UUID`
- JsonNode stored in PostgreSQL JSONB column for flexible recipe data

**Validation and Error Handling:**

- Use `@Valid` on request bodies with `@RequestBody`
- `RecipeNotFoundException` thrown when recipe not found, handled globally
- Return consistent `ErrorResponse` record for all error cases
- Validate using Bean Validation annotations (`@NotBlank`, `@NotNull`, etc.)

**Testing Patterns:**

- Integration tests with `@SpringBootTest` and `@Import(TestcontainersConfiguration.class)`
- Use RestClient for HTTP calls in tests
- Test both success and error scenarios (404 cases)

**Data Conversion:**

- `RecipeService` handles conversion between JsonNode and RecipeData
- Existing `convertToJsonNode()` and `convertToRecipeData()` methods in service

## Implementation Plan

### Tasks

```
Task 1: Create UpdateRecipeRequest DTO
  Action: CREATE
  File: backend/src/main/java/xyz/stasiak/recipai/recipes/UpdateRecipeRequest.java
  Changes:
    - [ ] Create record identical to CreateRecipeRequest for consistency
    - [ ] Include same validation annotations (@NotBlank, @NotNull, @Valid)
    - [ ] Follow existing pattern: record UpdateRecipeRequest(@NotBlank String name, @NotNull @Valid RecipeData data)

Task 2: Add updateById method to RecipeService
  Action: MODIFY
  File: backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeService.java
  Changes:
    - [ ] Add updateById(UUID id, UpdateRecipeRequest request) method
    - [ ] Check if recipe exists first using findById
    - [ ] Throw RecipeNotFoundException if not found
    - [ ] Update existing recipe with new name and data
    - [ ] Use existing convertToJsonNode() method for data conversion
    - [ ] Return RecipeDto using existing toDto() method
    - [ ] Add SLF4J debug logging

Task 3: Add deleteById method to RecipeService  
  Action: MODIFY
  File: backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeService.java
  Changes:
    - [ ] Add deleteById(UUID id) method returning boolean
    - [ ] Check if recipe exists first using existsById
    - [ ] If exists, call repository.deleteById(id) and return true
    - [ ] If not exists, return false (don't throw exception here)
    - [ ] Add SLF4J debug logging

Task 4: Add PUT endpoint to RecipeController
  Action: MODIFY  
  File: backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeController.java
  Changes:
    - [ ] Add @PutMapping("/{id}") method updateRecipe
    - [ ] Accept @PathVariable UUID id and @Valid @RequestBody UpdateRecipeRequest
    - [ ] Call recipeService.updateById(id, request)
    - [ ] Return ResponseEntity.ok(updatedRecipe) - 200 OK with body
    - [ ] RecipeNotFoundException will be handled by GlobalExceptionHandler (404)
    - [ ] Add SLF4J debug logging

Task 5: Add DELETE endpoint to RecipeController
  Action: MODIFY
  File: backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeController.java  
  Changes:
    - [ ] Add @DeleteMapping("/{id}") method deleteRecipe
    - [ ] Accept @PathVariable UUID id
    - [ ] Call recipeService.deleteById(id)
    - [ ] If true returned: ResponseEntity.noContent().build() - 204 No Content
    - [ ] If false returned: ResponseEntity.notFound().build() - 404 Not Found
    - [ ] Add SLF4J debug logging

Task 6: Add integration tests for update endpoint
  Action: MODIFY
  File: backend/src/test/java/xyz/stasiak/recipai/recipes/RecipeIntegrationTest.java
  Changes:
    - [ ] Add shouldUpdateExistingRecipe() test method
    - [ ] Create recipe first using existing POST pattern
    - [ ] Update recipe using PUT with modified data
    - [ ] Verify response is 200 OK with updated data
    - [ ] Verify GET shows updated data
    - [ ] Add shouldReturn404WhenUpdatingNonExistentRecipe() test
    - [ ] Test PUT with random UUID returns 404

Task 7: Add integration tests for delete endpoint
  Action: MODIFY  
  File: backend/src/test/java/xyz/stasiak/recipai/recipes/RecipeIntegrationTest.java
  Changes:
    - [ ] Add shouldDeleteExistingRecipe() test method
    - [ ] Create recipe first using existing POST pattern
    - [ ] Delete recipe using DELETE endpoint
    - [ ] Verify response is 204 No Content
    - [ ] Verify GET returns 404 for deleted recipe
    - [ ] Add shouldReturn404WhenDeletingNonExistentRecipe() test
    - [ ] Test DELETE with random UUID returns 404
```

### Per Task Pseudocode

```java
// Task 2: RecipeService.updateById() pseudocode
public RecipeDto updateById(UUID id, UpdateRecipeRequest request) {
    log.debug("Updating recipe with id: {}", id);

    Recipe existingRecipe = recipeRepository.findById(id)
            .orElseThrow(() -> new RecipeNotFoundException(id));

    existingRecipe.setName(request.name());
    existingRecipe.setData(convertToJsonNode(request.data()));

    Recipe savedRecipe = recipeRepository.save(existingRecipe);
    return toDto(savedRecipe);
}

// Task 3: RecipeService.deleteById() pseudocode  
public boolean deleteById(UUID id) {
    log.debug("Deleting recipe with id: {}", id);

    if (recipeRepository.existsById(id)) {
        recipeRepository.deleteById(id);
        return true;
    }
    return false;
}

// Task 4: RecipeController.updateRecipe() pseudocode
@PutMapping("/{id}")
public ResponseEntity<RecipeDto> updateRecipe(@PathVariable UUID id, @Valid @RequestBody UpdateRecipeRequest request) {
    log.debug("Updating recipe with id: {}", id);
    RecipeDto updatedRecipe = recipeService.updateById(id, request);
    return ResponseEntity.ok(updatedRecipe);
}

// Task 5: RecipeController.deleteRecipe() pseudocode
@DeleteMapping("/{id}")
public ResponseEntity<Void> deleteRecipe(@PathVariable UUID id) {
    log.debug("Deleting recipe with id: {}", id);
    boolean deleted = recipeService.deleteById(id);
    return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
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
# Pay special attention to TestContainers setup and database state
```

## Integration Points

- **API Changes**: New PUT /recipes/{id} and DELETE /recipes/{id} endpoints added
- **Database**: Uses existing JPA repository methods (save, deleteById, existsById)
- **Exception Handling**: Leverages existing RecipeNotFoundException and GlobalExceptionHandler
- **Data Validation**: Uses existing Bean Validation with @Valid annotations
- **Response Format**: Follows existing JSON response patterns

## Documentation

- `docs/backend/api.md` - Add documentation for new PUT and DELETE endpoints with examples
- `CLAUDE.md` - No updates needed, implementation follows existing patterns

## Final Validation Checklist

- [ ] Correct syntax - mvn compile passes
- [ ] Correct style - follows existing Lombok and Spring Boot patterns
- [ ] All tests pass - mvn test succeeds
- [ ] Manual test successful - can update and delete recipes via API
- [ ] Error cases handled gracefully - 404 responses for non-existent recipes
- [ ] Logs are informative but not verbose - debug level logging added
- [ ] Documentation updated if needed - API docs updated

**Confidence Score: 9/10** - High confidence for one-pass implementation success due to comprehensive context, existing
patterns to follow, and clear validation steps.