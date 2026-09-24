# Account deletion — Tasks

**Date:** 2026-09-04

## Summary

- **T1:** Self-service account deletion endpoint and the per-module purge behind it
- **T2:** Admin deletion endpoint and the config-driven admin authority
- **T3:** Mobile settings screen hosting Logout and Send logs
- **T4:** Mobile account deletion flow

## Cross-task notes

- **T1 is the backbone.** It builds the `settings` module, every module's `deleteAllByEmail` entry
  point and the purge order. T2 reuses that purge unchanged; T4 calls the endpoint it exposes.
- **T3 is independent of the backend** and can run in parallel with T1 and T2. It is the only mobile
  task with no backend dependency, so it is the natural filler while T1 is in review.
- **T1 is the largest task by some margin** — six modules gain a purge entry point plus a new module
  and its integration suite. It is kept whole because any smaller slice leaves an account half
  deleted, which is a worse end state than not deleting at all and cannot be verified at a slice
  boundary. If it proves too large in implementation planning, the split to reach for is
  `permissions` + `limits` first (they have no dependants), never a split that ships a partial purge.
- **Several HLD open questions land in T1 and T2** — recipe/collection purge order and the
  `resource_invite` sender index in T1, the admin endpoint's place in the URL space and the dev-profile
  administrator in T2. None block the breakdown; all are task-design decisions.

---

## T1: Self-service account deletion endpoint

**User-visible outcome**

An API consumer holding a valid token can call one endpoint and have every recipe, collection,
shopping list, meal plan, permission row, invite and limit-usage row belonging to their email
removed, leaving a sign-in with the same account resolving to a fresh empty one.

**Scope**

- The `settings` module and its self-service endpoint, per `HLD.md` > Feature areas > Backend —
  `settings` module: subject taken from the caller's own token, purge order owned here, facades only.
- A `deleteAllByEmail` entry point on `recipes`, `recipes.collections`, `shoppinglists`, `planning`,
  `limits` and `permissions`, each routing through the module's existing per-resource delete path so
  the `RecipeDeleted` event, the S3 prefix delete, the limits release and
  `PermissionsFacade.resourceDeleted` all still fire.
- The purge succeeding for an email that owns nothing.
- Integration coverage of the end state, including a resource shared with a third party.

**Out of scope**

- The admin endpoint and any notion of an administrator — covered in T2.
- Firebase identity deletion — client-side, covered in T4.
- Any marker, retry or reconciliation for a partly purged account — out of scope in `HLD.md`.
- Scrubbing the email from existing INFO log lines — out of scope in `HLD.md`.

**Depends on:** none

**HLD references**

- `HLD.md` > Feature areas > Backend — `settings` module
- `HLD.md` > Feature areas > Backend — per-module account purge
- `docs/ADRs/0010-account-deletion-orchestrated-over-per-module-purges.md`

**How to verify**

Against a local `dev`-profile backend: as `Bearer alice`, create a recipe, a collection, a shopping
list and a meal plan, and share the recipe with `bob@local.test`. Then call the self-service deletion
endpoint as `Bearer alice`. Afterwards:

- `curl -sS -H "Authorization: Bearer alice" localhost:8080/recipes` returns an empty list, and the
  same for `/collections`, `/shopping-lists` and `/meal-plans`
- `curl -sS -H "Authorization: Bearer bob" localhost:8080/invites` no longer shows alice's invite,
  and the shared recipe is gone for bob too
- alice's `limit_usage` rows are gone — a freshly created recipe as `Bearer alice` counts from zero

**Risks / unknowns**

- Purging collections before recipes churns rows that are about to be deleted anyway; the order
  between those two is an open question in `HLD.md`.
- Removing invites the user *sent* scans `resource_invite` on an unindexed column — task design
  decides whether this purge justifies an index.

---

## T2: Admin deletion endpoint

**User-visible outcome**

An operator whose email is in the configured allowlist can delete any account's data by naming its
email; every other caller is refused as though the route did not exist.

**Scope**

- Administrator identification per `HLD.md` > Feature areas > Backend — administrator identification:
  allowlist from configuration, supplied by the environment in production, turned into an authority.
- The admin endpoint in `settings`, guarded by that authority in the existing security chain, running
  the same purge as T1 against a named email.
- A dev-profile synthetic caller carrying the admin authority, so the endpoint is reachable in the
  integration suite without Firebase.

**Out of scope**

- Firebase identity deletion for the admin path — the operator removes it from the console by hand,
  per `HLD.md` > Approach > Chosen.
- Runtime administrator management — no UI, no roles table, out of scope in `HLD.md`.
- Any change to the purge itself — it is T1's, reused unchanged.

**Depends on:** T1

**HLD references**

- `HLD.md` > Feature areas > Backend — administrator identification
- `docs/ADRs/0011-admin-authority-from-config-allowlist.md`

**How to verify**

With an allowlisted dev-profile caller, seed data as `Bearer alice`, then call the admin endpoint as
that caller naming `alice@local.test`; alice's data is gone exactly as in T1's checks. The same call
as `Bearer bob` returns 403, and the failing call leaves alice's data untouched.

**Risks / unknowns**

- Where the endpoint sits in the URL space is open in `HLD.md`; `/users/**` is already an
  authenticated matcher with no controller behind it, so the choice interacts with the existing chain.

---

## T3: Mobile settings screen

**User-visible outcome**

A user can open a settings screen from the Main Screen overflow menu and log out or send logs from
there instead of from the menu.

**Scope**

- The settings screen and its `AppRoute` entry, per `HLD.md` > Feature areas > Mobile — settings screen.
- Logout and Send logs moved off the overflow menu onto it, with the logout confirmation and the
  `recipai/share` platform channel carried over unchanged.

**Out of scope**

- Delete account, its dialog, the re-authentication and the deletion calls — covered in T4.
- Any change to what Logout or Send logs actually do.

**Depends on:** none

**HLD references**

- `HLD.md` > Feature areas > Mobile — settings screen

**How to verify**

In the running app: the Main Screen overflow menu no longer offers Logout or Send logs but does lead
to Settings; from Settings, Logout still prompts for confirmation and returns to the Login Screen, and
Send logs still opens the platform share sheet with a log file.

---

## T4: Mobile account deletion flow

**User-visible outcome**

A user can delete their account from the settings screen — confirm, re-authenticate with Google, and
land on the Login Screen with their data and Firebase identity gone.

**Scope**

- Delete account on the settings screen, its confirmation dialog, and the Google re-authentication,
  per `HLD.md` > Feature areas > Mobile — account deletion flow.
- The backend purge call followed by the client-side Firebase identity deletion, in that order, then
  sign-out to the Login Screen.
- The three failure paths the HLD names: cancelled re-authentication returns silently; a failed
  re-authentication or a failed backend purge shows a message and leaves the user signed in on the
  settings screen; a failed identity deletion after a successful purge shows a contact-support message
  and signs out anyway.

**Out of scope**

- Local device cleanup — the sqflite store, log files and preferences stay, per `HLD.md`.
- Any backend change — T1 and T2 own the endpoints.
- Signalling other signed-in devices — they fail their own auth refresh, out of scope in `HLD.md`.

**Depends on:** T1, T3

**HLD references**

- `HLD.md` > Feature areas > Mobile — account deletion flow
- `docs/ADRs/0009-client-side-firebase-identity-deletion.md`

**How to verify**

In the running app against a backend built from T1: create a recipe, then Settings → Delete account →
confirm → complete the Google re-authentication. The app lands on the Login Screen. Signing in again
with the same Google account succeeds and shows an empty account. Repeating the flow and dismissing
the confirmation dialog, or cancelling the Google prompt, leaves the recipe in place.

**Risks / unknowns**

- Whether the screen needs a service of its own or sits on the existing auth service plus a deletion
  repository is open in `HLD.md`.
- The order — backend first, identity second — is load-bearing; reversing it can leave data a
  re-registration of the same email would inherit.
