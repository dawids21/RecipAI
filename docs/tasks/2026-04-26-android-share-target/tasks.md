# Android share target for recipe extraction — Tasks

**Date:** 2026-04-26
**Status:** T1 complete — T2 pending

## Summary

- **T1:** Text share target — manifest filter, share-intent plumbing, URL pre-fill, non-URL snackbar
- **T2:** Image share target — manifest filter, cache-write bridge, image pre-fill

Order is by dependency: T1 builds the cross-cutting plumbing (platform channels, `ShareIntentService`, payload sealed class, route `extra` wiring, post-first-frame trigger, auth gating) and proves it end-to-end on the simpler payload. T2 layers the image filter and binary-payload path onto the same plumbing.

## Cross-task notes

- T1 carries the infra deliberately (channels, service, DI setup, route `extra` wiring, manifest scaffolding inside `MainActivity`, `MainActivity.kt` overrides, `ShareIntentBridge` skeleton). T2 extends `ShareIntentBridge` with the image-copy path and adds a second `<intent-filter>` block — it must not duplicate channel or service registration.
- The manifest assumption flagged in `design.md` > **Assumptions to verify** (launcher icon, resume-from-recents, share-sheet appearance) should be re-checked at the end of *both* tasks: T1 introduces the first `<intent-filter>` siblings to the launcher filter; T2 adds another and could regress merging.
- No feature flag — share registration is on as soon as the manifest ships. Land tasks in order on `main`.

---

## T1: Text share target — plumbing, URL pre-fill, non-URL snackbar

**User-visible outcome**

An end user who shares a link from Chrome (or any app sending `text/plain`) can pick RecipAI from the Android share sheet and land on the URL extraction screen with the URL already filled in and the WebView loading it; sharing non-URL text instead drops them on the main screen with a snackbar explaining the limitation. Works whether RecipAI was running or cold-started.

**Scope**

- `AndroidManifest.xml` `<intent-filter>` for `ACTION_SEND` + `text/plain` under existing `MainActivity` (see design > **Integration changes**).
- `ShareIntentBridge.kt` skeleton: `attach`, `stageInitialShare`, `handleNewIntent`, `MethodChannel`/`EventChannel` registration, text payload extraction. (No image path yet — that's T2.)
- `MainActivity.kt` overrides for `configureFlutterEngine`, `onCreate`, `onNewIntent`.
- New Dart files in `features/share_intent/`: `share_payload.dart` (sealed class, full hierarchy including `ImageSharePayload` placeholder so T2 only adds the construction path), `share_intent_service.dart`, `share_intent_setup.dart`.
- `main.dart`: `scaffoldMessengerKey`, `setupShareIntent(...)`, post-first-frame `consumeInitialShare()`.
- `core/routes.dart`: `urlExtraction` and `imageExtraction` builders read typed `state.extra` wrappers (`UrlPrefill`, `ImagePrefill`). `ImagePrefill` plumbing lands here even though it has no producer until T2 — keeps the routing change atomic.
- `url_extraction_screen.dart`: `initialUrl` parameter, `_loadUrlInternal` refactor, pre-fill in `initState`.
- Auth gating, URL classification, and non-URL snackbar via root `ScaffoldMessenger` (design > **Flows & state**).

**Out of scope**

- Image manifest filter, image cache write, `ImageSharePayload.fromMap`, `image_extraction_screen.dart` pre-fill — covered in T2.
- Persisting payload across login — design > **Out of scope (design-level)**.
- Mid-edit confirmation prompt — anti-requirement.
- iOS share extension — deferred.

**Depends on:** none

**Design references**

- `design.md` > **Approach** (especially the URL-filter-in-Dart rationale)
- `design.md` > **Module & component boundaries**
- `design.md` > **Interface contracts** — platform channels, payload map, `ShareIntentService` API
- `design.md` > **Flows & state** — cold and warm start
- `design.md` > **Integration changes** — note the `_loadUrlInternal` refactor detail
- `design.md` > **Assumptions to verify** — re-run the launcher / resume / share-sheet checks before merging

**How to verify**

On a physical Android device with a debug build:

1. Cold start: kill RecipAI, share a URL from Chrome → app launches and the URL extraction screen opens with the URL in the input and the WebView loading it. The Extract button is *not* auto-pressed.
2. Warm start: open RecipAI, background it, share a different URL → URL extraction screen replaces whatever was on top, pre-filled with the new URL.
3. Non-URL text: share "hello world" from any text app → app opens to the main screen and a snackbar reads *"RecipAI can only extract recipes from URLs or images."*
4. Unauthenticated: log out, share a URL → app lands on `/login`, no extraction screen, no snackbar; subsequent successful login lands on a clean main screen.
5. Launcher regression check: tap the RecipAI launcher icon → main screen, no snackbar, no extraction route.
6. Resume-from-recents check: background and resume from recents → no `onNewIntent` log line from `ShareIntentBridge.handleNewIntent`.

**Risks / unknowns**

- `_loadUrlInternal` refactor must avoid `FocusScope.of(context)` during `initState`. Verify by hot-restarting directly into the share flow — no exceptions in the log.
- `consumeInitialShare()` post-first-frame trigger must run *after* the auth redirect resolves; if the redirect is async, an early `goNamed(urlExtraction)` could be overridden by the redirect. Check by sharing a URL while logged in vs logged out and confirming both end states.

**Status:** complete (2026-04-28) — all six How to verify steps confirmed on device.

**Implementation deviations**

1. **`super.onCreate` order** — `design.md` says to call `stageInitialShare` before `super.onCreate`; implemented with `super.onCreate` first (conventional `FlutterActivity` order). `stageInitialShare` only writes to a static field and `consumeInitialShare` is post-first-frame, so order is safe. Confirmed correct on device; `design.md` should be updated to match.
2. **`setupExtraction` parameter** — added optional `ExtractionRepository?` parameter to `setupExtraction()` (consistent with DI standard; all other `setup*` functions already accept an optional repository). Not in original scope but required for future widget tests.
3. **Widget test for `initialUrl` skipped** — `WebViewController` in test environments requires `WebViewPlatform` mock setup not yet in place. The pre-fill behaviour was verified on device instead. To add the test later, set up a `MockWebViewPlatform` in the test harness.
4. **Dart files in `features/extraction/` (not `features/share_intent/`)** — per earlier feedback, all new files live flat in `features/extraction/` rather than a separate `features/share_intent/` directory. `tasks.md` scope text still references `features/share_intent/`; the actual files are in `features/extraction/`.
5. **`ShareIntentBridge.handleNewIntent` has no Logcat output** — resume-from-recents was verified by absence of unexpected navigation, not by a log line. Consider adding a log statement before T2 if Logcat confirmation is important for ongoing debugging.

---

## T2: Image share target — manifest filter, cache-write bridge, image pre-fill

**User-visible outcome**

An end user who shares a single image from the gallery, a messaging app, or the camera app can pick RecipAI from the share sheet and land on the image extraction screen with the shared image already shown in the preview, ready to confirm with Extract. Works cold and warm.

**Scope**

- Second `<intent-filter>` in `AndroidManifest.xml` for `ACTION_SEND` + `image/*` (no `ACTION_SEND_MULTIPLE`).
- `ShareIntentBridge.kt`: image branch — `EXTRA_STREAM` content-URI read, copy to `cacheDir/share_intent/<timestamp>-<random>.<ext>`, directory wipe at start of each share (design > **State machine — image cache file lifecycle**).
- `ImageSharePayload.fromMap` construction in `share_payload.dart`.
- `ShareIntentService` image-branch routing (`router.goNamed(imageExtraction, extra: ImagePrefill(file))`).
- `image_extraction_screen.dart`: `initialImageFile` parameter; `_selectedImage = XFile(initialImageFile.path)` in `initState`.

**Out of scope**

- Multiple images, PDFs, arbitrary files — design > **Out of scope (design-level)**.
- Auto-pressing Extract — anti-requirement.
- Cache eviction beyond the per-share directory wipe — OS handles it (design > **State machine**).

**Depends on:** T1

**Design references**

- `design.md` > **Approach** — image branch
- `design.md` > **Interface contracts** — `imagePath` field, `ImageSharePayload`, `ImageExtractionScreen` pre-fill
- `design.md` > **Flows & state** — image case in cold/warm flows; cache lifecycle
- `design.md` > **Integration changes** — `image_extraction_screen.dart` and the `XFile(path)` MIME-inference note
- `design.md` > **Assumptions to verify** — re-run launcher / share-sheet checks now that two filters coexist

**How to verify**

On a physical Android device:

1. Cold start: kill RecipAI, open the gallery, share a recipe screenshot → app opens directly into the image extraction screen with the screenshot in the preview. Tapping Extract runs the existing image-extraction flow against `/extract/image` and produces a recipe draft.
2. Warm start: with RecipAI in the background, share a different image → image extraction screen replaces the current top route, preview shows the new image.
3. Cache hygiene: `adb shell run-as xyz.stasiak.recipai ls cache/share_intent/` after a share shows at most one file; sharing again replaces it.
4. Mixed-payload regression: re-run all six T1 verification steps — text shares must still behave correctly with both filters in the manifest.
5. Launcher regression check (again): the launcher icon still opens to a clean main screen.

**Risks / unknowns**

- Some senders deliver images via `content://` URIs that require `FLAG_GRANT_READ_URI_PERMISSION`; the bridge must read via `ContentResolver.openInputStream`, not `File(uri.path)`. Verify against at least one cloud-backed source (e.g., Google Photos) and one local source (gallery).
- `XFile(path)` round-trip relies on `lookupMimeType(path)` matching the cached extension — confirm the bridge preserves the original extension (or maps the MIME type to one) when copying.
