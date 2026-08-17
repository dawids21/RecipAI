# Extraction — Codebase Structure

```
mobile/lib/features/extraction/
├── extraction_repository.dart  # API communication layer for extraction endpoints
├── extraction_service.dart     # Business logic layer for extraction operations
├── extraction_setup.dart       # Dependency injection setup for extraction module
├── extracted_recipe.dart       # Data models (ExtractedRecipe, ExtractedIngredient with double? quantity and String? comment, ExtractedInstruction)
├── url_extraction_screen.dart  # WebView-based URL extraction UI
├── image_extraction_screen.dart # Camera/Gallery image extraction UI
├── share_intent_service.dart   # Receives cold-start and warm-start Android share intents and routes them to the extraction screens
├── share_intent_setup.dart     # Dependency injection setup for share intent handling
├── share_payload.dart          # SharePayload sealed class classifying a share as URL, non-URL text, or image
├── share_route_extras.dart     # UrlPrefill/ImagePrefill route extras carrying shared content to the screens
├── extraction_dialog.dart      # Simple dialog widget
└── web_recipe_extractor.dart   # Utility for HTML extraction
```
