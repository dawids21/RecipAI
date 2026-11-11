# Extraction — UI

## Screens

Both extraction screens show the `used / limit` counter (with "resets in ..." when the balance carries a countdown)
and disable the extract action at the quota — see `docs/mobile/modules/limits/module.md` → Services and State.

- URL Extraction Screen (`url_extraction_screen.dart`) - An in-app browser for extracting the recipe on the page
  being viewed:
  - *Address field*: a domain (example.com, sub.example.co.uk, localhost:3000) or a full URL (https://example.com)
    opens that page; anything else runs a Google search for it.
  - *Extract*: "Extract Recipe" extracts the page currently shown and opens the Create Recipe Screen pre-filled with
    the result and the page's URL as the source.
  - *Back*: goes back in the page history first, and leaves the screen only when there is no page to go back to.
- Image Extraction Screen (`image_extraction_screen.dart`) - Pick a photo from the camera or gallery, preview it, and
  tap "Extract Recipe"; opens the Create Recipe Screen pre-filled with the result and the photo as a pending image.
- Extraction Dialog (`extraction_dialog.dart`) - "Extract Recipe" dialog offering "Extract from URL" and "Extract from
  Image".

## Flow

#### Extraction Flow

1. **Speed Dial FAB → Extract Tap** (on Recipes tab) → Extraction Dialog → URL/Image Extraction Screen
   (`/recipes/url-extraction` or `/recipes/image-extraction`)
2. **Successful URL Extraction** → Create Recipe Screen with pre-filled extracted data, source URL, and collection (if
   filter active) → Recipe creation → Back to Main Screen
3. **Successful Image Extraction** → Create Recipe Screen with pre-filled extracted data, pending image, and collection
   (if filter active) → Recipe creation → Back to Main Screen

#### Share Intent Flow

1. **Android share sheet → RecipAI** → the shared content opens:
   - **Text containing a URL** → URL Extraction Screen with that URL loaded (which text counts as a URL is in
     `module.md` → Services and State).
   - **Image** → Image Extraction Screen with the image preview ready.
   - **Text without a URL** → Main Screen with a snackbar explaining that only URLs and images can be extracted.
2. From there both screens continue through the Extraction Flow above — the user triggers extraction themselves.
3. **Unauthenticated user** → Login Screen; after signing in the user lands on the Main Screen and the share is lost
   (see `module.md` → Services and State).
