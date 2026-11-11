# Extraction Module

Turns a web page or a photo into recipe data with the backend's AI extraction, then hands the result to the recipe
create screen to review and save. Also makes the app an Android share target: a shared URL or image opens the
matching extraction screen pre-filled.

## Boundary

- **Backend endpoints**: `GET /extract/balance`, `POST /extract/text`, `POST /extract/image`
  (`docs/backend/modules/extraction/api.md`).
- **Uses**: `auth`; `limits` (extraction counter and quota); `recipe` (the result is passed to the create screen as
  `InitialRecipeFormData`).
- **Used by**: `recipe` (the Recipes tab's Extract action opens the Extraction Dialog), `core` (routes, share-intent
  wiring in `main()`).

## File Map

```
mobile/lib/features/extraction/
├── extraction_repository.dart   # /extract endpoints
├── extraction_service.dart      # Extraction balance and the two extract calls
├── extraction_setup.dart
├── extracted_recipe.dart        # ExtractedRecipe, ExtractedIngredient, ExtractedInstruction
├── url_extraction_screen.dart   # WebView browser; extracts the page currently shown
├── image_extraction_screen.dart # Camera/gallery pick, preview and extract
├── extraction_dialog.dart       # Choose URL or image extraction
├── web_recipe_extractor.dart    # Reads the page HTML out of the WebView
├── share_intent_service.dart    # Receives Android shares and routes them (see Services and State)
├── share_intent_setup.dart
├── share_payload.dart           # SharePayload: URL, non-URL text, or image
└── share_route_extras.dart      # UrlPrefill/ImagePrefill route extras carrying the shared content
```

```
mobile/test/features/extraction/
└── share_payload_test.dart      # Share classification: URL, URL in text, non-URL text, image
```

## Services and State

- **`ExtractionService`** — lazy singleton, never reset, so it lives for the app's lifetime. Holds only the
  extraction balance, reloaded each time an extraction screen opens; the extract calls return the result to the
  calling screen and keep nothing.
- **`ShareIntentService`** — app-lifetime singleton registered after the router. It receives a cold-start share once,
  after the first frame, and warm-start shares as they arrive, over the `recipai/share` platform channel. A share
  received while signed out is discarded, not deferred until sign-in. Where each kind of share goes is in `ui.md` →
  Share Intent Flow.
- **Which shared text counts as a URL.** Text that is entirely a URL is taken as-is; otherwise the first URL embedded
  in it is used (e.g. "Check this recipe: https://cookbook.com/recipe1"). Inside text only `http(s)://` and
  `www.`-prefixed URLs are recognised, since bare domains would too easily match ordinary prose, and `www.` matches
  are normalised to `https://`.
