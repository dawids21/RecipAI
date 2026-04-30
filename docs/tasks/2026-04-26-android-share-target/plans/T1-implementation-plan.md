# T1: Text share target — plumbing, URL pre-fill, non-URL snackbar — Implementation Plan

**Date:** 2026-04-27
**Status:** draft

## Required reading

**Docs & standards** (from `docs/INDEX.md`)

- `docs/mobile/standards/architecture.md` — `ShareIntentService` follows the Service-layer rules; the new files live flat inside `features/extraction/` (per feedback — no separate `share_intent` feature directory).
- `docs/mobile/standards/dependency-injection.md` — `setupShareIntent({...})` follows the per-feature setup-function convention; external deps (router, scaffold key) come in as parameters.
- `docs/mobile/standards/navigation.md` — `AppRoute` enum and `goNamed`; how routes pull services via builder closures (the new typed `extra` reads mirror this).
- `docs/mobile/standards/state-management.md` — even though `ShareIntentService` exposes no notifier, the `dispose()` discipline applies to the `EventChannel` subscription.
- `docs/mobile/modules/extraction/codebase_structure.md` and `docs/mobile/modules/extraction/ui.md` — the module the new files live in and the screens being extended.

**Design & ADRs**

- `design.md` > **Approach** — pull/push channel split and the URL-filter-in-Dart rationale.
- `design.md` > **Module & component boundaries** — full file list. The component layout assumes `features/share_intent/`; per feedback we collapse those files into `features/extraction/` instead.
- `design.md` > **Interface contracts** — channel names, payload map shape, `ShareIntentService` constructor, screen pre-fill parameters.
- `design.md` > **Flows & state** — cold and warm start sequencing; auth-redirect interaction with `consumeInitialShare()`.
- `design.md` > **Integration changes** — `_loadUrlInternal` refactor and `MainActivity` overrides.
- `design.md` > **Assumptions to verify** — launcher / resume-from-recents / share-sheet check, re-run before merge.

**Code to mirror**

- `mobile/lib/features/extraction/extraction_setup.dart` — shape of a per-feature `setup*()` function and `getIt.registerSingleton` usage. `setupShareIntent` mirrors it but accepts `router` and `scaffoldMessengerKey` as required parameters.
- `mobile/lib/features/auth/auth_service.dart` — service-class lifecycle: `late final` stream subscription cancelled in `dispose()`. The `EventChannel` subscription follows the same pattern.
- `mobile/lib/core/routes.dart` (around the `recipeCreate` builder, lines 156–164) — the typed `state.extra` cast pattern used for `InitialRecipeFormData`. New `UrlPrefill` / `ImagePrefill` reads mirror it.
- `mobile/lib/features/extraction/url_extraction_screen.dart` (`_loadUrl`, lines 104–132) — split into `_loadUrl` (user input, keeps `unfocus`) and `_loadUrlInternal(String input)` (no `FocusScope.of(context)`).
- `mobile/lib/features/recipe/initial_recipe_form_data.dart` — minimal-DTO style for typed route `extra` payloads; `UrlPrefill` / `ImagePrefill` follow it.
- `mobile/lib/shared/extensions.dart` — extension-on-built-in-type pattern (e.g. `DateTimeLocalizations`, `IsoDateFormat`). The new `isUrl` getter / method goes in here as an extension on `String`.

## File inventory

**Native (Android)**

- **MODIFY** `mobile/android/app/src/main/AndroidManifest.xml` — add a sibling `<intent-filter>` for `ACTION_SEND` + `text/plain` under `MainActivity`; leave space for the T2 image filter as a separate sibling.
- **MODIFY** `mobile/android/app/src/main/kotlin/xyz/stasiak/recipai/MainActivity.kt` — override `configureFlutterEngine`, `onCreate`, `onNewIntent`; delegate to `ShareIntentBridge`.
- **CREATE** `mobile/android/app/src/main/kotlin/xyz/stasiak/recipai/ShareIntentBridge.kt` — Kotlin object: `attach(messenger, context)`, `stageInitialShare(intent)`, `handleNewIntent(intent)`, internal payload-map field, text-extraction helper, `EventChannel.StreamHandler`, `MethodChannel.MethodCallHandler`. Image branch stubbed with a TODO referencing T2.

**Dart (Flutter)**

All new Dart files live under `mobile/lib/features/extraction/` (flat, no sub-folder), per feedback.

- **CREATE** `mobile/lib/features/extraction/share_payload.dart` — sealed `SharePayload` with `UrlSharePayload`, `NonUrlTextSharePayload`, `ImageSharePayload`; factory `SharePayload? fromMap(Map<String, Object> map)` that returns `null` for `type == "image"` in T1 (T2 wires the construction).
- **CREATE** `mobile/lib/features/extraction/share_intent_service.dart` — service class with `consumeInitialShare()`, `dispose()`, internal `_classifyAndRoute(SharePayload)`. URL classification uses the new `String.isUrl` extension.
- **CREATE** `mobile/lib/features/extraction/share_intent_setup.dart` — `void setupShareIntent({required GoRouter router, required AuthService authService, required GlobalKey<ScaffoldMessengerState> scaffoldMessengerKey})`.
- **CREATE** `mobile/lib/features/extraction/share_route_extras.dart` — typed wrappers `UrlPrefill { final String url; }` and `ImagePrefill { final File file; }`. `ImagePrefill` lands now even though it has no producer until T2 — keeps the routing change atomic.
- **MODIFY** `mobile/lib/shared/extensions.dart` — add `extension UrlString on String { bool get isUrl { ... } }` containing the regex/`Uri.parse` logic currently inlined in `_UrlExtractionScreenState._isUrl`.
- **MODIFY** `mobile/lib/main.dart` — add `final scaffoldMessengerKey = GlobalKey<ScaffoldMessengerState>();`; pass it to `MaterialApp.router(scaffoldMessengerKey: ...)`; call `setupShareIntent(...)` after `createAppRouter()`; schedule `WidgetsBinding.instance.addPostFrameCallback((_) => getIt<ShareIntentService>().consumeInitialShare())` from `_RecipAIAppState.initState`; call `getIt<ShareIntentService>().dispose()` next to `AuthService.dispose()`.
- **MODIFY** `mobile/lib/core/routes.dart` — `urlExtraction` builder reads `state.extra as UrlPrefill?` and forwards `prefill?.url` as `initialUrl`; `imageExtraction` builder reads `state.extra as ImagePrefill?` and forwards `prefill?.file` as `initialImageFile` (the receiving screen ignores it until T2).
- **MODIFY** `mobile/lib/features/extraction/url_extraction_screen.dart` — replace `_isUrl(...)` calls with `input.isUrl`; remove the local `_isUrl` helper; add `final String? initialUrl` constructor parameter; refactor `_loadUrl()` into `_loadUrl()` + `_loadUrlInternal(String input)` (the latter does not call `FocusScope.of(context).unfocus()`); call `_loadUrlInternal(initialUrl!)` and set `_urlController.text = initialUrl!` in `initState` after `_initializeWebView()` when `initialUrl != null`.
- **MODIFY** `mobile/lib/features/extraction/image_extraction_screen.dart` — add `final File? initialImageFile;` constructor parameter (plumbing only; body changes deferred to T2).

**Tests**

- **CREATE** `mobile/test/features/extraction/url_extraction_screen_initial_url_test.dart` — pumps `UrlExtractionScreen(initialUrl: 'https://example.com/recipe')` with a mocked `ExtractionRepository` and a real `ExtractionService` registered via `setupExtraction()` (per the widget-testing standard: mock repositories, not services), asserts the URL field contains the URL on first frame and no `FocusScope` exception is logged.

(Per feedback: no `share_payload_test.dart` and no `share_intent_service_test.dart`.)

## Step-by-step plan

Each step ends with `flutter analyze` clean and existing tests green.

1. **Add `String.isUrl` extension** — move the regex + `Uri.parse` logic from `_UrlExtractionScreenState._isUrl` into `extension UrlString on String { bool get isUrl }` in `mobile/lib/shared/extensions.dart`. Update `url_extraction_screen.dart` to call `input.isUrl` instead of `_isUrl(input)`; delete the local helper.
   - Files: `mobile/lib/shared/extensions.dart`, `mobile/lib/features/extraction/url_extraction_screen.dart`.
   - Verify: `flutter analyze` clean; `flutter test` still passes (URL extraction screen tests, if any, untouched).

2. **Scaffold the Dart payload + route extras** — create `share_payload.dart` (full sealed hierarchy including `ImageSharePayload`) and `share_route_extras.dart`. `SharePayload.fromMap` accepts `type == "image"` but returns `null` until T2.
   - Files: `mobile/lib/features/extraction/share_payload.dart`, `mobile/lib/features/extraction/share_route_extras.dart`.
   - Verify: `flutter analyze` clean.

3. **Add `ShareIntentService`** — constructor takes `AuthService`, `GoRouter`, `GlobalKey<ScaffoldMessengerState>`, plus `MethodChannel?` and `EventChannel?` overrides defaulting to `MethodChannel("recipai/share")` and `EventChannel("recipai/share/events")`. Store `_eventSubscription`, implement `dispose()`. `consumeInitialShare()` calls the method channel and routes the result. `_classifyAndRoute(SharePayload)`:
   - URL → `router.goNamed(AppRoute.urlExtraction.name, extra: UrlPrefill(url))`.
   - Non-URL text → `router.goNamed(AppRoute.main.name)` then `scaffoldMessengerKey.currentState?.showSnackBar(...)` with copy *"RecipAI can only extract recipes from URLs or images."*.
   - Image → `router.goNamed(AppRoute.imageExtraction.name, extra: ImagePrefill(file))` (unreachable in T1 because `fromMap` returns `null`).
   - Unauthenticated (any) → drop silently. Read `authService.isAuthenticated.value` at call time.
   - Files: `mobile/lib/features/extraction/share_intent_service.dart`.
   - Verify: `flutter analyze` clean.

4. **Add `setupShareIntent` and route-extras consumption** — write `share_intent_setup.dart` registering the service as a singleton with `dispose`. Update `routes.dart` builders to read `state.extra as UrlPrefill?` / `state.extra as ImagePrefill?` and pass through to the screens.
   - Files: `mobile/lib/features/extraction/share_intent_setup.dart`, `mobile/lib/core/routes.dart`.
   - Verify: `flutter analyze` clean. Existing tests pass: `flutter test`.

5. **Refactor `_loadUrl` and add `initialUrl` pre-fill** — split `_loadUrl()` into `_loadUrl()` (user-input path, keeps `unfocus`) and `_loadUrlInternal(String input)` (URL/search resolution + `_controller.loadRequest`). Add `final String? initialUrl;` to the widget. In `initState`, after `_initializeWebView()` and **before** adding the `_urlController` listener: if `initialUrl != null`, set `_urlController.text = initialUrl!` and `_isCurrentInputUrl = initialUrl!.isUrl` directly (so the first build renders the correct button label without a listener-triggered `setState`). Then add the listener as usual. After the listener, if `initialUrl != null`, call `_loadUrlInternal(initialUrl!)`.
   - Files: `mobile/lib/features/extraction/url_extraction_screen.dart`.
   - Verify: `flutter analyze` clean; `flutter test` passes (existing tests only — the new test for this change is written in step 7).

6. **Add the `initialImageFile` constructor parameter (no body change)** — purely a constructor addition so T2 doesn't have to also touch the route builder.
   - Files: `mobile/lib/features/extraction/image_extraction_screen.dart`.
   - Verify: `flutter analyze` clean.

7. **Add the `initialUrl` widget test** — write `url_extraction_screen_initial_url_test.dart`. Follow the widget-testing standard: mock `ExtractionRepository` (and `AuthRepository` for `setupAuth`), register both via their respective `setup*()` functions so a real `ExtractionService` is used. Build a minimal single-route `GoRouter` with the `urlExtraction` route pointing to `UrlExtractionScreen(initialUrl: ..., extractionService: getIt<ExtractionService>())`.
   - Files: `mobile/test/features/extraction/url_extraction_screen_initial_url_test.dart`, `mobile/test/support/mocks.dart` (add `MockExtractionRepository` if not already present).
   - Verify: `flutter test` — green.

8. **Wire it up in `main.dart`** — declare `final scaffoldMessengerKey = GlobalKey<ScaffoldMessengerState>();`, pass it to `MaterialApp.router`, call `setupShareIntent(router: appRouter, authService: getIt<AuthService>(), scaffoldMessengerKey: scaffoldMessengerKey)` after `createAppRouter()`. In `_RecipAIAppState.initState`, schedule `WidgetsBinding.instance.addPostFrameCallback((_) => getIt<ShareIntentService>().consumeInitialShare())`. Call `getIt<ShareIntentService>().dispose()` in `dispose()`.
   - Files: `mobile/lib/main.dart`.
   - Verify: `flutter analyze`; `flutter run` boots cleanly (no share intent yet — channel returns `null`).

9. **Create the Kotlin `ShareIntentBridge`** — `attach(messenger: BinaryMessenger, context: Context)` sets up `MethodChannel("recipai/share")` (handles `consumeInitialShare` returning and clearing the staged map) and `EventChannel("recipai/share/events")` (stores the `EventSink`). `stageInitialShare(intent)` and `handleNewIntent(intent)` extract `EXTRA_TEXT` into `mapOf("type" to "text", "text" to text)`. Add a `wipeShareCacheDir(context)` helper called at the start of each share extraction (no-op for T1's text-only path; in place for T2). Image branch is a TODO referencing T2.
   - Files: `mobile/android/app/src/main/kotlin/xyz/stasiak/recipai/ShareIntentBridge.kt`.
   - Verify: `./gradlew :app:assembleDebug` (from `mobile/android`) succeeds.

10. **Wire `MainActivity` overrides** — `MainActivity : FlutterActivity()` gains:
    - `override fun configureFlutterEngine(flutterEngine: FlutterEngine) { super.configureFlutterEngine(flutterEngine); ShareIntentBridge.attach(flutterEngine.dartExecutor.binaryMessenger, applicationContext) }`
    - `override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); ShareIntentBridge.stageInitialShare(intent) }` — `super.onCreate` first (conventional `FlutterActivity` order); staging writes to a static field and is consumed post-first-frame, so order is safe. See **Risks surfaced during planning**.
    - `override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); ShareIntentBridge.handleNewIntent(intent) }`
    - Files: `mobile/android/app/src/main/kotlin/xyz/stasiak/recipai/MainActivity.kt`.
    - Verify: app builds and runs.

11. **Add the manifest `<intent-filter>`** — sibling of the existing LAUNCHER filter inside `<activity android:name=".MainActivity">`:
    ```xml
    <intent-filter>
        <action android:name="android.intent.action.SEND"/>
        <category android:name="android.intent.category.DEFAULT"/>
        <data android:mimeType="text/plain"/>
    </intent-filter>
    ```
    - Files: `mobile/android/app/src/main/AndroidManifest.xml`.
    - Verify: `adb shell dumpsys package xyz.stasiak.recipai | grep -A2 MainActivity` shows two distinct filters (LAUNCHER + SEND).

12. **End-to-end device verification** — run all six `tasks.md` > T1 "How to verify" steps on a physical Android device. Capture Logcat to confirm `onNewIntent` does *not* fire on launcher tap or resume from recents.

## Test plan

**Unit tests**

- _N/A — `share_payload_test.dart` and `share_intent_service_test.dart` excluded per feedback._

**Dart widget tests**

- `url_extraction_screen_initial_url_test.dart` — mocks `ExtractionRepository` (and `AuthRepository`); real `ExtractionService` via `setupExtraction()`.
  - With `initialUrl: 'https://example.com/recipe'`, the URL field contains that URL after first frame **and** the button label is "Load" (not "Search"), confirming `_isCurrentInputUrl` is correct on the first build.
  - With `initialUrl: null`, the URL field is empty and the button label is "Search" (regression guard against accidental `String? → String` mistakes).
  - With `initialUrl` set, no exception is thrown during `initState` (regression guard for `FocusScope.of(context)` in `_loadUrlInternal`).

**Kotlin tests**

- _N/A — the bridge is thin glue; the project has no JVM unit-test infrastructure for the Android module. Kotlin behaviour is covered by device-level manual verification._

**Manual verification (physical Android device)**

- Run all six `tasks.md` > T1 "How to verify" steps verbatim.
- `adb shell dumpsys package xyz.stasiak.recipai | grep -A2 MainActivity` — confirm filter merge.
- `adb logcat | grep ShareIntentBridge` — confirm `onNewIntent` does not fire on launcher tap or resume from recents.
- Re-confirm copy: snackbar reads *"RecipAI can only extract recipes from URLs or images."*.

## Verification checklist

- [ ] `flutter analyze` passes from `mobile/`.
- [ ] `dart format --set-exit-if-changed lib/ test/` passes.
- [ ] `flutter test` passes.
- [ ] `./gradlew :app:assembleDebug` (from `mobile/android`) succeeds.
- [ ] All six `tasks.md` > T1 "How to verify" steps succeed on a physical Android device.
- [ ] `design.md` > Assumptions to verify — re-run launcher / resume / share-sheet checks; record results in the PR description.
- [ ] No `onNewIntent` Logcat line on resume-from-recents.
- [ ] Manifest is two sibling `<intent-filter>` blocks (LAUNCHER + SEND), not merged.
- [ ] No new analyzer warnings.

## Risks surfaced during planning

- **Risk:** `design.md` says `onCreate` should call `bridge.stageInitialShare(intent)` *before* `super.onCreate`. Conventional `FlutterActivity` subclasses call `super.onCreate(savedInstanceState)` first.
  **Why it matters:** Following the design literally could cause subtle engine-init issues; deviating without acknowledgment leaves a discrepancy between code and design.
  **Mitigation:** Implement with `super.onCreate` first — `stageInitialShare` only writes to a static field and `consumeInitialShare` is post-first-frame anyway. Note the deviation in the PR; update `design.md` if device verification confirms this is correct.

- **Risk:** Moving `_isUrl` to a `String.isUrl` extension changes a screen with no current widget tests. Behaviour must remain identical for the existing user-input path *and* match the share-side classification.
  **Why it matters:** Drift would create UX where typing a string into the URL field accepts it but sharing the same string drops to a snackbar (or vice versa).
  **Mitigation:** Keep the regex bytes identical when moving — pure relocation, no logic edits. Add a focused widget assertion in step 7 that the screen still treats `https://example.com` as a URL after the refactor.

- **Risk:** `consumeInitialShare()` runs post-first-frame but `go_router`'s redirect to `/login` may not have settled yet on cold start. An early `goNamed(urlExtraction)` could be overridden by the redirect (already flagged in `tasks.md`), or could itself override the redirect.
  **Why it matters:** Users could land on the extraction screen unauthenticated, violating the auth-gate requirement.
  **Mitigation:** Read `authService.isAuthenticated.value` inside `_classifyAndRoute` at call time and drop the payload if false. Verify both logged-in and logged-out cold-start scenarios in step 12.

- **Risk:** The post-first-frame trigger lives in `_RecipAIAppState`. The Kotlin bridge's `consumeInitialShare` is one-shot; a second call returns `null` and silently drops.
  **Why it matters:** A second call site (e.g. someone later wiring it into `MainScreen.initState`) would race and lose shares.
  **Mitigation:** Document in `share_intent_service.dart` that `consumeInitialShare()` must be called from exactly one site. Add an assertion in the service that it is called at most once.
