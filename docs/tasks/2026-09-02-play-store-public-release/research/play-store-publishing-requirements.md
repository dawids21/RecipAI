# Publishing RecipAI publicly on Google Play

**Date:** 2026-09-02
**Source:** internet (Play Console Help, developer.android.com, Android Developers Blog, secondary guides)

## Summary

Moving RecipAI from the internal test track to a public (production) listing is not one more upload — it
crosses three gates that internal testing skips entirely. First, if the Play Console account is a
**personal** account created after 13 November 2023, production access must be *earned*: a closed test
with **12 testers opted in continuously for 14 days**, then a written application Google reviews (usually
≤ 7 days). Second, the **App content** section must be completed in full — privacy policy, Data safety
form, content rating questionnaire, target audience, ads, and (because RecipAI has accounts) an
**in-app account-deletion path plus a public web deletion URL**. Third, **developer identity verification**
is now mandatory before apps can be submitted, with Android-wide developer verification landing
30 September 2026 in the first four countries and rolling out globally through 2027.

The good news: the technical side is essentially already done. RecipAI targets API 36 (meets the
31 August 2026 deadline), builds a signed AAB with a separate upload key, and has no sensitive
permissions in its manifest. The real work is legal/administrative — a privacy policy, an account
deletion flow, store listing assets, and recruiting 12 testers.

## What changes when you leave internal testing

Internal testing is deliberately exempt from most of the store surface. This is why it "didn't require a
lot of work" — and it is exactly the delta you now have to close.

| Requirement | Internal test | Closed test | Production |
| --- | --- | --- | --- |
| Signed AAB, Play App Signing | ✅ required | ✅ | ✅ |
| Data safety form | ❌ exempt | ✅ required | ✅ |
| Privacy policy URL | ❌ | ✅ required | ✅ |
| Content rating questionnaire | ❌ | ✅ required | ✅ |
| Full store listing (icon, feature graphic, screenshots, descriptions) | ❌ | partial | ✅ required |
| Target audience / ads / other App content declarations | ❌ | mostly | ✅ required |
| 12 testers × 14 days + production access application | — | — | ✅ (new personal accounts) |
| Developer identity verification | — | — | ✅ required to submit |

## Key findings

### 1. Production access must be earned (new personal accounts)

- Applies to **personal** Play Console accounts created **after 13 November 2023**. Organisation accounts
  and older personal accounts publish straight to production.
- Requirement: a closed test with **at least 12 testers opted in continuously for at least 14 days**.
  Reduced from 20 testers on 11 December 2024.
- "Opted in" means the tester accepted the invite **and installed the app** under the matching Google
  account. Invited-but-not-installed does not count.
- The 14-day clock starts only once the closed-test release is **approved** and 12 testers are opted in.
  If a tester opts out and rejoins, their continuity counter resets — you need 12 *simultaneously* held
  for the whole window. Recruit 15–20 for slack.
- Google also checks that testers **genuinely used the app**, not just installed it.
- Afterwards: app Dashboard → **Apply for production**. A three-section form asks how you recruited
  testers, whether they used all features, how you collected feedback, who your target audience is, your
  value proposition, and what you changed based on feedback. Review is **usually ≤ 7 days**; the result
  emails the account owner. Rejections are appealable/re-submittable.

### 2. Developer identity verification

- You **must verify your developer account before you can submit apps**. Verification can be completed in
  Play Console from 60 days before your individual deadline.
- **Personal account**: legal name, address, email, phone, and an official government photo ID. The name
  on the ID must match the Play Console profile exactly, and the address must match what you registered.
- **Organisation account**: a D-U-N-S number plus government ID and an official organisation document.
  A D-U-N-S number is **not** required for personal accounts.
- Your **verified email address is shown publicly** on the store listing (organisations also show a phone
  number). If you do not want a personal address public, set up a dedicated support mailbox now.
- Failure to verify: developer presence and apps may be removed from Google Play, with no republishing
  until verification completes.
- Separately, **Android developer verification** (which also covers sideloaded/other-store distribution)
  takes effect **30 September 2026** in Brazil, Indonesia, Singapore and Thailand, expanding globally
  through 2027. Roughly **99% of Play apps are registered automatically** — but the July 2026 policy
  announcement warns that apps left unregistered face **global removal**, so check the Play Console Home
  page for a registration prompt.

### 3. App content declarations (the "Grow → App content" page)

All of these block a production release:

- **Privacy policy** — a public URL in Play Console *and* a link or the text reachable **inside the app**.
  Non-negotiable for RecipAI: it handles accounts, emails, user-generated recipes and photos.
- **Data safety form** — required for every app on closed/open/production tracks, **including apps that
  collect no data**. Declares, across 14 data categories (personal info, photos/media, app activity,
  device IDs, …): what is collected, what is shared, why, whether it is encrypted in transit, and whether
  users can request deletion. Must match what the app actually does — mismatches are an enforcement
  trigger. You may claim deletion support if you offer a deletion mechanism *or* auto-delete/anonymise
  within 90 days.
- **Account deletion** — because RecipAI lets users create accounts (Firebase Auth + Google Sign-In), you
  must provide **(a)** an in-app path to delete the account and its data, and **(b)** a **publicly reachable
  web link** where deletion can be requested without installing the app. The web page must be
  error-free, put the deletion path front and centre, and name the app/developer as shown on the listing.
  Both are then declared in the Data safety form. Enforcement (including removal) has been live since
  mid-2024.
- **Content rating questionnaire** — completed with IARC-affiliated authorities. Unrated apps are not
  allowed on Google Play; the July 2026 update restated this explicitly.
- **Target audience and content** — declare the age groups. Keep children *out* of the target audience
  unless you intend to meet the full Families policy (Designed for Families, ad SDK restrictions, etc.).
- **Ads** — declare whether the app contains ads, including third-party ad SDKs. RecipAI: no.
- **App access** — RecipAI is behind a login, so you must supply **working demo credentials or a
  sign-in walkthrough** for the reviewer, or the review will fail. Google Sign-In-only auth is awkward
  here; plan for a reviewer-usable account.
- **Government / financial / health / news / COVID declarations** — all "no" for RecipAI, but the
  questions still have to be answered.
- **Permissions declaration form** — only if you request high-risk permissions (SMS, Call Log, all-files
  access, background location). RecipAI's manifest has none, so this should not appear.

### 4. Store listing assets (production only)

| Asset | Spec | Required |
| --- | --- | --- |
| App name | short, ≤ 30 chars | ✅ |
| Short description | 80 characters max | ✅ |
| Full description | 4000 characters max | ✅ |
| App icon | 512×512 px, 32-bit PNG with alpha, ≤ 1024 KB | ✅ |
| Feature graphic | 1024×500 px, JPEG or 24-bit PNG (no alpha) | ✅ |
| Phone screenshots | min 2 (4+ recommended), 1080 px min side, 16:9 or 9:16, 320–3840 px | ✅ |
| Tablet screenshots | 4 recommended, 1080–7680 px | recommended |
| Preview video | one public/unlisted YouTube URL, ads disabled | optional |

Also: category, contact email, and (if you have one) website and phone.

### 5. Build and signing

- New apps must ship as an **Android App Bundle (AAB)**, not an APK — mandatory since August 2021.
- **Play App Signing is required** for new apps. Google holds the app signing key; you sign the bundle
  with a separate **upload key**. Keeping the two distinct is the recommended posture.
  RecipAI already does this: `mobile/android/app/build.gradle.kts` reads a release signing config from
  `upload-key.properties` and the build fails loudly if it is missing.
- **Target API level**: from **31 August 2026**, new apps and updates must target **Android 16 (API 36)**.
  Extensions to 1 November 2026 can be requested. **RecipAI already targets API 36** — Flutter 3.44.4's
  `flutter.targetSdkVersion` is 36 and `build.gradle.kts` inherits it, so this gate is met with no change.
- Run the **pre-launch report** (automatic on closed/open/production uploads): Google runs the app on real
  devices and reports crashes, accessibility problems, and security warnings.

### 6. EU distribution — DSA trader status

- Google Play requires a **trader status declaration** under the EU Digital Services Act. The compliance
  deadline for Play developers passed on **27 May 2026**.
- "Trader" means you earn income professionally from apps. If yes, you must publish legal entity details,
  a contact email and phone, and a legal address — which becomes visible to EU users.
- Non-compliance means **removal from all 27 EU territories**. If RecipAI is free and non-commercial, you
  declare non-trader — but the declaration itself is still mandatory.

### 7. Policy areas specific to what RecipAI does

- **AI-generated content**: Play's AI-Generated Content policy targets apps where AI generation is a
  *central user-facing feature* (chatbots, image/voice/video generators). RecipAI uses an LLM to *extract*
  structured recipes from user-supplied text and images, which sits outside the listed categories — but
  the July 2026 update brought **third-party AI integrations under the User Data policy**, so the
  Data safety form and privacy policy must disclose that recipe text and uploaded photos are sent to a
  third-party model provider. Treat that as the compliance obligation, not the generative-content rules.
- **Photos and media**: RecipAI uses `image_picker`, which on modern Android goes through the system photo
  picker and needs no broad media permission. Photos are still a declarable Data safety category.
- **User-generated content**: shared recipes/collections/plans between users make this a UGC app. Play's
  UGC policy expects a reporting mechanism and moderation posture; scope is small (share-by-invite only,
  not public publishing), but worth a look before submitting.
- **Deceptive behaviour / functionality**: the app must not appear broken to a reviewer. The backend at
  `https://recipai.stasiak.xyz` must be reliably up during review, or the app reads as non-functional.

## Can a solo developer skip the testers?

Short answer: **not on the production track**, if the Play Console account is a personal one created after
13 November 2023. There is no waiver, no hobbyist exemption, and no "small app" carve-out. The options are:

- **Check the account creation date first.** A personal account created **on or before 13 November 2023**
  is exempt and publishes straight to production. This costs nothing to check and settles the question.
- **Organisation account.** Exempt from the tester requirement entirely — but it requires a *real
  registered legal entity* and a D-U-N-S number, which takes roughly 28–30 days to obtain (free from
  Dun & Bradstreet). Registering a business purely to skip a 14-day test is disproportionate for a
  portfolio app, and declaring an organisation that does not exist risks account termination.
- **Open testing is not a shortcut.** The open (public beta) track only becomes available *after*
  production access is granted, so it cannot be used to sidestep closed testing.
- **The 12 testers do not have to be strangers.** Friends, family and colleagues count. Each needs a
  distinct Google account and a **real physical Android device**. Emulators, virtual machines, several
  accounts belonging to the same person, and accounts sharing one IP or device fingerprint are detected
  and are common grounds for rejecting the production application.
- **Paid tester services** (~$15 for 12 testers) exist and advertise heavily. Google explicitly screens
  for coordinated inauthentic activity, so this trades a 14-day wait for account-termination risk.

Note that clearing the tester gate removes only **one of the three gates**. Identity verification, the
privacy policy, the Data safety form, account deletion and the store listing are all still required —
an organisation account does not avoid any of them.

### If the goal is showcasing the app, not shipping it

A Play Store listing is one distribution channel, and for a portfolio it is the most expensive one. Cheaper
routes that need none of the above:

- **Internal testing** — where RecipAI already is. Up to 100 testers by email address, no store listing,
  no Data safety form. Adding a reviewer's Google account takes a minute.
- **Firebase App Distribution** — the project already uses Firebase. Link- or email-based distribution to
  testers, entirely outside Play's release gates.
- **Signed APK on GitHub Releases**, linked from the repository README. Zero Play requirements, and the
  repository is what an engineering reviewer usually looks at anyway.

The one caveat on sideloaded distribution: Android developer verification will eventually extend to apps
installed outside Play (Poland falls in the 2027 global rollout). For exactly this case there is a
**limited distribution account** for students, teachers and hobbyists — up to 20 devices, no
government-issued ID, no registration fee.

**Recommendation:** if the Play badge itself is the point, the honest path is the closed test with a dozen
friends on real phones — about three weeks of calendar time on top of the privacy-policy and
account-deletion work. If the point is demonstrating the work, a GitHub Releases APK plus the existing
internal-testing link achieves it today, for nothing.

## Recommended order of work

1. **Verify the developer account** and check Play Console Home for the developer-verification /
   app-registration prompt. Nothing else can be submitted until this clears.
2. **Write and host a privacy policy**; link it in Play Console *and* from inside the app.
3. **Build the account-deletion path** — backend endpoint that deletes the Firebase user plus all owned
   recipes, collections, plans, shopping lists, images and permissions; an in-app entry point; and a
   public web page for deletion requests. This is the largest engineering item on the list.
4. **Complete App content**: Data safety form, content rating, target audience, ads, app access
   (reviewer credentials), trader status.
5. **Produce store listing assets** (icon, feature graphic, ≥ 4 phone screenshots, descriptions).
6. **Promote to closed testing**, recruit ≥ 15 testers, hold 12 opted in for 14 days, and encourage real
   feature usage (recipe extraction, planning, shopping lists, sharing).
7. **Apply for production access**, answer the three-section form with concrete tester feedback, and wait
   ≤ 7 days.
8. **Release to production**, ideally as a staged rollout.

Realistic timeline: **4–8 weeks**, dominated by the 14-day tester window plus the ≤ 7-day review, and
gated by how fast the privacy policy and deletion flow get built.

## Open questions / gaps

- **Account type and creation date** — is the RecipAI Play Console account personal or organisation, and
  was it created after 13 November 2023? This single fact decides whether the 12-tester/14-day gate
  applies at all. Everything else in this report applies either way.
- **Where the deletion web page lives** — a page on `stasiak.xyz`, a GitHub Pages site, or a route on the
  backend? Same question for the privacy policy. Not yet decided.
- **What the LLM provider is told** — the Data safety disclosure depends on which model provider the
  backend calls and whether recipe text/images are retained by them. Needs checking against the
  provider's data-retention terms before the form is filled in.
- **Reviewer access with Google Sign-In only** — Play expects working demo credentials. Whether to add a
  reviewer-only email/password path or document a Google test account is unresolved.
- **UGC moderation posture** — whether Play considers invite-only sharing to trigger UGC reporting
  obligations was not conclusively answered by the sources consulted.
- Country-by-country dates for identity verification beyond the first four (Brazil, Indonesia, Singapore,
  Thailand) are not yet published in detail; Poland's deadline sits somewhere in the 2027 global rollout.

## Sources

- [App testing requirements for new personal developer accounts — Play Console Help](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en) — the 12-testers/14-days rule, who it applies to, the production access application and review timeline.
- [Verify your developer identity information — Play Console Help](https://support.google.com/googleplay/android-developer/answer/10841920?hl=en) — personal vs organisation verification documents, D-U-N-S scope, public contact details, consequences of non-verification.
- [Provide information for Google Play's Data safety section — Play Console Help](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en) — who must file, the internal-testing exemption, data categories, security and deletion questions.
- [Prepare your app for review — Play Console Help](https://support.google.com/googleplay/android-developer/answer/9859455?hl=en) — the App content declaration list: privacy policy, ads, app access, target audience, permissions form, content ratings.
- [Provide a way for users to request app account deletion](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en) — the in-app path plus web URL requirement and its enforcement dates.
- [Add preview assets to showcase your app — Play Console Help](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en) — exact icon, feature graphic and screenshot specifications.
- [Set up your app's store listing — Play Console Help](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en) — the 30 / 80 / 4000 character limits for app name, short description and full description.
- [Target API level requirements for Google Play apps — Play Console Help](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en) — the 31 August 2026 API 36 deadline and the extension option.
- [Policy announcement: July 15, 2026 — Play Console Help](https://support.google.com/googleplay/android-developer/answer/17134731?hl=en) — app registration or global removal, third-party AI under the User Data policy, no unrated apps, the API 36 reminder.
- [Android developer verification — developer.android.com](https://developer.android.com/developer-verification) — scope, the 30 September 2026 four-country deadline, the global 2027 rollout, and automatic registration for Play developers.
- [Understanding Google Play's AI-Generated Content policy — Play Console Help](https://support.google.com/googleplay/android-developer/answer/14094294?hl=en) — which app categories the generative-AI rules cover.
- [Use Play App Signing — Play Console Help](https://support.google.com/googleplay/android-developer/answer/9842756?hl=en) — the upload key / app signing key split required for new apps.
- [Android App Bundle FAQ — developer.android.com](https://developer.android.com/guide/app-bundle/faq) — AAB mandatory for new apps since August 2021.
- [Google will require developer verification for Android apps outside the Play Store — TechCrunch](https://techcrunch.com/2025/08/25/google-will-require-developer-verification-for-android-apps-outside-the-play-store/) — background and rollout context for the verification programme.
- [Trader Status for Developer: DSA of the EU](https://makaka.org/unity-tutorials/trader-status) — the EU DSA trader declaration, the 27 May 2026 Play deadline, and EU-wide removal as the penalty.
- [Google Play's 12 Testers, 14 Days Rule (2026) — testfi.app](https://www.testfi.app/blog/google-play-closed-testing-requirement-explained) — practical reading of what "opted in continuously" means and the continuity reset.
- [Google Play Store Submission Checklist 2026 — applaunchflow.com](https://www.applaunchflow.com/blog/google-play-store-submission-checklist-2026) — corroborating checklist of which items block closed testing versus production.
- [Internal vs Closed vs Open Testing on Google Play (2026)](https://www.testerscommunity.com/guides/internal-vs-closed-vs-open-testing-google-play) — that open testing unlocks only after production access, so it is not a route around closed testing.
- [Personal vs Organization Google Play Account — primetestlab.com](https://primetestlab.com/blog/personal-vs-organization-google-play-account-12-testers) — organisation accounts are exempt from the tester rule, and what a D-U-N-S number actually requires.
- [Google Play 12 Testers Closed Testing — testerbee.com](https://testerbee.com/blog/google-play-12-testers-closed-testing) — that friends and family count as testers, and that emulators, shared IPs and duplicate accounts are grounds for rejection.
