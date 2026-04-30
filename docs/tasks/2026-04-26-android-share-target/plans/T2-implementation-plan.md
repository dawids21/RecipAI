# T2: Image share target — manifest filter, cache-write bridge, image pre-fill — Implementation Plan

**Date:** 2026-04-28
**Status:** final

## Required reading

**Docs & standards** (from `docs/INDEX.md`)

- `docs/mobile/standards/architecture.md` — confirms the new image-pre-fill code stays in `features/extraction/` (flat, no sub-folder), per the same convention T1 followed.
- `docs/mobile/standards/navigation.md` — `goNamed` + typed `state.extra` is how the share service hands the image off to the route; `ImagePrefill` plumbing already lives in `routes.dart` from T1.
- `docs/mobile/modules/extraction/codebase_structure.md` and `docs/mobile/modules/extraction/ui.md` — `ImageExtractionScreen` is the screen being extended.

**Design & ADRs**

- `design.md` > **Approach** — image branch of the bridge, `EXTRA_STREAM` content-URI handling.
- `design.md` > **Interface contracts** — payload map shape (`type: "image"`, `imagePath: String`), `ImageSharePayload`, `ImageExtractionScreen.initialImageFile` semantics.
- `design.md` > **Flows & state** — image case in cold/warm flows; **State machine — image cache file lifecycle** for the directory-wipe rule.
- `design.md` > **Integration changes** — the `XFile(path)` MIME-inference note (`ExtractionRepository.extractRecipeFromImage` calls `lookupMimeType(path)`, so the cached extension must match the original image type).
- `design.md` > **Assumptions to verify** — re-run launcher / resume / share-sheet checks now that two `<intent-filter>` siblings coexist.

**Code to mirror**

- `mobile/android/app/src/main/kotlin/xyz/stasiak/recipai/ShareIntentBridge.kt` — extend the existing `extractTextPayload` pattern with an `extractImagePayload` sibling; reuse the `attach(messenger, context)` entry point (the `context` parameter is already plumbed through and currently unused — T2 finally needs it for `cacheDir`).
- `mobile/lib/features/extraction/share_payload.dart` — the existing `fromMap` text branch; the `image` branch currently returns `null` with a `T2 wires` comment — replace that.
- `mobile/lib/features/extraction/image_extraction_screen.dart` (lines 26–28, current `initState` is the default) — add an `initState` that pre-fills `_selectedImage = XFile(initialImageFile.path)` when non-null. Mirror `url_extraction_screen.dart` lines 36–58 for the "use the pre-fill on first build, then behave normally" pattern.
- `mobile/lib/features/extraction/extraction_repository.dart` (`extractRecipeFromImage`, lines 45–80) — confirms `XFile.path` + `lookupMimeType(path)` is the only data the upload needs; the bridge must preserve a meaningful extension when copying.

## File inventory

**Native (Android)**

- **MODIFY** `mobile/android/app/src/main/AndroidManifest.xml` — add a second sibling `<intent-filter>` for `ACTION_SEND` + `image/*` under `MainActivity`, alongside the existing LAUNCHER and `text/plain` SEND filters. No `ACTION_SEND_MULTIPLE`.
- **MODIFY** `mobile/android/app/src/main/kotlin/xyz/stasiak/recipai/ShareIntentBridge.kt` — store the `Context` from `attach()` for use by the image path; replace the `wipeShareCacheDir(context: Context?)` no-op with a real implementation that deletes the contents of `cacheDir/share_intent/` (creating the directory if absent); add `extractImagePayload(intent: Intent): Map<String, Any>?` that reads `EXTRA_STREAM` as a `Uri`, copies the bytes through `ContentResolver.openInputStream(uri)` to `cacheDir/share_intent/<timestamp>-<random>.<ext>`, and returns `mapOf("type" to "image", "imagePath" to <absolutePath>)`; route both `stageInitialShare` and `handleNewIntent` through the new combined extractor (text first, image fallback).

**Dart (Flutter)**

- **MODIFY** `mobile/lib/features/extraction/share_payload.dart` — `fromMap` `type == "image"` branch: read `imagePath` as `String`, return `ImageSharePayload(File(imagePath))` (or `null` if the field is missing/empty). Remove the `T2 wires` comment.
- **MODIFY** `mobile/lib/features/extraction/image_extraction_screen.dart` — add `initState()` that calls `super.initState()` then, when `widget.initialImageFile != null`, sets `_selectedImage = XFile(widget.initialImageFile!.path)`. No `setState` (initial state is set before first build). The Extract button is *not* auto-pressed — the user still confirms.

**Tests**

- _None._ Per the same testing posture as T1 (deviation #3 in `tasks.md`), the image pre-fill is verified on device. The bridge has no JVM unit-test infrastructure (T1 deviation, plan §Test plan).

(No changes to `share_intent_service.dart`, `share_intent_setup.dart`, `share_route_extras.dart`, `routes.dart`, `main.dart`, or `MainActivity.kt` — T1 already wired all of these for both branches.)

## Step-by-step plan

Each step ends with `flutter analyze` clean and existing tests green.

1. **Wire `ImagePrefill` into `ImageExtractionScreen.initState`** — add an `initState()` override that pre-fills `_selectedImage = XFile(widget.initialImageFile!.path)` when non-null. Done as the first step so the Dart side compiles end-to-end before native changes; if a manual share triggers any path before the bridge is finished, the screen still renders correctly when given a real file.
   - Files: `mobile/lib/features/extraction/image_extraction_screen.dart`.
   - Verify: `flutter analyze` clean from `mobile/`. Hot-restart the app, manually navigate to `imageExtraction` with a synthetic `ImagePrefill(File('/tmp/test.jpg'))` via a debug entry-point (or skip and rely on later device verification) — preview renders without crashing.

2. **Wire `ImageSharePayload` construction in `share_payload.dart`** — replace the `type == "image"` early-return with `final path = map['imagePath'] as String?; if (path == null || path.isEmpty) return null; return ImageSharePayload(File(path));`. Drop the `T2 wires` comment.
   - Files: `mobile/lib/features/extraction/share_payload.dart`.
   - Verify: `flutter analyze` clean.

3. **Make `ShareIntentBridge` hold the application `Context`** — change the `private var stagedPayload` block to also include `private var appContext: Context? = null` (or store it in `attach()`). Update `attach(messenger, context)` to set `appContext = context.applicationContext`. Update `wipeShareCacheDir` to take no parameter and read from the field; if `appContext` is null, no-op (defensive — `attach` always runs first in `configureFlutterEngine`).
   - Files: `mobile/android/app/src/main/kotlin/xyz/stasiak/recipai/ShareIntentBridge.kt`.
   - Verify: `cd mobile/android && ./gradlew :app:assembleDebug` succeeds.

4. **Implement the cache directory wipe** — `wipeShareCacheDir()` resolves `File(appContext!!.cacheDir, "share_intent")`, creates it with `mkdirs()` if missing, and deletes any existing files inside (`listFiles()?.forEach { it.delete() }`). Called at the start of every `stageInitialShare` and `handleNewIntent` (already wired in T1).
   - Files: `mobile/android/app/src/main/kotlin/xyz/stasiak/recipai/ShareIntentBridge.kt`.
   - Verify: `./gradlew :app:assembleDebug` succeeds. After running the app once, `adb shell run-as xyz.stasiak.recipai ls cache/share_intent/` should show the empty directory after a share (text or image).

5. **Add `extractImagePayload(intent: Intent): Map<String, Any>?` to the bridge** — in the same Kotlin file:
   - Guard: `intent.action == Intent.ACTION_SEND` and `intent.type?.startsWith("image/") == true`.
   - Read the URI: `val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)`. Return `null` if `uri` is null.
   - Resolve a sensible extension: `val mime = appContext?.contentResolver?.getType(uri) ?: intent.type ?: "image/jpeg"`; `val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"`.
   - Build the destination: `val dir = File(appContext!!.cacheDir, "share_intent").apply { mkdirs() }`; filename `"${System.currentTimeMillis()}-${Random.nextInt(0, Int.MAX_VALUE).toString(16)}.$ext"`; `val outFile = File(dir, name)`.
   - Copy the bytes: `appContext!!.contentResolver.openInputStream(uri)?.use { input -> outFile.outputStream().use { output -> input.copyTo(output) } } ?: return null`.
   - Return `mapOf("type" to "image", "imagePath" to outFile.absolutePath)`.
   - Update `stageInitialShare` and `handleNewIntent` to fall through to the image extractor if the text extractor returns null: `val payload = extractTextPayload(intent) ?: extractImagePayload(intent) ?: return`.
   - Files: `mobile/android/app/src/main/kotlin/xyz/stasiak/recipai/ShareIntentBridge.kt`.
   - Verify: `./gradlew :app:assembleDebug` succeeds. Sharing an image still does nothing yet (manifest filter is added in step 6).

6. **Add the `image/*` manifest filter** — sibling to the existing two filters inside `<activity android:name=".MainActivity">`:
   ```xml
   <intent-filter>
       <action android:name="android.intent.action.SEND"/>
       <category android:name="android.intent.category.DEFAULT"/>
       <data android:mimeType="image/*"/>
   </intent-filter>
   ```
   Three sibling `<intent-filter>` blocks now: LAUNCHER, SEND text/plain, SEND image/\*. **No `ACTION_SEND_MULTIPLE`** (single-image requirement).
   - Files: `mobile/android/app/src/main/AndroidManifest.xml`.
   - Verify: `./gradlew :app:assembleDebug` succeeds. `adb shell dumpsys package xyz.stasiak.recipai | grep -A2 MainActivity` shows three distinct filters.

7. **End-to-end device verification** — install the debug build on a physical Android device and run all five `tasks.md` > T2 "How to verify" steps verbatim, plus re-run the six T1 steps for the mixed-payload regression check.
   - Verify: each step listed under "Manual verification" below.

## Test plan

**Unit tests**

- _N/A — `share_payload_test.dart` and `share_intent_service_test.dart` were excluded per feedback during T1; the same posture applies to the image branch._

**Dart widget tests**

- _N/A — testing the image pre-fill in isolation has no value beyond what device verification covers, and the screen has no `WebView`-style platform-channel blocker (an `XFile`-backed `Image.file` should render under widget tests). One could be added later, but per the same posture as T1 (deviation #3, where the URL pre-fill widget test was skipped), tests for this PR are device-level only._

**Kotlin tests**

- _N/A — same reasoning as T1: no JVM unit-test infrastructure for the Android module; bridge behaviour is covered by device-level manual verification._

**Manual verification (physical Android device)**

Run all five `tasks.md` > T2 "How to verify" steps. Concrete cases:

- **Cold start, gallery share** — kill RecipAI; in the gallery, long-press a recipe screenshot → Share → RecipAI. App launches directly into Image Extraction; preview shows the screenshot. Tap **Extract** → existing `/extract/image` flow runs; recipe draft appears.
- **Cold start, cloud-backed source (Google Photos)** — same flow with a photo not yet downloaded locally; the bridge must read via `ContentResolver.openInputStream` (not `File(uri.path)`). Verify the file lands in `cache/share_intent/` and Extract still works.
- **Warm start** — open RecipAI and background it, share a different image → Image Extraction screen replaces the current top route, preview shows the new image.
- **Cache hygiene** — `adb shell run-as xyz.stasiak.recipai ls cache/share_intent/` after a share shows exactly one file. Share again; the previous file is gone, replaced by one new file. Share text; the directory is wiped (zero files).
- **Mixed-payload regression — text** — re-run all six T1 verification steps verbatim with both filters in the manifest:
  1. Cold-start URL share → URL extraction screen, pre-filled, WebView loads.
  2. Warm-start URL share → URL extraction replaces top route.
  3. Non-URL text share → main + snackbar.
  4. Unauthenticated URL share → `/login`, no extraction screen, no snackbar after login.
  5. Launcher tap → main screen, no snackbar, no extraction.
  6. Resume from recents → no `onNewIntent` in Logcat.
- **Launcher regression check** — tap RecipAI launcher icon → main screen, no extraction route, no snackbar.
- **Filter merge inspection** — `adb shell dumpsys package xyz.stasiak.recipai | grep -A2 MainActivity` shows three distinct intent-filter blocks (LAUNCHER, SEND text/plain, SEND image/\*).
- **Extension preservation** — share a `.png` screenshot and a `.jpg` photo; `ls cache/share_intent/` shows the correct extension (or at minimum a non-empty extension that `lookupMimeType` resolves to a valid `image/*` MIME). Verify by tapping Extract and confirming the multipart upload's `Content-Type` is appropriate (server-side log or response success).

## Verification checklist

- [ ] `flutter analyze` passes from `mobile/`.
- [ ] `dart format --set-exit-if-changed lib/ test/` passes.
- [ ] `flutter test` passes (no new tests; existing suite must not regress).
- [ ] `cd mobile/android && ./gradlew :app:assembleDebug` succeeds.
- [ ] All five `tasks.md` > T2 "How to verify" steps succeed on a physical Android device.
- [ ] All six `tasks.md` > T1 "How to verify" steps still succeed (mixed-payload regression).
- [ ] `design.md` > Assumptions to verify — re-run launcher / resume / share-sheet checks now that three filters coexist; record results in the PR description.
- [ ] Manifest contains three sibling `<intent-filter>` blocks (LAUNCHER + SEND text/plain + SEND image/\*), not merged into one.
- [ ] `cache/share_intent/` contains at most one file at any time after a share, and is wiped on every new share (text or image).
- [ ] Image upload on Extract succeeds for both gallery (local file URI) and Google Photos (cloud-backed `content://` URI).
- [ ] No new analyzer warnings; no new Logcat errors during share flows.

## Risks surfaced during planning

- **Risk:** `Context` lifecycle in the singleton `ShareIntentBridge`. T1 plumbed `context` through `attach()` but ignored it; T2 stores it as `appContext = context.applicationContext`. Storing the activity context would leak; the application context is safe but if `attach()` is somehow called with a non-application context the singleton could keep a stale reference across activity recreation.
  **Why it matters:** Memory leak on configuration changes, or NPEs during share extraction if the field is ever cleared between `attach` and `stageInitialShare`.
  **Mitigation:** Always call `.applicationContext` in `attach()` before storing; never null out the field after assignment (the bridge is `object` — process-scoped). Verify on device by rotating the app and re-sharing.

- **Risk:** `Intent.getParcelableExtra(name)` is deprecated on API 33+ in favour of `getParcelableExtra(name, Class)`. Mixing these incorrectly silently returns `null` on some API levels.
  **Why it matters:** Image shares from older / newer devices could intermittently return no payload.
  **Mitigation:** Branch on `Build.VERSION.SDK_INT >= 33`, suppressing the deprecation on the legacy branch (see step 5). Verify on at least one device with API 33+ and one with API < 33 (or rely on a single device + Android Studio emulator at a different API level if a second device isn't available).

- **Risk:** `MimeTypeMap.getSingleton().getExtensionFromMimeType(...)` can return `null` for some image MIME types (e.g. `image/heif` on some Android versions, or vendor-specific MIME strings supplied by `ContentResolver.getType`).
  **Why it matters:** A `null` extension produces a filename like `<ts>-<rand>.null`, which `lookupMimeType` on the Dart side resolves to `null`, which `ExtractionRepository.extractRecipeFromImage` sends as a multipart part with no `Content-Type`. The backend's image extractor may reject it.
  **Mitigation:** Fall back to `"jpg"` when the extension lookup returns `null` (already in step 5 as `?: "jpg"`). HEIC/HEIF support is out of scope; if it surfaces in device testing, capture as a follow-up rather than blocking T2.

- **Risk:** `ImageExtractionScreen.initState` did not previously override `initState`; adding one is the first stateful behaviour on the screen and could race with `_buildImagePreview`'s `_selectedImage == null` check on the very first build if pre-fill runs after build.
  **Why it matters:** A blink of "No image selected" before the preview appears would be a regression vs. the gallery flow (where the preview only renders after `_pickImage` resolves and `setState` runs).
  **Mitigation:** Set `_selectedImage` directly in `initState` (no `setState`) — first build sees the populated field. Verify on device that the cold-start share lands directly on the populated preview with no flash.
