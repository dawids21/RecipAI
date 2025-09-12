# SIP: API Extracting Recipe from Image

## Goal

- Implement a new API endpoint `/extract/image` that extracts recipe information from uploaded images (JPEG/PNG formats)
- Enable users to upload recipe images and receive structured recipe data in the same format as text extraction
- Follow existing patterns from the `/extract/text` endpoint for consistency and maintainability
- Support multipart file uploads with proper validation and error handling

## Context

### Documentation and References

- **Spring AI ChatClient API**: https://docs.spring.io/spring-ai/reference/api/chatclient.html - Official ChatClient
  documentation
- **Spring AI Multimodality**: https://docs.spring.io/spring-ai/reference/api/multimodality.html - Media and UserMessage
  patterns
- **Spring AI Image Examples (2025)**: https://piotrminkowski.com/2025/03/04/spring-ai-with-multimodality-and-images/ -
  Comprehensive image processing examples
- **Existing text extraction**: `backend/src/main/java/xyz/stasiak/recipai/extraction/ExtractionController.java:19-23` -
  Pattern to follow
- **Integration test pattern**: `backend/src/test/java/xyz/stasiak/recipai/extraction/ExtractionIntegrationTest.java` -
  Testing approach
- **Project guidelines**: `/home/dawid/Projects/RecipAI/backend/CLAUDE.md` - Spring Boot best practices
- **API documentation**: `docs/backend/api.md:193-233` - Existing extraction endpoint format

### Current Codebase Tree

```
backend/src/main/java/xyz/stasiak/recipai/extraction/
├── ExtractionController.java    # REST endpoints for extraction
├── ExtractionService.java       # Business logic with ChatClient
├── ExtractionConfig.java        # ChatClient bean configuration  
├── ExtractTextRequest.java      # DTO for text extraction
├── ExtractedRecipe.java         # Response DTO with validation
├── ExtractedIngredient.java     # Ingredient DTO
└── ExtractedInstruction.java    # Instruction DTO
```

### Desired Codebase Tree

```
backend/src/main/java/xyz/stasiak/recipai/extraction/
├── ExtractionController.java    # REST endpoints (ADD: extractFromImage endpoint)
├── ExtractionService.java       # Business logic (ADD: extractFromImage method)
├── ExtractionConfig.java        # ChatClient bean configuration (unchanged)
├── ExtractTextRequest.java      # DTO for text extraction (unchanged)
├── ExtractImageRequest.java     # NEW: DTO for image extraction
├── ExtractedRecipe.java         # Response DTO (unchanged)
├── ExtractedIngredient.java     # Ingredient DTO (unchanged)
└── ExtractedInstruction.java    # Instruction DTO (unchanged)
```

### Known Gotchas of Our Codebase and Library Quirks

- **Spring AI Media Builder**: Use `Media.builder().mimeType().data().build()` pattern for image handling
- **MimeTypeUtils Constants**: Use `MimeTypeUtils.IMAGE_JPEG` and `MimeTypeUtils.IMAGE_PNG` for MIME type validation
- **MultipartFile to Resource**: Convert using `multipartFile.getResource()` for Media object creation
- **File Upload Size**: Spring Boot default max file size is 1MB (may need configuration adjustment)
- **Jakarta Validation**: Use `@Valid` on controller parameters and validation annotations on DTOs
- **Package-private classes**: Follow existing pattern where most classes are package-private
- **Lombok patterns**: Use `@RequiredArgsConstructor` for dependency injection and `@Slf4j` for logging
- **ChatClient entity mapping**: Use `.entity(ExtractedRecipe.class)` for automatic JSON-to-POJO conversion

## Implementation Plan

### Tasks

```
Task 1: Create image extraction request DTO
  Action: CREATE
  File: backend/src/main/java/xyz/stasiak/recipai/extraction/ExtractImageRequest.java
  Changes:
    - [ ] Create record with MultipartFile parameter and Jakarta validation
    - [ ] Follow ExtractTextRequest pattern using record syntax
    - [ ] Add @NotNull validation for file parameter
    - [ ] Include custom validation for JPEG/PNG MIME types

Task 2: Add extractFromImage method to ExtractionService  
  Action: MODIFY
  File: backend/src/main/java/xyz/stasiak/recipai/extraction/ExtractionService.java
  Changes:
    - [ ] Add extractFromImage(Media imageMedia) method
    - [ ] Use UserMessage.builder() pattern with text prompt and media parameter
    - [ ] Use similar prompt as text extraction but adapted for images
    - [ ] Add debug logging following existing pattern
    - [ ] Return ExtractedRecipe using chatClient.entity() method

Task 3: Add REST endpoint to ExtractionController
  Action: MODIFY  
  File: backend/src/main/java/xyz/stasiak/recipai/extraction/ExtractionController.java
  Changes:
    - [ ] Add @PostMapping("/image") endpoint method
    - [ ] Accept @RequestParam("file") MultipartFile parameter
    - [ ] Convert MultipartFile to Media object using file.getResource() and MimeTypeUtils
    - [ ] Add proper logging following existing debug pattern
    - [ ] Call extractionService.extractFromImage(media)
    - [ ] Return ExtractedRecipe response

Task 4: Create integration test for image extraction
  Action: MODIFY
  File: backend/src/test/java/xyz/stasiak/recipai/extraction/ExtractionIntegrationTest.java  
  Changes:
    - [ ] Add test method shouldExtractRecipeFromImage()
    - [ ] Use only kwestia_smaku.jpg test image resource
    - [ ] Create multipart request using RestClient
    - [ ] Assert ExtractedRecipe response structure matches expectations
    - [ ] Follow existing test pattern with TestcontainersConfiguration and TestSecurityConfiguration

Task 5: Update API documentation
  Action: MODIFY
  File: docs/backend/api.md
  Changes:  
    - [ ] Add POST /extract/image endpoint documentation
    - [ ] Include request format (multipart/form-data)
    - [ ] Document supported file types (JPEG, PNG)
    - [ ] Add example response matching existing extraction format
    - [ ] Document error cases (400 for unsupported file types)
```

### Per Task Pseudocode

```java
// Task 1: ExtractImageRequest
public record ExtractImageRequest(
                @NotNull @ImageFile MultipartFile file
        ) {
    // Custom validation annotation @ImageFile to check MIME type
}

// Task 2: ExtractionService.extractFromImage
public ExtractedRecipe extractFromImage(Media imageMedia) {
    log.debug("Extracting recipe from image media");

    // Create UserMessage with prompt and image
    UserMessage userMessage = UserMessage.builder()
            .text("Extract recipe data from this image. Include name, ingredients, and instructions.")
            .media(imageMedia)
            .build();

    // Get structured response
    ExtractedRecipe extractedRecipe = chatClient.prompt(new Prompt(userMessage))
            .call()
            .entity(ExtractedRecipe.class);

    log.debug("Extracted recipe with name: {}", extractedRecipe.name());
    return extractedRecipe;
}

// Task 3: ExtractionController.extractFromImage  
@PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ExtractedRecipe extractFromImage(@RequestParam("file") MultipartFile file) {
    log.debug("Extracting recipe from uploaded image");

    // Convert MultipartFile to Media
    Resource imageResource = file.getResource();
    MimeType mimeType = MimeTypeUtils.parseMimeType(file.getContentType());
    Media imageMedia = Media.builder()
            .mimeType(mimeType)
            .data(imageResource)
            .build();

    return extractionService.extractFromImage(imageMedia);
}
```

## Validation

### Syntax and Style

```bash
# Run these FIRST - fix any errors before proceeding
cd /home/dawid/Projects/RecipAI/backend
mvn compile

# Expected: No compilation errors. If errors, READ the error and fix.
```

### Unit Tests

```bash
# Run and iterate until passing:
cd /home/dawid/Projects/RecipAI/backend  
mvn test
# If failing: Read error, understand root cause, fix code, re-run (never mock to pass)
```

### Integration Tests

```bash
# Run and iterate until passing:
cd /home/dawid/Projects/RecipAI/backend
mvn test -Dtest=ExtractionIntegrationTest
# If failing: Read error, understand root cause, fix code, re-run (never mock to pass)
```

## Integration Points

- **API Response Format**: Uses identical ExtractedRecipe structure as /extract/text endpoint
- **Authentication**: Inherits same JWT-based authentication from @RestController
- **Error Handling**: Leverages existing GlobalExceptionHandler for consistent error responses
- **Validation**: Integrates with Spring Boot validation framework using Jakarta annotations
- **File Upload**: Uses standard Spring Boot multipart file handling (no additional configuration needed for MVP)

## Documentation

- **docs/backend/api.md**: Add new /extract/image endpoint documentation with request/response examples
- **No CLAUDE.md updates needed**: Existing Spring Boot guidelines already cover the patterns used

## Final Validation Checklist

- [ ] Correct syntax (mvn compile passes)
- [ ] Correct style (follows existing Lombok and Spring Boot patterns)
- [ ] All tests pass (mvn test)
- [ ] Manual test successful (can upload JPEG/PNG and get structured recipe data)
- [ ] Error cases handled gracefully (invalid file types return 400 with error message)
- [ ] Logs are informative but not verbose (debug level following existing pattern)
- [ ] Documentation updated (API docs include new endpoint)

## Confidence Score: 9/10

This SIP has high confidence for one-pass implementation because:

- **Complete Context**: All necessary patterns from existing codebase identified and documented
- **Proven Examples**: Real Spring AI 2025 examples showing exact patterns to use
- **Minimal Changes**: Builds on existing, working text extraction patterns
- **Clear Validation**: Specific test approach using existing integration test infrastructure
- **Realistic Scope**: Focused on core functionality without over-engineering

**Risk Mitigation**: The only potential issue is MultipartFile size limits, but this can be addressed during
implementation if needed and doesn't affect core functionality.