# Extraction — UI

## Screens

- URL Extraction Screen (`url_extraction_screen.dart`) - WebView-based screen for extracting recipes from web pages with
  smart input field that automatically detects URLs vs search queries. Supports domain patterns (example.com,
  sub.example.co.uk, localhost:3000) and full URLs (https://example.com). Non-URL inputs trigger Google search with
  encoded query parameters. Captures the current URL from WebView and navigates to create screen with
  InitialRecipeFormData containing extracted recipe detail and source URL. Back button uses WebView history navigation
  when possible, only popping the route when there is no WebView history to go back to.
- Image Extraction Screen (`image_extraction_screen.dart`) - Screen for extracting recipes from images using camera or
  gallery selection with image preview and upload functionality. Navigates to create screen with InitialRecipeFormData
  containing extracted recipe detail and the selected image file as a pending image.
- Extraction Dialog (`extraction_dialog.dart`) - Modal dialog for choosing between URL and image extraction methods with
  Material Design buttons.
- Web Recipe Extractor (`web_recipe_extractor.dart`) - Utility class for extracting HTML content from WebView.

## Flow

#### Extraction Flow

1. **Speed Dial FAB → Extract Tap** (on Recipes tab) → Extraction Dialog → URL/Image Extraction Screen
   (`/recipes/url-extraction` or `/recipes/image-extraction`)
2. **Successful URL Extraction** → Create Recipe Screen with pre-filled extracted data, source URL, and collection (if
   filter active) via InitialRecipeFormData → Recipe creation → Back to Main Screen
3. **Successful Image Extraction** → Create Recipe Screen with pre-filled extracted data, pending image, and collection
   (if filter active) via InitialRecipeFormData → Recipe creation → Back to Main Screen
