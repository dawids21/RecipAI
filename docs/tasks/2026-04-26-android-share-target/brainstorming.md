# Android share target for recipe extraction — solution brainstorming

**Date:** 2026-04-26
**Status:** brainstorming

## Summary

Register RecipAI as an Android share target so users can share a URL or an image from any app into the existing extraction screens. This doc explores four distinct approaches for wiring the Android intent into the Flutter app and recommends one.

## Approaches considered

### Approach 1: `receive_sharing_intent` plugin + router-level dispatcher

**Sketch.** Pull in the community `receive_sharing_intent` Flutter plugin. It registers the manifest intent filters per its docs and exposes a unified Dart stream covering both cold start (`getInitialMedia`) and warm start (`getMediaStream`). A small `ShareIntentService` listens to the stream, classifies the payload (URL vs. image), checks `AuthService.isAuthenticated`, and uses `go_router`'s `context.goNamed` to navigate into `urlExtraction` / `imageExtraction` with the payload passed via `state.extra`. The two extraction screens grow optional `initialUrl` / `initialImage` constructor params.

**Trade-offs.**
- Almost zero native code; cold/warm start handled uniformly by the plugin.
- Adds a community-maintained third-party dependency for a foundational app capability.
- Plugin sometimes requires a custom Activity subclass on Android, which couples our `MainActivity` to the plugin's expectations.
- Manifest filter shape is partly dictated by the plugin's recipe rather than ours.

**When it's the right choice.** When shipping fast matters more than dependency surface, and the plugin's lifecycle assumptions happen to fit `singleTop` MainActivity + go_router redirects cleanly.

**Main risk.** Plugin lifecycle timing doesn't match our auth-redirect flow, leading to subtle cold-start bugs we'd then have to work around inside someone else's abstraction.

### Approach 2: Hand-rolled `MethodChannel` + native intent handling in `MainActivity`

**Sketch.** Add intent filters to `mobile/android/app/src/main/AndroidManifest.xml` ourselves — `ACTION_SEND` for `text/plain` and `image/*`, no `SEND_MULTIPLE`. In `MainActivity.kt`, override `onCreate` and `onNewIntent` to read `Intent.EXTRA_TEXT` / `EXTRA_STREAM`, copying image bytes to a controlled cache file. Dart side: a `ShareIntentService` calls a one-shot `getInitialShare()` MethodChannel at startup (cold start) and listens to an `EventChannel` for subsequent shares (warm start). The service classifies the payload (URL / non-URL text / image), checks `AuthService.isAuthenticated`, and uses go_router's `context.goNamed` to navigate. Both extraction screens grow optional `initialUrl` / `initialImageFile` constructor params and pre-fill on first build.

**Trade-offs.**
- Full control over manifest, intent-filter mime types, and the native lifecycle.
- No third-party dependency for a foundational capability.
- Clean separation: native intake → channel → Dart routing.
- More Kotlin to write and own; we are responsible for the cold-vs-warm-start split.

**When it's the right choice.** When dependency hygiene matters and we expect more share-target work in the future, so a foundation we own pays off.

**Main risk.** Cold-start race — the Flutter engine isn't subscribed when `onCreate` fires. Mitigated by the pull-on-startup pattern (Dart asks when it's ready), but easy to get subtly wrong on first implementation.

### Approach 3: Custom Android URI scheme + intent that opens an in-app deep link

**Sketch.** Skip `ACTION_SEND` entirely. Declare a `recipai://share?...` deep-link intent filter and accept `ACTION_VIEW` for `text/plain` URLs only. The share path becomes: native intent → deep link URL → go_router route → extraction screen.

**Trade-offs.**
- Reuses go_router's existing deep-link plumbing.
- Awkward for image bytes — would need a content URI handed off and read later.
- `ACTION_VIEW` for `text/plain` is non-standard; RecipAI would not actually appear in the share sheet for most apps.

**When it's the right choice.** Effectively never for this task — it solves a different problem (deep linking) than the requirement (appearing in the share sheet).

**Main risk.** Doesn't satisfy the core requirement. Listed for completeness; ruled out.

### Approach 4: Native-only catcher Activity that bounces into a deep link

**Sketch.** Add a separate `ShareReceiverActivity` in Kotlin whose only job is to receive `ACTION_SEND`, classify, persist the payload (text into a SharedPreferences slot, image into app cache), then `startActivity` to `MainActivity` with a deep-link URI like `recipai://share/url`. Flutter sees only a normal deep link; on launch the Dart side reads the persisted payload from a MethodChannel once and clears it.

**Trade-offs.**
- Cold and warm start become identical from Flutter's perspective — both are deep links.
- `MainActivity` stays untouched.
- Extra Activity + on-disk payload slot + one-shot read channel — more moving parts than Approach 2.
- Persistence layer introduces its own bug surface (stale payloads, races between two quick shares).

**When it's the right choice.** When we strongly want share intake to feel like just another deep link inside the app, perhaps because we already had heavy deep-link infrastructure.

**Main risk.** The persisted-payload layer becomes a source of subtle bugs (stale values surviving across shares) for no real gain over Approach 2.

## At a glance

| Approach | Native code | 3rd-party dep | Cold-start handling | Control over share-sheet filter |
|----------|-------------|---------------|---------------------|---------------------------------|
| 1. `receive_sharing_intent` plugin | none | yes (community) | plugin handles | constrained by plugin |
| 2. MethodChannel + manifest | moderate (Kotlin) | none | we implement (pull on start + event stream) | full |
| 3. Custom URI scheme | low | none | via go_router deep links | poor — wrong tool for share sheet |
| 4. Bouncer Activity + deep link | moderate (Kotlin) | none | uniform via deep links | full |

## Recommendation

**Chosen: Approach 2.** No third-party dependency for what will be a foundational capability of the app, full control over the manifest filters, and a clean split between native intake (`MainActivity` `onCreate` / `onNewIntent`) and Flutter routing (go_router). The cold-vs-warm-start asymmetry maps naturally onto MethodChannel-pull (cold) + EventChannel-push (warm): cold start is a one-shot question Dart asks when it's ready, warm start is a stream of events. Filtering URL-shaped `text/plain` shares is accepted as a Dart-side concern — we will appear in the share sheet for any text share, and on non-URL text we drop the user on the main screen with a snackbar explaining RecipAI only handles URLs.

What this gives up vs. Approach 1: a small amount of upfront Kotlin work and ownership of the cold-start replay logic. What it gives up vs. Approach 4: nothing material — Approach 4 is just Approach 2 with extra moving parts.

## Questions for design

- Exact shape of the payload DTO across the MethodChannel / EventChannel (text vs. image-path discriminator).
- Where image cache files live and when they're cleaned up (one-shot consume? on next share? on app close?).
- Where in the widget tree the `ShareIntentService` listener is wired so navigation has a valid `BuildContext` / `GoRouter` available.
- How mid-extraction or mid-edit screens are silently replaced — go_router stack reset vs. push, and how to guarantee discard is silent per requirements.
- Confirm `MainActivity` stays `singleTop` so `onNewIntent` fires for warm-start shares (it currently is).
- Snackbar copy and surface for the non-URL `text/plain` case.
