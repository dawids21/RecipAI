# Extraction — UI

## Screens

Both extraction screens load the caller's extraction usage on open, render the `used / limit` counter (with
"resets in ..." when the balance carries a countdown) and disable the extract action at the quota — see
`docs/mobile/modules/limits/ui.md`.

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
- Share Intent Service (`share_intent_service.dart`) - Receives shares from the Android share sheet over the
  `recipai/share` platform channel (method channel for cold start, event channel for warm start), classifies the
  payload, and routes it to the matching extraction screen with the content pre-filled.

## Flow

#### Extraction Flow

1. **Speed Dial FAB → Extract Tap** (on Recipes tab) → Extraction Dialog → URL/Image Extraction Screen
   (`/recipes/url-extraction` or `/recipes/image-extraction`)
2. **Successful URL Extraction** → Create Recipe Screen with pre-filled extracted data, source URL, and collection (if
   filter active) via InitialRecipeFormData → Recipe creation → Back to Main Screen
3. **Successful Image Extraction** → Create Recipe Screen with pre-filled extracted data, pending image, and collection
   (if filter active) via InitialRecipeFormData → Recipe creation → Back to Main Screen

#### Share Intent Flow

1. **Android share sheet → RecipAI** → ShareIntentService classifies the payload:
   - **Text containing a URL** → URL Extraction Screen with the URL pre-filled and the WebView loaded. The URL is taken
     as-is when the whole text is a URL, otherwise the first URL embedded in the text is extracted (e.g. "Check this
     recipe: https://cookbook.com/recipe1"). Only `http(s)://` and `www.`-prefixed URLs are recognised inside text —
     bare domains would too easily match ordinary prose — and `www.` matches are normalised to `https://`.
   - **Image** → Image Extraction Screen with the image preview ready.
   - **Text without a URL** → Main Screen with a snackbar explaining that only URLs and images can be extracted.
2. From there both screens continue through the Extraction Flow above — the user triggers extraction themselves.
3. **Unauthenticated user** → login screen; the shared content is discarded and the user lands on the Main Screen after
   signing in.
