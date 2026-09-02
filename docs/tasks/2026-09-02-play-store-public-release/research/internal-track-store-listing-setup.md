# Finishing the store listing while staying on internal testing

**Date:** 2026-09-02
**Source:** internet (Play Console Help, developer.android.com, secondary guides) plus inspection of the
RecipAI repository
**Companions:** [`play-store-publishing-requirements.md`](play-store-publishing-requirements.md) — the
production gates; [`alternative-distribution-options.md`](alternative-distribution-options.md) — routes
that avoid Play entirely

## Summary

None of the nine remaining items on the "Finish setting up your app" checklist block an internal test —
Play Console states outright that "you can start an internal test before completing app setup", and the
Data safety form carries an explicit written exemption for apps that live only on that track. What makes
the work worth doing anyway is that **the store listing is shared across tracks**: internal testers open a
real Play Store page, and until the listing is filled in they see the package name `xyz.stasiak.recipai`
rather than "RecipAI", with no icon, screenshots or description.

Seven of the nine are ten-minute form-filling jobs with an obvious answer for RecipAI. Two need thought:
**Sign-in details**, because the app is Google Sign-In only and a reviewer cannot use your Google account;
and **Health**, because the declaration's "Nutrition and Weight Management" option is worded as
"planning meals" — and health apps are required to sit on an **Organisation** developer account, which a
personal account cannot satisfy without a D-U-N-S number.

Two of the graphic assets already in the repo do not meet Play's specs and will be rejected on upload.

## The one thing that actually matters for internal testers

The store listing is not per-track. Play Console's store listing page says the listing "is shared across
tracks, including testing tracks". So the four things internal testers see improve the moment you fill
them in:

| Field | Effect on the tester's Play Store page |
| --- | --- |
| App name (≤ 30 chars) | Replaces the raw application ID in the title |
| App icon (512×512) | Replaces the placeholder tile |
| Short description (≤ 80 chars) | The one line under the title |
| Screenshots (≥ 2) | The carousel; without them the page looks broken |

Everything else on the checklist is paperwork that unlocks *later* tracks. It costs an hour and removes
the "not started" state, but it changes nothing your internal testers experience.

## Item-by-item

The checklist has 11 entries; **Set privacy policy** and **Ads** are already done. The rest:

### 1. Sign-in details — needs a decision

Formerly called "App access". You declare either that all functionality is available without signing in,
or you supply credentials a reviewer can use. RecipAI is entirely behind Firebase Auth, so "no sign-in
required" is not truthful.

Google's requirements page is strict: credentials must be "accessible at all times, reusable, and valid
regardless of user location", maintained without error, in English, must bypass 2FA, and must be a **test
account, never a production user's**. For OAuth providers it says explicitly: for "sign-in with Google,
Facebook, or similar", you must "provide all account information with detailed instructions".

For RecipAI that means one of:

- **Create a dedicated Google account** used only for review, seed it with a few recipes and a meal plan,
  and paste its email + password into the form with a short walkthrough. Simplest, but you must keep the
  password from expiring and disable 2FA on it.
- **Add an email/password sign-in path** for reviewers. Firebase Auth already supports the provider; it is
  a mobile-side change plus enabling the provider in the Firebase console. More engineering, less
  ongoing babysitting.

Not urgent while you stay on internal testing — nobody reviews an internal build's functionality — but it
is the item most likely to fail a future closed-test review, so decide it before you need it.

### 2. Content rating — questionnaire, ~10 minutes

You enter an email address for IARC correspondence, pick a category, and answer content questions
(violence, sexuality, language, controlled substances, gambling, user interaction). RecipAI's only
non-trivial answers are the user-interaction ones: users **can** share recipes, collections and plans with
other users, and can exchange user-generated content that way. Answer those honestly — invite-only sharing
still counts as users interacting.

The output is a set of per-territory ratings (PEGI, ESRB, USK, …) attached to the listing. Unrated apps are
not permitted on Google Play and the July 2026 policy update restated it, so this is worth clearing even
though internal testing does not enforce it.

### 3. Target audience and content — pick 18+

You select target age groups. Any selection that includes an age band with children pulls the app under
the **Families Policy**, which brings ad-SDK restrictions, age verification and parental-consent handling.
RecipAI has no reason to want that. Select **18 and over** only.

The section also asks whether the store listing unintentionally appeals to children — relevant only if the
graphics use cartoon characters, which RecipAI's do not.

### 4. Data safety — explicitly exempt today, but fill it in

The Play Console Help page is unusually direct: "Apps that are active on internal testing tracks are exempt
from inclusion in the data safety section. Apps that are exclusively active on this track do not need to
complete the Data safety form."

If you fill it in anyway, RecipAI's honest answers across the 14 categories are roughly:

| Category | Collected | Shared | Purpose |
| --- | --- | --- | --- |
| Personal info → Email address | Yes (Firebase Auth / Google Sign-In) | No | Account management |
| Personal info → User IDs | Yes (Firebase UID) | No | Account management, app functionality |
| Photos and videos | Yes (recipe images → AWS S3) | Yes — sent to Google Gemini for extraction | App functionality |
| App activity → Other user-generated content | Yes (recipes, collections, plans, shopping lists) | Yes — recipe text sent to Gemini | App functionality |

Two answers deserve care. **Encryption in transit** is yes (HTTPS to `recipai.stasiak.xyz`, presigned S3
URLs). **"Can users request data deletion?"** is the awkward one — RecipAI has no account-deletion path
today. You may only answer yes if a deletion mechanism exists or data is auto-deleted within 90 days, and
the Data safety answers must match what the app actually does. Answering it honestly ("no") is allowed on
the form but conflicts with the separate account-deletion policy the moment you leave internal testing.

The third-party sharing rows are the ones people get wrong: recipe text and uploaded photos leave your
infrastructure for Gemini, and the July 2026 update brought third-party AI integrations under the User
Data policy. Declare it.

### 5. Government apps — No

Select that the app is not developed by or for a government. Government apps are restricted to
organisation accounts; a personal account answers "no" here always.

### 6. Financial features — "My app doesn't provide any financial features"

Mandatory for every app regardless of whether it has any. RecipAI has no payments, lending, crypto,
insurance or investment functionality. One click.

### 7. Health — the one with a trap

The obvious answer is "My app doesn't provide any health features". The complication is the wording of the
alternative: the **Nutrition and Weight Management** option reads "Tools for tracking dietary intake,
**planning meals**, managing diets, and supporting weight loss or weight management goals" — and RecipAI
plans meals.

Why the answer matters more than a checkbox usually does: Play Console Requirements lists health apps
among the categories that **must register as an Organisation account** (alongside financial services, VPN
and government apps), with existing health apps required to migrate to a verified organisation account by
28 January 2026. An organisation account needs a real registered legal entity and a D-U-N-S number.
Declaring a health feature on a personal account therefore risks a category/account-type conflict that no
form field can resolve.

The case for "no health features" is solid on the facts: RecipAI stores recipes, plans which recipe is
cooked on which day, and builds shopping lists. It has **no calorie counting, no macro or nutrient data, no
dietary-goal tracking, no weight logging, and no Health Connect integration** — nothing in the codebase or
docs references nutrition. The policy's own scope is apps that "offer health-related features or
information" or "access health data"; a recipe organiser does neither. "Planning meals" in that sentence
sits inside a clause about dietary intake and weight management, not calendar scheduling.

**Recommendation:** declare no health features, and pick **Food and Drink** as the app category (not
Health and Fitness) so the listing does not contradict the declaration. Revisit if nutrition data is ever
added — that would flip the answer and drag the account-type question with it.

### 8. Select an app category and provide contact details

- **App or game:** App
- **Category:** Food and Drink
- **Tags:** up to five; recipe/cooking/meal-planning tags fit
- **Contact email:** required, and **shown publicly on the listing**. Use a dedicated address, not the
  personal Gmail on the developer account.
- **Website and phone:** optional

### 9. Set up your store listing

| Field | Spec | RecipAI status |
| --- | --- | --- |
| App name | ≤ 30 characters | "RecipAI" — fine |
| Short description | ≤ 80 characters | to write |
| Full description | ≤ 4000 characters | to write |
| App icon | 512×512, 32-bit PNG **with** alpha, ≤ 1024 KB | `mobile/assets/graphics/icon.png` — 512×512 RGBA ✅ |
| Feature graphic | 1024×500, JPEG or **24-bit PNG (no alpha)** | `feature_graphic.png` — 1024×500 but **RGBA** ❌ |
| Phone screenshots | ≥ 2 (up to 8), each side 320–3840 px, max side ≤ 2× min side | two 1080×2214 JPEGs — **ratio 2.05 ✗** |
| Tablet screenshots | 4 recommended, 16:9 or 9:16 | none |
| Preview video | public/unlisted YouTube URL, ads disabled | none |

Two concrete fixes needed in `mobile/assets/graphics/`:

- **`feature_graphic.png` carries an alpha channel.** Play's spec is "JPEG or 24-bit PNG (no alpha)".
  Flatten it onto an opaque background or export as JPEG.
- **The screenshots are 1080×2214, which violates the aspect rule.** Play requires that "the maximum
  dimension of your screenshot can't be more than twice as long as the minimum dimension" — 2 × 1080 =
  2160, so they are 54 px too tall. Crop to 1080×2160 (or shorter). They are Galaxy S911 device captures
  with EXIF intact; stripping metadata while cropping is tidy.

Two screenshots meets the minimum, but four or more is the recommendation and gives testers a real sense of
the app — recipe list, recipe detail, meal plan calendar, shopping list.

## How the changes get published

Store listing, store settings, content rating and other app content edits are **held for review** — they
appear under "Changes ready to send for review" on the Publishing overview page and are not live until you
send them and Google approves. Reviews of this kind are typically quick but can take days. This is
independent of the app bundle: you can update the listing without shipping a build, and the listing edits
publish on their own schedule.

Nothing here forces an internal-testing release to be re-reviewed, and nothing here can break the build
your testers already have installed.

## Recommended order

1. **Fix the two graphic assets** (flatten the feature graphic, crop the screenshots to 1080×2160), and
   capture two more screenshots — meal plan and shopping list.
2. **Store settings**: App, Food and Drink, tags, dedicated contact email.
3. **Store listing**: name, short description, full description, icon, feature graphic, screenshots.
4. **Send for review** and confirm the tester-facing page now shows "RecipAI" with the icon and carousel.
5. **The quick declarations** in one sitting: Government apps (no), Financial features (none), Health (none),
   Target audience (18+), Content rating (questionnaire, disclose user-to-user sharing).
6. **Data safety** — optional while internal-only, but filling it in now surfaces the account-deletion gap
   before it becomes blocking.
7. **Sign-in details** — decide between a dedicated reviewer Google account and adding an email/password
   provider. Leave until you actually intend to move to closed testing.

Steps 1–4 are the ones that change anything for your internal testers. Steps 5–7 are pre-work for a track
you have said you do not intend to reach.

## Open questions / gaps

- **Whether Play's health classifier is automated.** The declaration is self-reported, but the category
  and store listing text are scanned. If the full description leans on "meal planning" and "diet", it is
  conceivable an automated check flags a mismatch with a "no health features" declaration. No source
  consulted describes how that check works.
- **Whether internal testing is exempt from the Financial features and Health declarations.** Both help
  pages say "including apps on closed testing, open testing, or production tracks" and conspicuously omit
  internal testing — the same phrasing the Data safety page uses immediately before granting internal
  testing an explicit exemption. The omission strongly implies the same carve-out, but unlike Data safety
  it is never stated. Filling them in costs a minute and settles it.
- **Content rating for internal-only apps.** No source found states whether the "unrated apps are removed"
  rule reaches apps that have never left internal testing.
- **Whether an internal test's opt-in page reflects listing changes immediately** or on the same review
  cycle as the store listing itself. Sources describe a 48-hour temporary-listing window for first-time
  uploads but not the steady-state refresh behaviour.
- **Account deletion.** Not on this checklist and not required for internal testing, but it is the
  precondition for answering the Data safety deletion question honestly, and it is the largest engineering
  item standing between RecipAI and any public track.

## Sources

- [Set up your app's store listing — Play Console Help](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en) — the listing is shared across tracks including testing tracks; the 30 / 80 / 4000 character limits; contact email required.
- [Set up an open, closed, or internal test — Play Console Help](https://support.google.com/googleplay/android-developer/answer/9845334?hl=en) — "You can start an internal test before completing app setup"; 100 testers; email lists and the opt-in link.
- [Provide information for Google Play's Data safety section — Play Console Help](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en) — the verbatim internal-testing exemption, the 14 data categories, and the collected/shared/encryption/deletion questions.
- [Requirements for providing sign in details for review — Play Console Help](https://support.google.com/googleplay/android-developer/answer/15748846?hl=en) — credentials must be reusable, location-independent, error-free, English, 2FA-bypassing, test-account only; explicit instructions required for Google Sign-In.
- [Prepare your app for review — Play Console Help](https://support.google.com/googleplay/android-developer/answer/9859455?hl=en) — the App content section list: privacy policy, ads, sign-in details, target audience, content ratings, permissions form.
- [Provide information for the Financial features declaration — Play Console Help](https://support.google.com/googleplay/android-developer/answer/13849271?hl=en) — mandatory for all apps; the "doesn't provide any financial features" option; tracks listed.
- [Provide information for the Health apps declaration form — Play Console Help](https://support.google.com/googleplay/android-developer/answer/14738291?hl=en) — the full category list including "Nutrition and Weight Management: tools for tracking dietary intake, planning meals, managing diets" and the "doesn't provide any health features" option.
- [Health Content and Services — Play Console Help](https://support.google.com/googleplay/android-developer/answer/16679511?hl=en) — policy scope: apps offering health features or accessing health data.
- [Health app categories and additional information — Play Console Help](https://support.google.com/googleplay/android-developer/answer/13996367?hl=en) — the Health and Fitness / Medical / Research grouping and the obligations that follow a health declaration.
- [Play Console Requirements — Play Console Help](https://support.google.com/googleplay/android-developer/answer/10788890?hl=en) — financial, health, VPN and government apps must use an Organisation account; D-U-N-S requirement.
- [Google Play Health Apps Update: New January 2026 Requirements — myappmonitor.com](https://myappmonitor.com/blog/google-play-health-apps-update-2026-requirements) — the 28 January 2026 deadline for migrating existing health apps to a verified organisation account.
- [Target audience and content — Play Console Help](https://support.google.com/googleplay/android-developer/answer/9285070?hl=en) — age-group selection, the Families Policy consequences of including children, and the "appeals to children" check.
- [Content rating requirements for apps, games, and the ads served in them — Play Console Help](https://support.google.com/googleplay/android-developer/answer/9859655?hl=en) — the IARC questionnaire flow, per-app ratings, and the email correspondence process.
- [Content Ratings — Play Console Help](https://support.google.com/googleplay/android-developer/answer/9898843?hl=en) — unrated apps may be removed from Google Play; misrepresentation risks suspension.
- [Add preview assets to showcase your app — Play Console Help](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en) — icon, feature graphic ("JPEG or 24-bit PNG (no alpha)") and screenshot specs including "the maximum dimension of your screenshot can't be more than twice as long as the minimum dimension".
- [Control when app changes are reviewed and published — Play Console Help](https://support.google.com/googleplay/android-developer/answer/9859654?hl=en) — which changes need review, the Publishing overview sections, and managed publishing.
- [Choose a category and tags for your app or game — Play Console Help](https://support.google.com/googleplay/android-developer/answer/9859673?hl=en) — the app category list including Food and Drink, and the five-tag limit.
- [Policy announcement: July 15, 2026 — Play Console Help](https://support.google.com/googleplay/android-developer/answer/17134731?hl=en) — third-party AI integrations under the User Data policy; no unrated apps.
- [Filling out the App Content Declarations — webtoapp.design](https://webtoapp.design/blog/configure-app-content-in-play-console) — practical walkthrough corroborating the Government apps "always no for personal accounts" guidance.
- [How to Set Up Google Play Internal Testing (2026) — primetestlab.com](https://primetestlab.com/blog/google-play-internal-testing-setup) — that a finished listing, screenshots, content rating and Data safety form are not required to run an internal test.
- [Internal Testing in Google Play Store — Medium](https://medium.com/@tukaianirban/internal-testing-in-google-playstore-47de955062ad) — that testers see the application ID rather than the app name until the listing is configured.
