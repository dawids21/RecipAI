# SIP: API Refactor - Remove Dependency Between Extraction and Recipes Modules

## Goal

- Remove the tight coupling between "extraction" and "recipes" modules in the backend API
- Extract recipes from text and return extracted data directly to client without automatic persistence
- Create independent DTOs for extraction module to eliminate recipes module dependency
- Reduce visibility of recipes module classes to package-private when no longer needed externally
- Success criteria: Extraction API returns extraction-specific DTOs, no automatic saving to database, no import
  dependencies on recipes module

## Context

### Documentation and References

- `docs/prd.md` - Product Requirements Document (shows extraction should not automatically save)
- `docs/backend/api.md` - Current API Documentation (needs updates for new extraction response format)
- `docs/backend/backend.md` - Backend Architecture Overview (needs module dependency updates)
- Spring Boot Documentation: https://docs.spring.io/spring-boot/reference/web/spring-mvc.html
- Spring AI Documentation: https://docs.spring.io/spring-ai/reference/

### Current Codebase Tree

```
backend/src/main/java/xyz/stasiak/recipai/
├── RecipAiApplication.java
├── extraction/
│   ├── ExtractTextRequest.java           # Input DTO for text extraction
│   ├── ExtractedIngredient.java          # Extraction-specific ingredient DTO
│   ├── ExtractedRecipe.java              # Extraction-specific recipe DTO  
│   ├── ExtractedInstruction.java          # Extraction-specific instruction DTO
│   ├── ExtractionConfig.java             # Spring AI configuration
│   ├── ExtractionController.java         # REST controller (DEPENDS ON recipes.RecipeDto)
│   └── ExtractionService.java            # Business logic (DEPENDS ON recipes.RecipeService)
└── recipes/
    ├── CreateRecipeRequest.java
    ├── ErrorResponse.java
    ├── GlobalExceptionHandler.java
    ├── Ingredient.java                   # Recipe ingredient DTO
    ├── Instruction.java                  # Recipe instruction DTO
    ├── Recipe.java                       # JPA entity
    ├── RecipeController.java
    ├── RecipeData.java                   # Recipe data container
    ├── RecipeDto.java                    # Recipe response DTO
    ├── RecipeListDto.java
    ├── RecipeNotFoundException.java
    ├── RecipeRepository.java
    ├── RecipeService.java                # PUBLIC - used by extraction
    └── UpdateRecipeRequest.java
```

### Desired Codebase Tree

```
backend/src/main/java/xyz/stasiak/recipai/
├── RecipAiApplication.java
├── extraction/
│   ├── ExtractTextRequest.java           # Input DTO for text extraction
│   ├── ExtractedIngredient.java          # Extraction-specific ingredient DTO (with validation)
│   ├── ExtractedRecipe.java              # Extraction-specific recipe DTO (updated)
│   ├── ExtractedInstruction.java          # Extraction-specific instruction DTO (with validation)
│   ├── ExtractionConfig.java             # Spring AI configuration
│   ├── ExtractionController.java         # REST controller (returns ExtractedRecipe)
│   └── ExtractionService.java            # Business logic (NO recipes dependency)
└── recipes/
    ├── CreateRecipeRequest.java
    ├── ErrorResponse.java
    ├── GlobalExceptionHandler.java
    ├── Ingredient.java                   # Recipe ingredient DTO
    ├── Instruction.java                  # Recipe instruction DTO
    ├── Recipe.java                       # JPA entity
    ├── RecipeController.java
    ├── RecipeData.java                   # Recipe data container
    ├── RecipeDto.java                    # Recipe response DTO
    ├── RecipeListDto.java
    ├── RecipeNotFoundException.java      # PACKAGE-PRIVATE
    ├── RecipeRepository.java
    ├── RecipeService.java                # PACKAGE-PRIVATE
    └── UpdateRecipeRequest.java
```

### Known Gotchas of Our Codebase and Library Quirks

- Spring AI requires specific record format for entity mapping (seen in ExtractedRecipe)
- Current codebase uses different field names: "steps" in extraction vs "instructions" in recipes
- Bean validation annotations (@NotBlank, @NotNull, @Valid) are required for DTOs
- Tests use Testcontainers with @Import(TestcontainersConfiguration.class)
- Maven is used for build automation
- Current test expects extraction to save recipe - this behavior must change
- Jackson ObjectMapper handles JSON serialization/deserialization
- Package-private visibility is preferred per CLAUDE.md guidelines

## Implementation Plan

### Tasks

```
Task 1: Rename ExtractedStep to ExtractedInstruction
  Action: MODIFY
  File: backend/src/main/java/xyz/stasiak/recipai/extraction/ExtractedStep.java
  Changes:
    - [ ] Rename file to ExtractedInstruction.java
    - [ ] Rename record from ExtractedStep to ExtractedInstruction
    - [ ] Update field name from description to step to match recipes module
    - [ ] Add @NotBlank validation to step field

Task 2: Add validation to existing extraction DTOs
  Action: MODIFY
  File: backend/src/main/java/xyz/stasiak/recipai/extraction/ExtractedIngredient.java
  Changes:
    - [ ] Add @NotBlank validation to name field (pattern from Ingredient.java line 5)
    - [ ] Follow existing validation patterns

Task 3: Update ExtractedRecipe to use ExtractedInstruction and add validation
  Action: MODIFY
  File: backend/src/main/java/xyz/stasiak/recipai/extraction/ExtractedRecipe.java
  Changes:
    - [ ] Update steps field type from List<ExtractedStep> to List<ExtractedInstruction>
    - [ ] Keep flat structure (name, description, ingredients, steps)
    - [ ] Add validation annotations to fields

Task 4: Remove recipes dependency from ExtractionService
  Action: MODIFY
  File: backend/src/main/java/xyz/stasiak/recipai/extraction/ExtractionService.java
  Changes:
    - [ ] Remove import xyz.stasiak.recipai.recipes.*; (line 9)
    - [ ] Remove RecipeService dependency (line 20)
    - [ ] Remove convertToRecipeData method (lines 42-52)
    - [ ] Update extractFromText to return ExtractedRecipe directly
    - [ ] Remove CreateRecipeRequest conversion (lines 34-37)
    - [ ] Update references from ExtractedStep to ExtractedInstruction

Task 5: Update ExtractionController to return extraction DTOs
  Action: MODIFY
  File: backend/src/main/java/xyz/stasiak/recipai/extraction/ExtractionController.java
  Changes:
    - [ ] Remove import xyz.stasiak.recipai.recipes.RecipeDto; (line 10)
    - [ ] Change return type from RecipeDto to ExtractedRecipe (line 21)
    - [ ] Update method signature and return value

Task 6: Reduce visibility of RecipeService and RecipeNotFoundException
  Action: MODIFY
  File: backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeService.java
  Changes:
    - [ ] Change public class to package-private class (line 17)
    - [ ] Verify no external module dependencies remain

Task 7: Reduce visibility of RecipeNotFoundException  
  Action: MODIFY
  File: backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeNotFoundException.java
  Changes:
    - [ ] Change public class to package-private class
    - [ ] Verify no external module dependencies remain

Task 8: Update integration test expectations
  Action: MODIFY
  File: backend/src/test/java/xyz/stasiak/recipai/extraction/ExtractionIntegrationTest.java
  Changes:
    - [ ] Remove import xyz.stasiak.recipai.recipes.RecipeDto (line 12)
    - [ ] Change expected response type from RecipeDto to ExtractedRecipe (line 43, 48)
    - [ ] Remove test validation that checks if recipe was saved to /recipes (lines 57-79)
    - [ ] Update assertions to match ExtractedRecipe structure
    - [ ] Test should focus only on extraction, not persistence
```

### Per Task Pseudocode

```java
// Task 1: ExtractedRecipeData structure
public record ExtractedRecipeData(
    @NotNull @NotEmpty @Valid List<ExtractedIngredient> ingredients,
    @NotNull @NotEmpty @Valid List<ExtractedStep> steps
) {}

// Task 4: Updated ExtractedRecipe structure  
public record ExtractedRecipe(
    String name, 
    String description, 
    ExtractedRecipeData data
) {}

// Task 5: ExtractionService without recipes dependency
public ExtractedRecipe extractFromText(String text) {
    // AI extraction logic (unchanged)
    ExtractedRecipe extractedRecipe = chatClient.prompt(prompt)
            .call()
            .entity(ExtractedRecipe.class);
            
    // Direct return without saving
    return extractedRecipe;
}

// Task 6: ExtractionController return type change
@PostMapping("/text")
public ExtractedRecipe extractFromText(@Valid @RequestBody ExtractTextRequest request) {
    return extractionService.extractFromText(request.text());
}
```

## Validation

### Syntax and Style

```bash
# Run these FIRST - fix any errors before proceeding
cd backend && mvn compile
cd backend && mvn checkstyle:check

# Expected: No compilation or style errors. If errors, READ the error and fix.
```

### Unit Tests

```bash
# Run and iterate until passing:
cd backend && mvn test -Dtest=ExtractionIntegrationTest
# If failing: Read error, understand root cause, fix code, re-run (never mock to pass)
```

### Integration Tests

```bash
# Run and iterate until passing:
cd backend && mvn test
# If failing: Read error, understand root cause, fix code, re-run (never mock to pass)
```

## Integration Points

- **API Response Format Change**: POST /extract/text will return ExtractedRecipe instead of RecipeDto
    - Response will not include UUID (no longer saved automatically)
    - Client applications must handle the new response format
    - Mobile app updates are noted as later work per feature requirements
- **Database**: No changes to database schema required
- **Module Dependencies**: Extraction module will be fully independent of recipes module

## Documentation

- `docs/backend/api.md` - Update POST /extract/text endpoint documentation with new response format
- `docs/backend/backend.md` - Update module dependencies section to reflect removal of extraction→recipes dependency
- `backend/CLAUDE.md` - No updates needed (architecture guidelines remain the same)

## Final Validation Checklist

- [ ] Correct syntax (mvn compile passes)
- [ ] Correct style (mvn checkstyle:check passes)
- [ ] All tests pass (mvn test passes)
- [ ] Manual test successful (POST /extract/text returns ExtractedRecipe format)
- [ ] Error cases handled gracefully (validation errors return 400 with clear messages)
- [ ] Logs are informative but not verbose (existing log statements maintained)
- [ ] Documentation updated (API docs reflect new response format)
- [ ] No import dependencies from extraction to recipes module
- [ ] RecipeService and RecipeNotFoundException are package-private
- [ ] Extraction works independently without affecting recipes functionality

**SIP Confidence Score: 9/10**

This SIP provides comprehensive context including exact file paths, line numbers, specific code patterns to follow,
current dependency analysis, and clear validation steps. The one-pass implementation success rate should be very high
given the detailed research and specific guidance provided.