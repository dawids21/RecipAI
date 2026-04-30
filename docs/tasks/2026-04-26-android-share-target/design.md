# Android share target for recipe extraction — Design

**Date:** 2026-04-26
**Status:** draft

## Overview

Register `MainActivity` as an Android share target via manifest intent filters
for `text/plain` and `image/*` (single). A new `ShareIntentService` on the Dart
side bridges native intents to `go_router`: it pulls the cold-start payload
once via a `MethodChannel`, then listens to an `EventChannel` for warm-start
shares. The service classifies payloads, checks auth, and routes into the
existing `urlExtraction` / `imageExtraction` screens, which gain optional
pre-fill constructor parameters.

## Required reading for implementation

- `docs/mobile/standards/architecture.md` — Repository/Service/View layering
  the new `ShareIntentService` follows.
- `docs/mobile/standards/state-management.md` — `ValueNotifier<AsyncValue<T>>`
  is not used here (no async state to expose), but the service-class lifecycle
  conventions (`dispose()`, no `getIt` inside the class) still apply.
- `docs/mobile/standards/dependency-injection.md` — pattern for the new
  `setupShareIntent()` and how external (`MethodChannel`) dependencies are
  wired.
- `docs/mobile/standards/navigation.md` — `AppRoute` enum and how
  `context.goNamed` is used for screen entry.
- `docs/mobile/modules/extraction/ui.md` and `codebase_structure.md` — the two
  screens being extended.
- `docs/mobile/modules/core/ui.md` — `MainScreen` is where the share listener
  needs a valid `BuildContext` / `GoRouter`.

## Approach

Native side: `MainActivity.kt` (Kotlin) handles `Intent.ACTION_SEND` in
`onCreate` (cold start) and `onNewIntent` (warm start). It reads
`EXTRA_TEXT` or `EXTRA_STREAM`, copies image bytes from the content URI into
the app's cache directory under a controlled filename, and stages a typed
payload object. Two Flutter platform channels are exposed:

- `MethodChannel("recipai/share")` with a single `consumeInitialShare` method —
  Dart asks for the cold-start payload exactly once after the engine is ready.
- `EventChannel("recipai/share/events")` — emits a payload each time
  `onNewIntent` fires while Flutter is running.

Dart side: a new `ShareIntentService` (in `features/extraction/`, alongside the
existing extraction repository, services, and screens) owns both channels. It is constructed during `main()` after the router is built, with
the `GoRouter` instance, an `AuthService`, and a
`GlobalKey<ScaffoldMessengerState>` (also passed to `MaterialApp.router` as
`scaffoldMessengerKey`) for non-URL-text snackbars. The service uses
`router.goNamed(...)` directly — `go_router` 17 supports calling `goNamed`
on the `GoRouter` instance without a `BuildContext`. It:

1. Calls `consumeInitialShare()` after the first frame of `MainScreen`.
2. Subscribes to the event stream for the lifetime of the app.
3. For each payload: classifies (`url`, `nonUrlText`, `image`), checks auth,
   and either navigates into the matching extraction screen with pre-fill or
   surfaces a snackbar (non-URL text) or drops the payload (unauthenticated;
   the auth redirect already steers to `/login`, and on subsequent successful
   login the cleared payload yields a clean main-screen landing).

The two extraction screens grow optional constructor parameters
(`initialUrl: String?` / `initialImageFile: File?`). When set, they pre-fill
on first build and load the WebView / show the preview without auto-clicking
extract — the user still confirms.

Cold-vs-warm asymmetry maps cleanly onto pull (`MethodChannel`) + push
(`EventChannel`).

**Why URL filtering is in Dart, not the manifest:** Android `<data>` filters
match on URI scheme/host/path/MIME — not on the contents of `EXTRA_TEXT`. Apps
that share URLs almost never call `setData`; they set `EXTRA_TEXT` only. A
`<data android:scheme="https"/>` filter would therefore suppress RecipAI from
the share sheet for the exact flows we want to support. The manifest declares
`ACTION_SEND` + `text/plain` with no additional data constraint, and Dart
classifies the payload. On non-URL text the service navigates to `/` and shows
a snackbar; silent drop was rejected because the user would see RecipAI in the
share sheet, tap it, and watch nothing happen.

## Module & component boundaries

**New files:**

- `mobile/lib/features/extraction/share_intent_service.dart` — the service
  described above. Owns the `MethodChannel`, `EventChannel` subscription, and
  navigation dispatch.
- `mobile/lib/features/extraction/share_payload.dart` — a sealed class
  hierarchy: `SharePayload` with subtypes `UrlSharePayload(String url)`,
  `NonUrlTextSharePayload(String text)`, `ImageSharePayload(File file)`.
  Constructed from the platform-channel `Map`.
- `mobile/lib/features/extraction/share_intent_setup.dart` — `setupShareIntent({...})`
  registering `ShareIntentService` as a singleton with `dispose`.
- `mobile/lib/features/extraction/share_route_extras.dart` — typed wrappers
  `UrlPrefill { final String url; }` and `ImagePrefill { final File file; }`
  used for `state.extra` on the extraction routes.
- `mobile/android/app/src/main/kotlin/xyz/stasiak/recipai/ShareIntentBridge.kt` —
  Kotlin object encapsulating channel registration, intent extraction, and the
  cache-write helper. Keeps `MainActivity` thin.

The new Dart files live flat inside the existing `features/extraction/`
directory — they are part of the extraction surface (a third entry-point
alongside the URL and image screens) and per the architecture standard each
feature directory keeps all layers flat with no sub-folders.

**Existing files extended:**

- `mobile/android/app/src/main/AndroidManifest.xml` — adds two `<intent-filter>`
  blocks under the existing `MainActivity`.
- `mobile/android/app/src/main/kotlin/xyz/stasiak/recipai/MainActivity.kt` —
  overrides `configureFlutterEngine`, `onCreate`, `onNewIntent` to delegate to
  `ShareIntentBridge`.
- `mobile/lib/features/extraction/url_extraction_screen.dart` — adds
  `initialUrl` parameter and pre-fill logic in `initState`.
- `mobile/lib/features/extraction/image_extraction_screen.dart` — adds
  `initialImageFile` parameter and pre-fill logic in `initState`.
- `mobile/lib/core/routes.dart` — `urlExtraction` and `imageExtraction`
  builders read the optional pre-fill from `state.extra` (typed wrapper).
- `mobile/lib/main.dart` — calls `setupShareIntent(...)` after the router is
  created, and triggers `consumeInitialShare()` after first frame.

## Data model changes

_No data model changes._ Image payloads are written to the app's
`cacheDir/share_intent/<timestamp>-<random>.<ext>` and consumed once by Dart;
no DB, no `SharedPreferences`.

## Interface contracts

### Platform channels

`MethodChannel("recipai/share")`:

- `consumeInitialShare()` → `Map<String, Object>?`. Returns the staged
  cold-start payload exactly once and clears it. `null` if the app was launched
  without a share intent or the payload has already been consumed.

`EventChannel("recipai/share/events")`:

- Stream of `Map<String, Object>` — one event per `onNewIntent` carrying a
  share. No backpressure; the most recent share replaces any prior.

### Payload map shape

```
{
  "type": "text" | "image",
  "text": String?,        // present iff type == "text"
  "imagePath": String?,   // present iff type == "image"; absolute path in cache
}
```

Dart parses this into `SharePayload`:

```dart
sealed class SharePayload {}
class UrlSharePayload extends SharePayload { final String url; ... }
class NonUrlTextSharePayload extends SharePayload { final String text; ... }
class ImageSharePayload extends SharePayload { final File file; ... }

SharePayload? SharePayload.fromMap(Map<String, Object> map);
```

### `ShareIntentService` (Dart)

```dart
class ShareIntentService {
  ShareIntentService({
    required AuthService authService,
    required GoRouter router,
    required GlobalKey<ScaffoldMessengerState> scaffoldMessengerKey,
    MethodChannel? methodChannel,
    EventChannel? eventChannel,
  });

  Future<void> consumeInitialShare();   // called once, after first frame
  void dispose();                       // cancels EventChannel subscription
}
```

No public state notifier — this service is fire-and-forget routing, not
observable state.

### Extraction screen pre-fill

`UrlExtractionScreen({ ..., String? initialUrl })` — when non-null, the
WebView loads it on first build and the input field is set; the existing
extract button remains the user-confirmation step.

`ImageExtractionScreen({ ..., File? initialImageFile })` — when non-null,
`_selectedImage` is initialised in `initState` so the preview and Extract
button render immediately. The user still taps Extract.

## Flows & state

### Cold start (process not running)

1. User picks RecipAI in the system share sheet.
2. Android constructs `MainActivity` with the `ACTION_SEND` intent.
3. `MainActivity.onCreate` → `ShareIntentBridge.stageInitialShare(intent)`
   extracts text or copies image bytes to cache, stores the payload map in a
   bridge-internal field.
4. Flutter engine boots; `main.dart` runs DI, builds router, calls
   `setupShareIntent`. The router redirects to `/login` if unauthenticated, or
   `/` otherwise. Either way, `MainScreen` mounts.
5. Post-first-frame callback in `main.dart` (or attached to `MainScreen`'s
   `initState`) calls `shareIntentService.consumeInitialShare()`.
6. The service `await`s the channel call, classifies the payload, and:
   - **Authenticated + URL**: `router.goNamed(urlExtraction, extra: UrlPrefill(url))`.
   - **Authenticated + image**: `router.goNamed(imageExtraction, extra: ImagePrefill(file))`.
   - **Authenticated + non-URL text**: stay on main, show snackbar via the
     root `ScaffoldMessenger`.
   - **Unauthenticated**: drop the payload silently. (Auth redirect already
     placed the user on `/login`; per requirements, content is discarded
     across login.)

### Warm start (process running, foreground or background)

1. User picks RecipAI; Android calls `onNewIntent` on the live, `singleTop`
   `MainActivity`.
2. `ShareIntentBridge.handleNewIntent(intent)` builds the payload map and
   pushes it onto the `EventChannel` sink.
3. Dart subscriber receives it, runs the same classify-and-route logic as
   above. Because `goNamed` replaces the matched location, any current
   extraction screen is silently replaced — no confirmation prompt, matching
   the "discard is silent" requirement.

### State machine — image cache file lifecycle

- Created by Kotlin under `cacheDir/share_intent/`.
- Path handed to Dart via the channel.
- Dart reads it inside `ImageExtractionScreen` (passed to the existing
  `ImagePicker`-style `XFile` flow — it is already a file path, so we wrap as
  `XFile(path)`).
- Cleanup: `ShareIntentBridge` deletes any pre-existing files in
  `cacheDir/share_intent/` at the start of each `stageInitialShare` /
  `handleNewIntent` call. One-shot consume; older shares cannot accumulate.
  The OS cache-eviction policy handles the residual (file left behind if the
  app is killed mid-flow).

## Integration changes

**`mobile/android/app/src/main/AndroidManifest.xml`** — adds two intent
filters inside the existing `MainActivity` element. `ACTION_SEND` with
`text/plain` (no `data` element — manifest filters cannot inspect `EXTRA_TEXT`,
so URL-shape classification is done in Dart; see **Approach** above) and
`ACTION_SEND` with `image/*`. No `ACTION_SEND_MULTIPLE`,
satisfying the "single image" requirement.

**`mobile/android/app/src/main/kotlin/xyz/stasiak/recipai/MainActivity.kt`** —
overrides `configureFlutterEngine(FlutterEngine)` to register channels via
`ShareIntentBridge.attach(messenger, context)`. Overrides `onCreate(savedInstanceState)`
to call `bridge.stageInitialShare(intent)` before `super.onCreate`. Overrides
`onNewIntent(intent)` to call `setIntent(intent)` then
`bridge.handleNewIntent(intent)`.

**`mobile/lib/main.dart`** — create a top-level
`GlobalKey<ScaffoldMessengerState>`; pass it to `MaterialApp.router` as
`scaffoldMessengerKey` and to `setupShareIntent(router: appRouter,
scaffoldMessengerKey: ...)`. Add a
`WidgetsBinding.instance.addPostFrameCallback` (or wire from `MainScreen`'s
`initState`) that triggers `consumeInitialShare()` once.

**`mobile/lib/core/routes.dart`** — `urlExtraction` and `imageExtraction`
builders read `state.extra` as a typed `UrlPrefill` / `ImagePrefill` wrapper
(or `null`) and forward the value to the screen constructor.

**`mobile/lib/features/extraction/url_extraction_screen.dart`** — adds
`initialUrl`. Refactor `_loadUrl()` to extract the URL/search resolution and
`_controller.loadRequest` into a private `_loadUrlInternal(String input)`
that does *not* call `FocusScope.of(context).unfocus()`. The existing
`_loadUrl()` keeps the unfocus call (it runs in response to user input,
where dismissing the keyboard is correct). In `initState`, after
`_initializeWebView`, if `initialUrl != null` set
`_urlController.text = initialUrl` and call `_loadUrlInternal(initialUrl)`
directly — no focus to dismiss on a fresh route, and this avoids the
`FocusScope.of(context)` access during `initState`.

**`mobile/lib/features/extraction/image_extraction_screen.dart`** — adds
`initialImageFile`. In `initState` set `_selectedImage = XFile(initialImageFile.path)`
when non-null. `ExtractionRepository.extractRecipeFromImage` only reads
`XFile.path` and infers MIME via `lookupMimeType(path)`, so a hand-built
`XFile(path)` round-trips correctly.

**Backend extraction module** — no changes. The existing `/extract/text` and
`/extract/image` endpoints are sufficient: the URL flow ultimately calls
`extractFromText` with HTML scraped from the WebView (same as today's URL
extraction), and the image flow calls `extractFromImage` with an `XFile`
(same as today's gallery flow).

## Resolved questions

- **Q:** Do `/extract/text` and `/extract/image` need adjustments?
  **A:** No. The share flow reuses the existing extraction screens unchanged
  beyond pre-fill, so the same backend calls are made.
- **Q:** Cold-start mechanism in Flutter on Android?
  **A:** Native stages the payload in `onCreate`, Dart pulls it via a
  one-shot `MethodChannel("recipai/share").consumeInitialShare` after the
  first frame. Warm-start uses an `EventChannel`. No third-party plugin.
- **Q:** How to filter `text/plain` shares to URL-shaped text only?
  **A:** Dart-side classification, not manifest-side. Non-URL text drops the
  user on the main screen with a snackbar. See **Approach** for the rationale.
- **Q:** Payload DTO shape across the channel?
  **A:** Flat `Map<String, Object>` with `type` discriminator and either
  `text` or `imagePath`. See **Interface contracts**.
- **Q:** Where do image cache files live and when are they cleaned up?
  **A:** `cacheDir/share_intent/<timestamp>-<random>.<ext>`. The bridge wipes
  the directory at the start of each new share, so at most one file is live;
  OS cache eviction handles abandoned files.
- **Q:** Where is the `ShareIntentService` listener wired?
  **A:** Constructed in `main()` after the router is created and bound to it.
  `consumeInitialShare()` is fired from a post-first-frame callback so the
  router is mounted before any `goNamed` call.
- **Q:** How are mid-extraction / mid-edit screens silently replaced?
  **A:** `context.goNamed` (used via the held `GoRouter`) replaces the matched
  location. No confirmation, matching the silent-discard requirement.
- **Q:** Confirm `MainActivity` is `singleTop`?
  **A:** Yes — already declared in the manifest.
- **Q:** Snackbar copy and surface for non-URL text?
  **A:** "RecipAI can only extract recipes from URLs or images." Surfaced via
  the root `ScaffoldMessenger` after `goNamed(main)` (or while already on
  main).

## Assumptions to verify

- **Assumption:** Adding `<intent-filter>` blocks for `ACTION_SEND` to
  `MainActivity` does not perturb the existing launcher entry.
  **Why it matters:** A misconfigured filter could cause the launcher icon
  to open into an unexpected state, cause `onNewIntent` to fire on regular
  app resume, or — most likely — make the launcher icon disappear if the
  `LAUNCHER` filter is accidentally merged into the `SEND` filter (each
  `<intent-filter>` must be a separate element).
  **How to verify:** after wiring the manifest, on a physical device:
  (1) confirm the launcher icon still launches the app to the main screen
  with no share payload; (2) put the app in the background and resume from
  the recents list — `onNewIntent` should not fire (verify with a Logcat
  print in `ShareIntentBridge.handleNewIntent`); (3) share a URL from
  Chrome — RecipAI should be in the share sheet and `onNewIntent` should
  fire on the existing instance, not start a new task. Run `adb shell dumpsys
  package xyz.stasiak.recipai | grep -A2 MainActivity` to inspect the merged
  filter list as Android sees it.

## Out of scope (design-level)

- Persisting the shared payload across login (requirements anti-requirement,
  reaffirmed).
- A confirmation prompt before discarding mid-edit content (anti-requirement).
- Sharing multiple images, PDFs, arbitrary files — manifest filters
  intentionally exclude them.
- iOS share extension — requires a separate native target and is deferred.
- Automatic extraction on share arrival — requirements explicitly want user
  confirmation.
