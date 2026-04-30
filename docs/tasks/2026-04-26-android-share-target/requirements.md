# Android share target for recipe extraction

**Date:** 2026-04-26
**Type:** feature
**Status:** requirements

## Summary

Allow users to share a URL or an image from any Android app into RecipAI via
the native share sheet, landing them in the extraction screen with the shared
content pre-filled and ready to extract.

## Context

Today, getting a recipe into RecipAI from outside the app requires copying a
URL or saving an image and then opening RecipAI, navigating to extraction,
and pasting/uploading manually. The motivation is personal friction with this
flow — copy-paste is clunky and breaks the user's train of thought when they
encounter a recipe in a browser, social app, or gallery.

Registering RecipAI as an Android share target removes that friction: the
user shares straight into the extraction flow, with the content already
loaded.

iOS may follow later if an iOS version of RecipAI is built, but it is
explicitly out of scope for now.

## Requirements

- RecipAI appears as a single entry in the Android native share sheet.
- Supported share payloads:
  - A URL shared as `text/plain`.
  - A single image shared as `image/*`.
- Unsupported payloads (arbitrary text that is not a URL, multiple images,
  PDFs, other MIME types) must not cause RecipAI to appear as a share
  option.
- When the user picks RecipAI from the share sheet:
  - For a URL: the app opens the URL Extraction Screen with the URL
    pre-filled and the WebView loaded, stopped just before the extract
    button. The user clicks extract themselves to confirm the page loaded
    correctly.
  - For an image: the app opens the Image Extraction Screen with the image
    preview ready, stopped just before the extract button. The user clicks
    extract themselves.
- The flow must work on cold start (app process not running) and on warm
  start (app already running, foreground or background).
- If the user is not authenticated when the share is received:
  - The login screen is shown.
  - After successful login, the shared content is discarded and the user
    lands on the main screen (no preservation of shared content through
    login).

## Anti-requirements

- No iOS share-target support in this task. Architecture should not actively
  prevent it later, but no iOS work is in scope.
- No sharing *out* of RecipAI (e.g., sharing a recipe to other apps).
- No share targets for collections or shopping lists — only recipe
  extraction.
- No background or silent extraction. Sharing always opens the app and
  surfaces the extraction screen for user confirmation.
- No deep links beyond extraction (e.g., no "share a URL straight to the
  Create Recipe screen" path).
- No multi-image share — only a single image at a time.
- No preserving shared content across the login flow.
- No warning prompt when an in-progress edit or extraction is interrupted by
  an incoming share — discard is silent.

## Constraints & assumptions

- The existing `/extract/text` and `/extract/image` backend endpoints are
  assumed sufficient. If implementation reveals they are not, they may be
  changed.
- Android minimum SDK is whatever the current Flutter version requires
  (currently 24); no additional Android version constraint.
- Cold vs. warm start handling on Android is treated as a known unknown —
  cold start must work, but the exact mechanism is left to design.
- The existing extraction screens (URL and image) can be reused as the
  landing surfaces; this task adds a new way to enter them, not new
  extraction UIs.

## Acceptance criteria

- [ ] From Chrome: share a recipe URL → pick RecipAI → URL Extraction Screen
      opens with the URL pre-filled and the WebView loaded; the extract
      button is visible and the user can click it to extract.
- [ ] From a gallery app: share a single image → pick RecipAI → Image
      Extraction Screen opens with the image preview ready; the extract
      button is visible and the user can click it.
- [ ] Both flows above work when the RecipAI process is not running (cold
      start).
- [ ] Both flows above work when RecipAI is already running in the
      background (warm start).
- [ ] When unauthenticated, sharing into RecipAI triggers the login screen.
      After login, the user lands on the main screen and the shared content
      is gone.
- [ ] RecipAI does not appear as a share target for unsupported payloads
      (arbitrary non-URL text, multiple images, PDFs, other MIME types).
- [ ] If the user is mid-edit on another recipe or mid-extraction when a
      share arrives, those edits are silently discarded and the new
      extraction screen is shown.
- [ ] If the shared URL fails to load (offline, 404, blocked), the WebView
      shows its standard error/no-network page and the user can retry — no
      special handling needed.

## Edge cases

- **Non-recipe URL shared**: extraction screen still opens normally;
  downstream extraction may fail with the existing failure behavior.
- **Non-food image shared**: extraction screen still opens normally;
  downstream extraction may fail with the existing failure behavior.
- **Cold start**: hard requirement — must land in extraction with shared
  content after the app boots.
- **Mid-edit on another recipe when share arrives**: silently discard the
  in-progress edit, navigate to extraction.
- **Mid-extraction when a new share arrives**: silently replace current
  extraction state with the new shared content.
- **Multiple images selected**: should not be possible — RecipAI must not
  appear as a target for multi-image shares.
- **Offline at share time**: WebView shows its standard no-network page;
  user can retry from there. No app-level fallback needed.
- **Unauthenticated user**: login screen shown, shared content discarded
  after login, user lands on main screen.

## Integration points

- **Mobile, `features/extraction/`**: lives in this module as a new entry
  path. Existing `url_extraction_screen.dart` and `image_extraction_screen.dart`
  are the landing screens; they need to accept pre-filled content from a
  share intent in addition to the existing in-app entry from the Extraction
  Dialog.
- **Mobile, app/router level**: needs an Android intent handler that
  receives the share, classifies it (URL vs. image), checks auth, and
  dispatches into the right extraction screen — or to login if
  unauthenticated.
- **Mobile, Android manifest**: needs intent filters declaring RecipAI as a
  share target for `text/plain` (URL only) and `image/*` (single).
- **Backend, extraction module**: `/extract/text` and `/extract/image`
  assumed unchanged; flagged for review during design.

## Open questions

- Do the existing `/extract/text` and `/extract/image` endpoints suffice for
  the share-driven flow, or do they need adjustments? To be confirmed during
  design/implementation.
- Exact mechanism for cold-start intent handling in Flutter on Android — to
  be settled in design.
- How to filter `text/plain` shares so RecipAI only appears for URL-shaped
  text (vs. arbitrary text). May require a receiver-side check rather than
  a manifest-only filter.
