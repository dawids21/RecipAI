# Extraction Module — Codebase Structure

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
