# User usage limits

**Date:** 2026-07-29

## Summary

Introduce per-user, per-resource usage limits (stock caps and per-period flow caps) with a set of
defaults applied to every user, changeable at runtime without a redeploy.

## Context

RecipAI is being published to the app store. Until now it has been used by its developer and a small
number of testers, so resource consumption was self-policing. With unknown users able to sign up, the
expensive paths are exposed: AI extraction calls cost money per request, and stored recipe images
consume S3 storage and bandwidth. The goal is to prevent a stranger from spiking cloud and AI bills.

Limits already exist in the codebase, but ad-hoc and global:

- `planning` enforces a configurable owner plan limit (`maxOwnedPlans`), throwing
  `MealPlanLimitExceededException` (HTTP 409).
- `recipes.images` hardcodes `MAX_IMAGES = 2` per recipe.
- `ImageProcessingService` hardcodes a 5 MB per-image size cap, mirrored client-side in
  `recipe_image_manager.dart`.

None of these vary per user, and the extraction path has no limit at all.

## Requirements

- Every user has a set of limits, one per limited resource.
- A set of **default** limits applies to users without an explicit override. At rollout, every
  existing user lands on the defaults.
- A limit is configurable per user, overriding the default for that user and resource.
- Each limit is either:
  - a **stock** cap — maximum held at any one time (e.g. 100 recipes owned), or
  - a **flow** cap — maximum consumed per period (e.g. 2 extractions per day).
  The stock/flow choice is configurable per user per resource: one user may have "5 extractions
  ever", another "2 extractions per day".
- Resources in scope:

  | Resource | Scope of the count |
  |---|---|
  | AI extractions | per user |
  | Recipes owned | per user (owner only) |
  | Shopping lists owned | per user (owner only) |
  | Items per shopping list | per list |
  | Meal plans owned | per user (owner only) — replaces the existing `maxOwnedPlans` |
  | Collections owned | per user (owner only) |

- When a user attempts an action that would exceed a limit, the action is rejected and the user is
  told why. Existing content remains fully readable and editable.
- Changing a limit takes effect immediately for subsequent requests — no application restart or
  redeploy. The mobile client is the one caveat, spelled out in the acceptance criteria below.
- Users can see their recipe limit somewhere in the app. For AI extractions, users at minimum see a
  clear explanation at the moment the action is blocked.

## Anti-requirements

- **No billing or payments.** No paid plans, no purchase flow, no payment provider integration.
- **No self-serve upgrades.** Users cannot raise their own limits or request a raise in-app.
- **No admin UI or admin API.** Limits are changed by editing the database directly. Building a
  management surface is explicitly deferred.
- **No named tiers.** The originally-considered tier model was dropped in favour of per-user values
  plus defaults. Grouping users under named plans may come later.
- **No "unlimited" sentinel value.** A large number (e.g. 99999) is an acceptable stand-in for
  unlimited, including for the developer's own account.
- **No per-user image size limit.** The global 5 MB per image cap stays as-is.
- **Sharing is not restricted.** Shared items count only against the owner (see Constraints).

## Constraints & assumptions

- Users are identified by their Firebase JWT. The backend currently has no user table — identity
  exists only as a token claim.
- **All extraction attempts count**, regardless of outcome. A call that reaches Gemini and returns
  garbage, or one that fails afterwards with a server error, still consumes the user's budget. The
  cost is incurred whether or not the user gets a usable recipe.
- **Sharing is an accepted amplifier.** Because shared items count only against the owner, a user at
  their recipe cap can still be granted access to arbitrarily many recipes by another account, and
  reading those recipes hits S3 presigned URLs. This is a known and accepted hole.
- Recipe count transitively bounds image storage, since a recipe holds at most 2 images of at most
  5 MB each.
- Both applications must keep working for users who are over a limit — being over a cap is a normal,
  expected state, not an error condition.

## Acceptance criteria

- [ ] A new user signs up and receives the default limits without any manual setup.
- [ ] The user creates recipes until the recipe limit is reached; further creation attempts are
      rejected with an explanation.
- [ ] The recipe limit for that user is raised by editing the database; the backend accepts the very
      next creation request with no restart or redeploy. The mobile client greys Save out against
      caps it loaded once this session, so it enables again at the next app start.
- [ ] The same cycle works for AI extractions: the user is blocked at the limit, the limit is raised
      in the database, and the next request is accepted — with the same client-side caveat.
- [ ] A user who is over a limit (because it was lowered) can still view, open, and edit all of their
      existing content.
- [ ] A failed extraction consumes a unit of the user's extraction budget.
- [ ] Limits apply independently per user: raising one user's limit does not affect another's.
- [ ] The existing `maxOwnedPlans` behaviour is preserved through the new mechanism rather than
      duplicated alongside it.

## Edge cases

- **Over the cap after a change.** Lowering a limit (or introducing one) can leave a user above it.
  They keep full read and edit access; only creation of new items is blocked.
- **Per-list vs per-user counting.** The shopping list item limit applies to each list individually,
  not to the total number of items a user has across all lists.
- **Failed and abandoned extractions.** Budget is consumed on attempt, so a user who submits a URL
  behind a login wall, or abandons the extracted recipe without saving it, still pays for it.
- **Extraction image uploads.** The extraction path may accept images without going through
  `ImageProcessingService`, meaning the 5 MB cap might not apply there. Needs verifying.
- **Shared content and ownership transfer.** Items shared with a user do not count against them; if
  ownership semantics ever change, the counting basis changes with it.
- **Concurrent creation.** Two simultaneous create requests from the same user near their cap could
  both pass a naive check.

## Integration points

### Backend

Each module below has a creation path that needs a limit check and an existing `*ExceptionHandler`
where a limit-exceeded response fits.

- `extraction` — `/extract/text`, `/extract/image`
- `recipes` — recipe creation
- `recipes.collections` — collection creation
- `shoppinglists` — list creation and item creation
- `planning` — meal plan creation (`MealPlanService`, currently reads `properties.maxOwnedPlans()`)

Storage for per-user limits and consumption counters is new; there is currently no user table.

### Mobile

- `features/recipe` — recipe list and create/edit screens (limit display, blocked creation)
- `features/extraction` — URL and image extraction screens (blocked action messaging)
- `features/shopping_list` — list creation and item entry
- `features/planning` — meal plan creation
- `features/collection` — collection creation

## Open questions

- **Flow limit reset semantics.** For a per-day limit, does the window reset at a fixed wall clock
  time (and in which timezone) or 24 hours after first/last use? Deferred to design.
- **Limit visibility in the UI.** Whether the recipe count appears as a persistent indicator
  ("42/100") or only as a warning near the cap; and whether the app fetches budgets in advance to
  disable actions pre-emptively or only reacts to server rejections. Deferred to design.
- **Extraction image size.** Whether images submitted to `/extract/image` go through the same 5 MB
  validation as recipe images.
- **Sharing amplification.** Accepted for now, but may warrant a recipient-side cap if abuse
  materialises.
