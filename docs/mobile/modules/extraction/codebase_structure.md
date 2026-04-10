# Extraction — Codebase Structure

```
mobile/lib/features/extraction/
├── extraction_repository.dart  # API communication layer for extraction endpoints
├── extraction_service.dart     # Business logic layer for extraction operations
├── extraction_setup.dart       # Dependency injection setup for extraction module
├── extracted_recipe.dart       # Data models (ExtractedRecipe, ExtractedIngredient with double? quantity and String? comment, ExtractedInstruction)
├── url_extraction_screen.dart  # WebView-based URL extraction UI
├── image_extraction_screen.dart # Camera/Gallery image extraction UI
├── extraction_dialog.dart      # Simple dialog widget
└── web_recipe_extractor.dart   # Utility for HTML extraction
```
