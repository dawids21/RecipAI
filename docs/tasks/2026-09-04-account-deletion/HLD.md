# Account deletion — High-level design

**Date:** 2026-09-04
**ADRs:** docs/ADRs/0009-client-side-firebase-identity-deletion.md,
docs/ADRs/0010-account-deletion-orchestrated-over-per-module-purges.md,
docs/ADRs/0011-admin-authority-from-config-allowlist.md

## Summary

Give a signed-in user an in-app path to delete their account and everything belonging to it, and give an
administrator an endpoint that does the same for any email. A new backend `settings` module orchestrates the
purge by calling a `deleteAllByEmail` entry point on each module that holds account data; the Firebase identity
is deleted by the mobile client after re-authentication, so the backend never calls Firebase.

## Approach

### Chosen

**Orchestrated per-module purge, with client-side identity deletion.**

The backend gains a `settings` module holding both endpoints — the self-service one, which derives its subject
from the caller's own token, and the admin one, which takes an email. Both run the same purge. `settings` owns
the sequence and nothing else: it holds no queries, no table knowledge and no notion of what any other module
stores. Each module that holds account data exposes `deleteAllByEmail` on its facade and removes what it knows,
asking `permissions` for ownership where it needs to. Because `permissions` is what tells every other module
which resources an email owns, it is purged last; `limits` follows the resource modules, whose own deletes
release usage as they go.

Each module's purge routes through its existing per-resource delete path rather than around it. That is what
keeps the behaviour that is not visible in the tables: the `RecipeDeleted` event that rewrites other people's
meal-plan entries into named placeholders, the S3 prefix delete behind recipe images, the limits release, and
`PermissionsFacade.resourceDeleted` clearing the EDITOR rows and pending invites that third parties hold on a
resource being deleted. Transaction boundaries stay per module, as they are today.

The Firebase identity is deleted by the mobile client with the Firebase client SDK, after the re-authentication
the flow already requires. The app purges the backend first and deletes the identity second, so a failure can
only ever leave "data gone, identity alive" — an account that resolves to a fresh empty one on the next sign-in.
The admin endpoint therefore deletes data only; the operator removes the identity from the Firebase console.

Administrators are recognised by a configured allowlist of emails, turned into an authority so the admin path is
a matcher in the existing security chain.

**What this gives up.** Deletion is many per-resource operations rather than a handful of statements. A failure
part-way through the sequence leaves an account partly purged with nothing recording that — no retry, no
reconciliation. And the admin endpoint no longer produces the complete end state on its own; it depends on an
operator finishing the job by hand.

The per-resource owner check is not among the costs. Each module's purge iterates exactly the resources
`permissions` reports the email as owning, so every resource it then deletes passes that check by construction,
in the admin flow as much as the self-service one — the check is against the subject, not the caller. What the
purge entry point does not decide is who may ask for it; that guard is the endpoint's, and it is the caller's
own identity in one case and the admin authority in the other.

### Rejected alternatives

- **Server-side Firebase identity deletion** — the only thing a service-account credential bought was the admin
  path's identity deletion, and that did not justify a long-lived key on the VPS plus either the Firebase Admin
  SDK's dependency weight or a hand-rolled REST client. See ADR-0009.
- **Direct bulk purge from one class** — the fastest and least code, but it re-implements four modules' cleanup
  semantics in one place with nothing keeping it in sync, and it silently drops the `RecipeDeleted` event, so
  other users' meal plans would keep entries pointing at recipe ids that no longer resolve.
- **`AccountDeleted` event with each module purging itself** — keeps the modules decoupled, but the ordering
  constraint (ownership lives in `permissions`, so it must go last) is real and would become invisible and
  unenforced, and it nests the existing `RecipeDeleted` event inside another listener.
- **`permissions` driving deletion through registered per-type handlers** — puts the walk where the ownership
  knowledge already lives, but widens the charter of the module everything else depends on, and adds an
  extension point with four entries, to serve one caller.
- **Firebase custom claims for admin rights** — grants and revocations only take effect when a token refreshes,
  there is no console UI for claims, and the Admin SDK that sets them is exactly the dependency the identity
  decision removed. See ADR-0011.

## Feature areas

### Backend — `settings` module

**Key behaviors.**
- Exposes a self-service deletion endpoint whose subject is the caller's own identity, taken from the token the
  way every other controller takes it.
- Exposes an admin deletion endpoint that names its subject by email and is reachable only by a caller holding
  the admin authority; every other caller is refused.
- Both endpoints run the same purge against one email.
- Owns the order in which the modules are purged, and is the only place that order is stated: the resource
  modules, then `limits`, then `permissions` last.
- Holds no queries and no knowledge of any other module's storage — it calls facades only.
- Purging an email with no data anywhere succeeds rather than failing.

### Backend — per-module account purge

**Key behaviors.**
- `recipes` removes every recipe the email owns, each through the module's own delete path, so the images and
  their S3 objects go, the recipe-deleted event fires, and per-recipe permissions and invites are cleared.
- `recipes.collections` removes every collection the email owns; recipes belonging to other people that sat in
  one are detached rather than deleted.
- `shoppinglists` removes every list the email owns together with its items, and the per-list item usage that
  is counted against the list rather than the owner.
- `planning` removes every meal plan the email owns together with its entries.
- `limits` removes the recorded usage for the email across every resource counted against them, and any
  per-user quota override held for that email, so a re-registered account starts from the defaults.
- `permissions` removes every row carrying the email — the rights they hold on other people's resources, any
  residual ownership rows, invites addressed to them, and invites they sent to others.
- Each module decides for itself what belongs to the email; none of them learns anything about another module.
- Each purge is one transaction within its own module. A failure part-way through the sequence leaves earlier
  modules purged and later ones untouched, with nothing recording the partial state.

### Backend — administrator identification

**Key behaviors.**
- The set of administrator emails is configuration, supplied by the environment in production and never
  committed.
- A caller whose token carries an allowlisted email is granted an admin authority; every other caller has none,
  as today.
- The admin endpoint is guarded by that authority in the existing security chain, leaving the deny-by-default
  posture intact — a non-admin caller is refused rather than told the route exists.
- The dev profile can name an administrator among its synthetic callers, so the admin endpoint is reachable in
  the integration suite without any Firebase involvement.
- Changing the administrator set is a configuration change and takes effect on restart.

### Mobile — settings screen

**Key behaviors.**
- A settings screen is reachable from the Main Screen overflow menu.
- It hosts Logout, Send logs and Delete account.
- Logout and Send logs leave the overflow menu; the logout confirmation and the log-sharing platform channel
  move with them unchanged.

### Mobile — account deletion flow

**Key behaviors.**
- Delete account opens a confirmation dialog stating that deletion is immediate and irreversible; dismissing it
  deletes nothing.
- Confirming starts a Google re-authentication. Cancelling it returns to the settings screen silently; a
  re-authentication that fails for any other reason shows a message, and nothing has been deleted either way.
- After a successful re-authentication the app calls the backend deletion endpoint, and only then deletes its
  own Firebase identity. The order is deliberate: the reverse can leave data behind that a re-registration of
  the same Google account would inherit, because the backend is keyed by email.
- On success the app signs out and lands on the Login Screen.
- If the identity deletion fails after the data is gone, the app shows a message saying something went wrong
  and asking the user to contact support, then signs out and lands on the Login Screen anyway — the account's
  data is already irrecoverable and there is nothing for a retry to achieve.
- The device keeps its local shopping-list store, log files and preferences; signing out is all that happens
  locally.

## Out of scope

Beyond the requirements' anti-requirements, this design also leaves out:

- **Any Firebase call from the backend** — no Admin SDK, no service account, no credential on the VPS.
- **Scrubbing emails from backend logs.** Grants, invites, shares and unshares log emails at INFO, and those
  lines are the one remaining record of the address after a successful deletion.
- **Any marker, retry or reconciliation for a partly purged account.**
- **Invalidating tokens already issued.** An ID token minted before deletion stays valid until it expires, which
  is the same mechanism behind the "second device still signed in" case the requirements already accept.
- **Managing administrators at runtime** — no UI, no roles table, no endpoint.

## Assumptions

- **Assumption:** if the backend purge itself fails, the app shows a message and leaves the user on the settings
  screen, still signed in, free to try again — **why it matters:** nothing irrecoverable has happened at that
  point, so this is the only step in the flow where a retry is meaningful; treating it like the post-deletion
  failure would sign the user out of an account that still exists.
- **Assumption:** the operator finishing an admin deletion by removing the identity in the Firebase console is
  acceptable at the expected volume — **why it matters:** if requests ever arrive in bulk, the manual step is
  the part that does not scale, and the decision to keep Firebase out of the backend would need revisiting.

## Open questions

- In what order are recipes and collections purged? Deleting a collection detaches the recipes in it, so
  purging collections first churns rows that are about to be deleted anyway.
- Removing the invites a user sent scans `resource_invite`, which is indexed on the recipient and on the
  resource but not on the sender. Does this purge justify an index?
- Where does the admin endpoint sit in the URL space, and how does it fit the security chain's existing
  matchers — `/users/**` is already an authenticated matcher with no controller behind it.
- Which synthetic dev-profile caller is the administrator for the integration suite?
- Does the mobile settings screen need a service of its own, or does the screen sit directly on the existing
  auth service plus a repository for the deletion call?
