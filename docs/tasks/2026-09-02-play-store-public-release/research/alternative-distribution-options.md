# Alternatives to a Google Play production release for RecipAI

**Date:** 2026-09-02
**Source:** internet (Play Console Help, developer.android.com, Android Developers Blog, Firebase docs,
pub.dev, store operator docs, secondary guides) plus inspection of the RecipAI repository
**Companion:** [`play-store-publishing-requirements.md`](play-store-publishing-requirements.md) — why the
production track is blocked (12 testers × 14 days, identity verification, App content, store listing)

## Summary

The Play production track is not the only way to make RecipAI reachable by real people, but it is the only
one that comes with discovery. Everything else trades "people can find it" for "people can reach it if you
send them a link". On the merits the cleanest of those is a **Flutter web build served as an installable
PWA from the VPS that already runs the backend** — no store, no gatekeeper, no signing, a URL anyone can
open. Its cost is real: four of RecipAI's plugins do not work on web and the URL-extraction feature has to
move to the backend, which is why it has been set aside for now.

The runner-up — and the cheapest thing that works today — is a **signed APK hosted on your own site**.
Android developer verification is already handled: the developer account is verified and the package
`xyz.stasiak.recipai` shows as **Registered** in Play Console, so the 2026/2027 rollout imposes no device
cap and no future block. The only remaining care is signing the distributed APK with a key registered
against that package.

Third-party Android stores are mostly a dead end here: Amazon's Android store closed in August 2025,
F-Droid and IzzyOnDroid require an OSI/FSF-licensed public repository (RecipAI's is private), and Samsung
and Huawei impose identity verification comparable to Play's without delivering Play's audience.

## Key findings

- **Open testing is not an escape hatch.** Play Console states plainly that "open testing becomes available
  after you gain production access". Production *and* open testing are both gated behind the same
  12-tester/14-day closed test. There are no exemptions listed for personal accounts created after
  13 November 2023.
- **Internal testing (where RecipAI is now) already allows 100 testers** by email address, with no Data
  safety form or store listing. That is a lot of head-room for a portfolio app — it is just not public.
- **Firebase App Distribution is free and roomier**: 500 testers per project, 200 per group, 1,000 releases
  per app, releases expire after 150 days. It works with both APK and AAB. Still invite-based, not public.
- **Flutter web is a first-class target**, but RecipAI uses four plugins that do not port cleanly:
  `webview_flutter` (Android/iOS/macOS only), `sqflite` (no web engine), `path_provider` (no web), and
  `google_sign_in` 7.x (web needs a different sign-in widget and grants no scopes from `signIn`).
- **Android developer verification is already satisfied for RecipAI.** The developer account is verified and
  `xyz.stasiak.recipai` is Registered with one signing key (last updated 5 March 2026). Enforcement begins
  30 September 2026 in Brazil, Indonesia, Singapore and Thailand and reaches Poland in the 2027+ wave, so
  nothing is blocked today either. The free **limited distribution account** — no government ID, capped at
  20 devices — is therefore unnecessary; that cap applies to developers who skip identity verification.
- **Registration binds a package name to SHA-256 signing-key fingerprints**, and multiple keys can be
  registered per package. Play registers apps automatically, and for a Play App Signing app what it
  registers is the *app signing key* Google holds — not the local upload key in
  `mobile/android/upload_keystore.jks`.
- **Amazon Appstore for Android shut down on 20 August 2025**; new Android app submissions are no longer
  accepted. It survives only on Fire TV and Fire tablets.
- **F-Droid and IzzyOnDroid require a free/libre licence and a public repository.** RecipAI's GitHub repo is
  private and has no licence, so both are closed unless you open-source the app.

## Options in detail

### 1. Flutter web + PWA on your own VPS

**What it is.** `flutter build web`, served from a subdomain (e.g. `app.recipai.stasiak.xyz`) next to the
existing backend. Flutter's web template ships a `manifest.json` and a service worker, so Chrome on Android
offers "Add to home screen" and launches it in a standalone window with no address bar. Anyone with the
link is a user; there is no invite list, no review, no identity check, and no store deadline hanging over it.

**What it costs.** This is a real port, not a build-flag flip. The repository currently has no `web/`
directory, so the platform has never been generated. Concretely:

| Blocker | Where | Fix |
| --- | --- | --- |
| `webview_flutter` — Android/iOS/macOS only | `features/extraction/url_extraction_screen.dart`, `web_recipe_extractor.dart` | The URL-extraction flow loads a recipe page in a WebView and scrapes its HTML. Browsers block reading a cross-origin iframe, so this cannot be reproduced client-side — the page fetch has to move to a backend endpoint that takes a URL and returns the extracted recipe. |
| `sqflite` — no web engine | `features/shopping_list/*`, `main.dart` | `sqflite_common_ffi_web` 1.1.1 (Jan 2026) runs sqlite3 wasm over IndexedDB in a shared worker, but is explicitly experimental — slow, incompletely tested, `deleteDatabase` unsupported on wasm. The offline outbox is the only consumer, so a web-specific store is also viable. |
| `path_provider` — no web | `core/logging/app_log_sink.dart`, shopping-list DB factory | File-backed logging has no web equivalent; guard it behind `kIsWeb` or swap the sink. |
| `google_sign_in` 7.x on web | auth setup | Web uses `google_sign_in_web`'s `renderButton` widget rather than a programmatic `signIn()`, and on web no scopes are granted by `signIn`, `silentSignIn` or `renderButton`. The sign-in screen needs a web branch, and the domain must be added to Firebase Auth's authorized domains. |
| CORS | backend + S3 | The backend and the S3 presigned-URL image flow must allow the web origin. |

`image_picker` works through the endorsed `image_picker_for_web`, and `wakelock_plus`, `shared_preferences`,
`http`, `go_router` and `firebase_auth` all support web.

**Verdict.** The only option on this list that is genuinely public, permanently, with no external gatekeeper.
Budget the port at the same order of magnitude as a medium feature, dominated by the backend URL-extraction
endpoint.

### 2. Signed APK from your own website (or GitHub Releases) — *chosen route*

**What it is.** Publish the release APK — not the AAB — at a stable URL and link it from a landing page.
Nothing is required of you: no store account, no review, no privacy policy, no Data safety form. Power users
can subscribe to a GitHub Releases page in **Obtainium**, which watches release pages and notifies or
auto-installs updates; it is the standard tool for exactly this distribution model.

**Developer verification — already handled.** From 30 September 2026 in Brazil, Indonesia, Singapore and
Thailand (2027+ for Poland), apps must be registered by a verified developer to install on certified
devices; otherwise they install only via ADB or a deliberate "advanced flow" for power users. RecipAI
clears this: the account is verified and `xyz.stasiak.recipai` is Registered with one key. No device cap
applies and no limited distribution account is needed.

**Which key signs the distributed APK.** Registration links the package name to SHA-256 certificate
fingerprints, so the APK people install must be signed with a registered key. RecipAI has two candidates:

- the **upload key** in `mobile/android/upload_keystore.jks` (alias `upload`), which
  `mobile/android/app/build.gradle.kts` uses for local release builds — SHA-256
  `0C:1B:9E:EE:1F:F7:1D:F0:…:66:26`;
- the **Play app signing key** Google holds, because the app uses Play App Signing — SHA-256
  `45:A8:57:FD:6A:64:69:65:…:89:9D`, reported as `certificateSha256Hash` by `generatedapks.list`.

Play auto-registers the app signing key, so the single registered key is most likely the Google-held
`45:A8:…` one rather than the upload key. Two ways to stay consistent:

1. **Distribute the Play-generated APK** — Play Console → App bundle explorer → release → Downloads →
   *Signed, universal APK*, or the same artefact via the `generatedapks` API (see below). It is signed with
   the app signing key and carries the same signature as the Play build, so a user who later installs from
   Play hits no signature conflict.
2. **Register the upload key as an additional key** — package names page → *Add key*, which requires an APK
   signed with that key carrying an `adi-registration.properties` file. Local `flutter build apk --release`
   output is then distributable directly.

A debug-signed build is registered to nothing and must not be handed out.

**Fetching the APK without the console.** The Play Developer API exposes the same App bundle explorer
assets: `generatedapks.list(packageName, versionCode)` returns a `generatedUniversalApk.downloadId`, and
`generatedapks.download(packageName, versionCode, downloadId)` streams the APK. The existing
`scripts/play-service-account.json` and `scripts/.venv` used by `scripts/play_publish.py` already have the
access needed — verified against version code 23, which reports 171 split APKs and one universal APK. The
other assets in App bundle explorer are not distributable: *Original file* is the uploaded AAB signed with
the upload key, *Archived APK* is the ~59 KB stub Play generates for app archiving, and the ReTrace mapping
and native debug symbols are crash-deobfuscation artefacts.

**Verdict.** Works today, costs nothing, needs no web port, and is the natural companion to a landing page or
public repo. The only open item is confirming which fingerprint the registered key holds.

### 3. Firebase App Distribution

Already available — the project uses Firebase. Testers are invited by email or link, install through a
tester app or web flow, and you push builds from CI. Free, with 500 testers per project, 200 per group,
1,000 releases per app, and releases expiring after 150 days. Works with APK and AAB, and integrates with
Play internal app sharing.

**Verdict.** The best *invite-based* channel: much roomier than Play internal testing and outside every Play
release gate. Still not public — someone must be added before they can install.

### 4. Stay on Play internal testing

The status quo. Up to 100 testers by email address, no Data safety form, no store listing, no content
rating. First-time uploads show a temporary listing for up to 48 hours. Adding a reviewer's Google account
takes a minute, and it carries the "installed from Play" credibility signal.

**Verdict.** Zero work, already done, and enough for any individual you can name. Keep it running regardless
of what else you choose.

### 4a. Play internal app sharing — a QA tool, not a channel

Play Console's **Internal app sharing** page uploads an APK or AAB and hands back a download link
immediately: no review, no track, and version codes need not be new or unique, so the same build number can
be re-uploaded freely. It is genuinely useful for pushing a build to yourself or one reviewer in seconds.

It is not a distribution channel, for four reasons:

- **Recipients must flip a hidden toggle.** The Play Store app only honours these links after the tester
  opens Play Store → Settings, taps the Play Store version **seven times**, and enables *Internal app
  sharing*. Nobody outside a dev team will do this.
- **Links expire 60 days after upload**, and renewing means re-uploading to generate a new one.
- **A maximum of 100 users can download per link**, regardless of how widely the link is shared.
- **Builds are re-signed with a Google-generated Internal App Sharing key**, so the install does not carry
  the app's real signature and cannot be updated from any other channel.

**Verdict.** Faster than internal testing for a quick hand-off — no version bump, no track, instant link —
and worse than it on every axis that matters for reaching people. It does not advance the goal here.

### 5. Third-party Android stores

| Store | Status for RecipAI |
| --- | --- |
| **Amazon Appstore** | **Closed for Android since 20 August 2025**; no new Android submissions. Fire TV/tablet only. |
| **F-Droid / IzzyOnDroid** | Require an OSI/FSF-approved libre licence and a publicly accessible repository (Codeberg/GitLab/GitHub); IzzyOnDroid additionally prizes reproducible builds and bans trackers in privacy-sensitive apps. RecipAI's repo is private and unlicensed → not eligible without open-sourcing. |
| **Samsung Galaxy Store** | Free to publish, no annual fee, but Seller Portal requires a name matching a government ID, and business verification leans on D-U-N-S. Technical bar: target API ≥ 33, a 64-bit binary, and 16 KB page-size support for new/updated apps since 1 July 2026. Sellers must also complete Android developer verification before 30 September 2026. |
| **Huawei AppGallery** | Individual accounts are free but verification wants a full address, phone, identity-document scans *and* bank-card scans; review takes 1–2 days. Real reach in Poland is negligible. |
| **APKPure** | Free developer console; upload APK/XAPK up to 2 GB with icon, screenshots and description; review in 24–48 h. The lightest-weight real "store listing" available. |
| **Aptoide** | Free automated distribution is offered for free apps *already on Google Play*; otherwise a paid subscription applies. Free apps only, 7–10 day review. |

**Verdict.** Only APKPure is a low-friction win, and it buys a listing page more than an audience. The rest
either cost the same identity paperwork as Play or are unavailable.

### 6. Routes that still end on Play

- **Check the account creation date.** A personal account created on or before 13 November 2023 is exempt
  from the tester requirement entirely. This is a one-minute check and it either dissolves the problem or
  confirms it.
- **Organisation account.** Exempt from the tester rule, but needs a real registered legal entity and a
  D-U-N-S number (~28–30 days). Disproportionate for a portfolio app, and misdeclaring risks termination.
- **Tester-exchange communities.** r/betatesting, r/androiddev and Discord/itch.io threads where developers
  test each other's apps are the common honest route to 12 testers. Slow and unreliable, and Google checks
  that testers *genuinely used* the app. Paid tester farms trade the wait for account-termination risk.

## Comparison

| Option | Public? | Effort | Ongoing cost | Gatekeeper risk |
| --- | --- | --- | --- | --- |
| Flutter web PWA | ✅ anyone with the URL | medium — 4 plugin ports + backend extraction endpoint | VPS you already run | none |
| APK on your site / GitHub | ✅ anyone with the link | low | none | none — account verified, package registered |
| Firebase App Distribution | ❌ invite (500) | low | none | none |
| Play internal testing | ❌ invite (100) | none — done | none | none |
| APKPure listing | ✅ listed | low | none | store review |
| Samsung / Huawei | ✅ listed | medium — ID verification | none | same paperwork as Play |
| Play production | ✅ + discovery | high — 12 testers, 14 days, full App content | none | the blocker you already hit |

## Recommendation

Ruling out the web port (the decision taken), the sequence is:

1. **Confirm which key the registered package holds** — compare the fingerprint on the package names page
   against the upload key's `0C:1B:9E:EE:…:66:26`. This decides whether you distribute the Play-generated
   universal APK or register the upload key as an additional key.
2. **Publish the APK** on a small landing page next to the backend, linked from the README. Verification is
   already cleared, so this is genuinely public with no cap and no expiry date.
3. **Keep internal testing alive** for anyone who wants the Play-installed experience.
4. **Revisit Play production later**, when 12 testers are actually findable — APK users who like the app are
   exactly the people who will opt in.

The web PWA remains the strongest option on the merits — it is the only channel with no signing, store or
verification surface at all — and stays available if the APK route ever proves too awkward to share.

## Open questions / gaps

- **Is the Play Console account older than 13 November 2023?** Still unanswered from the previous research,
  and it is the cheapest thing to check.
- **Which signing key is registered against `xyz.stasiak.recipai`?** The package names page shows one key
  whose fingerprint has not been read; the two candidates are the app signing key `45:A8:…` and the upload
  key `0C:1B:…`. Everything about APK distribution follows from this.
- **Poland's exact developer-verification date** is not yet published — only "2027 and beyond". It does not
  gate anything here, since the package is already registered.
- **If the web port is ever revisited:** how much of the shopping-list offline layer must survive on web
  (`sqflite_common_ffi_web` is experimental), and whether the backend should gain a server-side URL-fetch
  endpoint — which carries SSRF exposure, since it fetches user-supplied URLs from the server.
- **Aptoide's terms for an app not on Google Play** were not conclusively established — the free tier is
  documented as covering apps already listed on Play.

## Sources

- [App testing requirements for new personal developer accounts — Play Console Help](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en) — confirms open testing unlocks only after production access, and that no exemptions exist.
- [Set up an open, closed, or internal test — Play Console Help](https://support.google.com/googleplay/android-developer/answer/9845334?hl=en) — tester limits per track (100 internal, 2,000 per closed list), how testers join, temporary listing for first uploads.
- [Share app bundles and APKs internally — Play Console Help](https://support.google.com/googleplay/android-developer/answer/9844679?hl=en) — internal app sharing: the Play Store developer toggle, 60-day link expiry, 100-download cap, reusable version codes, and re-signing with a Google-generated key.
- [Android developer verification: rolling out to all developers — Android Developers Blog](https://android-developers.googleblog.com/2026/03/android-developer-verification-rolling-out-to-all-developers.html) — the April/June/August 2026 milestones, limited distribution accounts (free, no ID, 20 devices), and what happens to unregistered apps.
- [Android developer verification — developer.android.com](https://developer.android.com/developer-verification) — scope of the programme, the 30 September 2026 four-country deadline and the 2027 global rollout.
- [Register on Google Play Console — developer.android.com](https://developer.android.com/developer-verification/guides/google-play-console) — automatic package registration for Play apps, and registering packages distributed outside Play.
- [Registering Android package names — Play Console Help](https://support.google.com/googleplay/android-developer/answer/16761053?hl=en) — package names are registered against SHA-256 certificate fingerprints, and multiple keys per package are supported.
- [generatedapks — Google Play Developer API](https://developers.google.com/android-publisher/api-ref/rest/v3/generatedapks) — the list/download methods that expose App bundle explorer's signed APKs, including `generatedUniversalApk.downloadId` and `certificateSha256Hash`.
- [Adding additional keys — Android Developer Console Help](https://support.google.com/android-developer-console/answer/16641418?hl=en) — how to add a further signing key to an already registered package name.
- [Sign and upload an APK — Play Console Help](https://support.google.com/googleplay/android-developer/answer/16761055?hl=en) — the `adi-registration.properties` proof-of-ownership upload used when registering a key.
- [Certified Android devices won't let users sideload APK files anymore — CNX Software](https://www.cnx-software.com/2026/03/20/certified-android-devices-wont-let-user-sideload-apk-app-files-anymore-or-at-least-it-wont-be-straightforward/) — practical reading of the advanced flow and what sideloading looks like after enforcement.
- [Android developer verification rollout begins — Android Authority](https://www.androidauthority.com/android-developer-verification-rollout-sideloading-flow-3653395/) — corroborating rollout dates and the sideloading UX.
- [Firebase App Distribution](https://firebase.google.com/docs/app-distribution) — APK and AAB support, tester onboarding, no SDK required.
- [Add and remove testers in App Distribution — Firebase](https://firebase.google.com/docs/app-distribution/add-remove-testers) — 500 testers per project, 200 per group.
- [App Distribution troubleshooting & FAQ — Firebase](https://firebase.google.com/docs/app-distribution/troubleshooting) — 1,000 releases per app, 150-day release expiry, limit-increase requests.
- [Build and release a web app — Flutter docs](https://docs.flutter.dev/deployment/web) — the supported web build and PWA output.
- [webview_flutter — pub.dev](https://pub.dev/packages/webview_flutter) — supported platforms are Android, iOS and macOS only; no web.
- [sqflite_common_ffi_web — pub.dev](https://pub.dev/packages/sqflite_common_ffi_web) — sqlite3 wasm over IndexedDB, version 1.1.1 (Jan 2026), still experimental.
- [image_picker_for_web — pub.dev](https://pub.dev/packages/image_picker_for_web) — endorsed web implementation, included automatically.
- [Flutter and Google Sign In for web applications (v7.2.0)](https://medium.com/@thomas.spillecke/flutter-and-google-sign-in-for-web-applications-v7-2-0-a64b8e5b5a5c) — web requires `google_sign_in_web`'s `renderButton`; no scopes granted by `signIn`/`renderButton`.
- [Creating a Progressive Web App with Flutter — Codemagic](https://blog.codemagic.io/pwa-in-flutter/) — manifest/service-worker setup and Android "Add to home screen" behaviour.
- [Obtainium — GitHub](https://github.com/ImranR98/Obtainium) — installs and updates apps straight from GitHub/GitLab release pages with update notifications.
- [Amazon is shutting down its Appstore for Android devices — Android Authority](https://www.androidauthority.com/amazon-shuts-down-android-app-store-3528170/) — 20 August 2025 shutdown; no new Android submissions.
- [App Inclusion Policy — IzzyOnDroid](https://izzyondroid.org/docs/general/AppInclusionPolicy/) — libre licence and public repository required; no trackers for privacy-sensitive apps.
- [FAQ - App Developers — F-Droid](https://f-droid.org/en/docs/FAQ_-_App_Developers/) — free-software licensing and source-availability requirements.
- [Get Started in Galaxy Store — Samsung Developers](https://developer.samsung.com/galaxy-store/prepare.html) — Seller Portal registration, government-ID name matching, no publishing fee.
- [Samsung Galaxy Store submission & ASO guide for 2026 — vmobify](https://vmobify.com/blog/samsung-galaxy-store-optimization) — API 33 minimum, 64-bit binary, 16 KB page size from 1 July 2026, ADV registration deadline.
- [How to create an APKPure Developer Console account](https://apkpure.com/howto/how-to-create-apkpure-developer-console-account-for-free) — free console, listing fields, 24–48 h review, 2 GB upload cap.
- [App submission FAQs — Aptoide Connect](https://docs.connect.aptoide.com/docs/app-submission-faqs) — free tier scoped to free apps already on Google Play, 7–10 day review.
- [How to create a developer account in App Store, Google Play and AppGallery — Friflex](https://medium.com/friflex/how-to-create-a-developer-account-in-app-store-google-play-and-appgallery-37e1f7ba92aa) — Huawei individual-account verification documents and timeline.
- [How to get 12 testers for Google Play closed testing fast — DEV](https://dev.to/tizoc_araujo_3cd9fb67191f/how-to-get-12-testers-for-google-play-closed-testing-fast-what-nobody-tells-you-3oa5) — tester-exchange communities and why manual recruitment is slow.
