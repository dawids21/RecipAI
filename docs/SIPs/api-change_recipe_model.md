# SIP - API Change Recipe Model

## Goal

- Change the Recipe API model to return structured data with defined `ingredients` and `instructions` fields instead of
  unstructured JSON
- Maintain backward compatibility at the database level (keep JSONB storage unchanged)
- Update API documentation to reflect the new structured format
- Success criteria: All recipe endpoints return structured data format matching the feature requirements while
  maintaining existing functionality

## Context

### Documentation and References

- **Feature Request**: `docs/feature-requests/api-change_recipe_model.md` - Contains exact structure requirements
- **Current API Documentation**: `docs/backend/api.md` - Documents current unstructured format, needs updating
- **Backend Overview**: `docs/backend/backend.md` - Explains modular architecture to follow
- **Project Rules**: `backend/CLAUDE.md` - Contains coding standards, Spring Boot best practices, and testing patterns
- **PRD**: `docs/prd.md` - Product context and user requirements

### Current Codebase Tree

```
backend/src/main/java/xyz/stasiak/recipai/
├── recipes/
│   ├── Recipe.java                    # JPA entity with JsonNode data field
│   ├── RecipeDto.java                 # Current API response with JsonNode data
│   ├── CreateRecipeRequest.java       # Current API request with JsonNode data
│   ├── RecipeListDto.java            # List response (unchanged)
│   ├── RecipeController.java         # REST endpoints (minor changes needed)
│   └── RecipeService.java            # Business logic (unchanged)
└── extraction/
    ├── ExtractedRecipe.java          # REFERENCE: Already has structured format
    ├── ExtractedIngredient.java      # REFERENCE: Matches desired ingredient structure
    ├── ExtractedStep.java            # REFERENCE: Matches desired instruction structure
    └── ExtractionService.java       # Converts structured to JsonNode
```

### Desired Codebase Tree

```
backend/src/main/java/xyz/stasiak/recipai/
├── recipes/
│   ├── Recipe.java                    # JPA entity (unchanged - still JsonNode)
│   ├── RecipeDto.java                 # NEW: Structured API response
│   ├── CreateRecipeRequest.java       # NEW: Structured API request
│   ├── RecipeData.java               # NEW: Structured data model
│   ├── Ingredient.java               # NEW: Ingredient model
│   ├── Instruction.java              # NEW: Instruction model
│   ├── RecipeListDto.java            # List response (unchanged)
│   ├── RecipeController.java         # REST endpoints (minor changes)
│   └── RecipeService.java            # NEW: Add conversion logic
└── extraction/
    └── [existing files unchanged]    # Continue using for AI extraction
```

### Known Gotchas of Our Codebase and Library Quirks

- **Spring Boot 3.5.4**: Use `record` types for DTOs with validation annotations
- **Jackson ObjectMapper**: Already configured for JsonNode <-> POJO conversion in ExtractionService:47
- **JPA with JSONB**: Recipe entity uses `@JdbcTypeCode(SqlTypes.JSON)` for PostgreSQL JSONB storage
- **Validation**: Use `@Valid` annotation on request bodies and Bean Validation annotations
- **Testing**: Use TestContainers with `@Import(TestcontainersConfiguration.class)` for integration tests
- **Package visibility**: Most classes are package-private following modular architecture
- **Error handling**: Use `@ControllerAdvice` pattern already established in GlobalExceptionHandler.java

## Implementation Plan

### Tasks

```
Task 1: Create structured data models in recipes package
  Action: CREATE
  Files: 
    - backend/src/main/java/xyz/stasiak/recipai/recipes/Ingredient.java
    - backend/src/main/java/xyz/stasiak/recipai/recipes/Instruction.java
    - backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeData.java
  Changes:
    - [ ] Create Ingredient record with String name, String quantity, String unit (mirror ExtractedIngredient.java:3)
    - [ ] Create Instruction record with String step (mirror ExtractedStep.java:3 but use 'step' field name)
    - [ ] Create RecipeData record with List<Ingredient> ingredients, List<Instruction> instructions
    - [ ] Add Bean Validation annotations (@NotNull, @NotEmpty) following project patterns
    - [ ] Use package-private visibility following backend/CLAUDE.md guidelines

Task 2: Update API request/response models
  Action: MODIFY
  Files:
    - backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeDto.java
    - backend/src/main/java/xyz/stasiak/recipai/recipes/CreateRecipeRequest.java
  Changes:
    - [ ] Change RecipeDto to use RecipeData instead of JsonNode for data field
    - [ ] Change CreateRecipeRequest to use RecipeData instead of JsonNode for data field
    - [ ] Keep UUID id and String name fields unchanged
    - [ ] Maintain record structure following existing patterns

Task 3: Add conversion logic in RecipeService
  Action: MODIFY
  File: backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeService.java
  Changes:
    - [ ] Add private method convertToRecipeData(JsonNode jsonNode) using ObjectMapper
    - [ ] Add private method convertToJsonNode(RecipeData recipeData) using ObjectMapper
    - [ ] Update findById() to convert JsonNode to RecipeData before returning RecipeDto
    - [ ] Update findAll() method (no change - RecipeListDto doesn't include data)
    - [ ] Update save() method to convert RecipeData to JsonNode before persisting
    - [ ] Follow ExtractionService.java:46-48 pattern for ObjectMapper usage
    - [ ] Add error handling for conversion failures

Task 4: Update integration tests 
  Action: MODIFY
  File: backend/src/test/java/xyz/stasiak/recipai/recipes/RecipeIntegrationTest.java
  Changes:
    - [ ] Replace JSON string creation with structured objects in shouldCreateListAndReadRecipes()
    - [ ] Update CreateRecipeRequest to use RecipeData objects instead of objectMapper.readTree()
    - [ ] Update assertions to check RecipeData fields instead of JsonNode navigation
    - [ ] Maintain test coverage for all endpoints and validation scenarios
    - [ ] Ensure tests validate the new structured format matches requirements

Task 5: Update API documentation
  Action: MODIFY  
  File: docs/backend/api.md
  Changes:
    - [ ] Replace "..." placeholder in data field examples with actual structured format
    - [ ] Update POST /recipes request example to show ingredients and instructions arrays
    - [ ] Update GET /recipes/{uuid} response example to show structured data
    - [ ] Update extraction endpoint response to show structured format
    - [ ] Keep HTTP status codes and error responses unchanged
```

### Per Task Pseudocode

```java
// Task 1: New Data Models
public record Ingredient(@NotBlank String name, String quantity, String unit) {
}

public record Instruction(@NotBlank String step) {
}

public record RecipeData(
        @NotNull @NotEmpty List<Ingredient> ingredients,
        @NotNull @NotEmpty List<Instruction> instructions
) {
}

// Task 3: Conversion Logic in RecipeService
private RecipeData convertToRecipeData(JsonNode jsonNode) {
    try {
        return objectMapper.treeToValue(jsonNode, RecipeData.class);
    } catch (JsonProcessingException e) {
        log.error("Failed to convert JsonNode to RecipeData", e);
        throw new RuntimeException("Invalid recipe data format", e);
    }
}

private JsonNode convertToJsonNode(RecipeData recipeData) {
    return objectMapper.valueToTree(recipeData);
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

- **API Contract Change**: Recipe endpoints now return structured data format - frontend applications will need to
  handle new structure
- **Database**: No changes to database schema or storage format (still JSONB)
- **Extraction Module**: ExtractionService.java:38-43 already converts structured ExtractedRecipe to JsonNode - no
  changes needed
- **Error Handling**: Existing GlobalExceptionHandler will handle conversion failures with 400 Bad Request responses

## Documentation

- **API Documentation**: `docs/backend/api.md` - Update all recipe endpoint examples to show structured format
- **CLAUDE.md**: No updates needed - existing patterns followed

## Final Validation Checklist

- [ ] Correct syntax - mvn compile passes
- [ ] Correct style - follows backend/CLAUDE.md guidelines (records, validation, package-private)
- [ ] All tests pass - mvn test shows green
- [ ] Manual test successful - recipe CRUD operations work with structured data
- [ ] Error cases handled gracefully - invalid data returns 400 Bad Request
- [ ] Logs are informative but not verbose - conversion errors logged at ERROR level
- [ ] Documentation updated - API docs reflect new structured format

**SIP Confidence Score: 9/10** - High confidence due to comprehensive context, existing similar patterns in extraction
module, clear task breakdown, and well-established testing patterns. Only minor risk is JSON conversion edge cases.