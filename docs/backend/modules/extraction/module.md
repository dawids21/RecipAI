# Extraction Module

Extracts recipes from text/images using AI (Spring AI Gemini integration); identifies the caller from
the JWT and reserves one unit of that user's `EXTRACTION` budget before calling the provider, so a
failed extraction still consumes its unit.

## Codebase Structure

```
backend/src/main/java/xyz/stasiak/recipai/
└── extraction/
    ├── ExtractionController.java            # /extract REST endpoints with JWT authentication and image MIME validation
    ├── ExtractionService.java               # Reserves the EXTRACTION budget, then prompts the AI provider; owns the resource key
    ├── ExtractionExceptionHandler.java      # Exception handling with ProblemDetail
    ├── ExtractionConfig.java                # ChatClient bean configuration
    ├── ExtractedRecipe.java                 # Extracted recipe response DTO
    ├── ExtractedIngredient.java             # Extracted ingredient response DTO
    ├── ExtractedInstruction.java            # Extracted instruction response DTO
    ├── ExtractTextRequest.java              # Text extraction request DTO
    ├── ExtractImageRequest.java             # Image extraction request DTO (multipart file)
    ├── UnsupportedImageTypeException.java   # File is not JPEG/PNG -> 400
    └── ExtractionFailedException.java       # AI provider returned no recipe -> 500
```

## Limits

Both endpoints consume one unit of the caller's `EXTRACTION` budget, reserved before the AI provider
is called and identified by the `email` claim of the JWT. A refused call consumes nothing; a call
that reaches the provider and then fails still consumes its unit — there is no refund. See
`docs/backend/modules/limits/` for how the budget is configured and changed.
