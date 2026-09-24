# Account deletion

**Date:** 2026-09-04
**Type:** feature

## Summary

Let a user delete their RecipAI account and everything belonging to it from inside the app, and give an
administrator an endpoint to delete any account by email on request.

## Context

Google Play requires apps that support account creation to offer in-app account deletion. RecipAI has no
such path today: a user can sign out, but nothing removes their identity or their data. This is
groundwork for publishing to Play; there is no deadline.

The second driver is operational. Deletion requests also arrive by email, and the person handling them
needs a way to delete someone else's account without touching the database by hand.

## Requirements

### Mobile — settings screen

- A settings screen, reached from the Main Screen overflow menu.
- It becomes the home for **Logout** and **Send logs**, both moved off the overflow menu, alongside the
  new **Delete account** action.

### Mobile — deletion flow

1. The user taps **Delete account**.
2. A confirmation dialog opens, making clear the deletion is immediate and irreversible.
3. On confirmation the user re-authenticates with Google.
4. Deletion runs.
5. The user is taken to the Login Screen.

### Backend — self-service deletion

An endpoint the signed-in caller invokes to delete their own account. It removes, for that email:

- every recipe they own, together with its images in S3
- every recipes collection they own
- every shopping list they own
- every meal plan they own
- every `resource_permission` row carrying their email — both the OWNER rows on their own resources and
  the EDITOR rows granting them access to other people's
- every `resource_invite` row carrying their email, in both directions: invites sent to them and invites
  they sent to others
- their `limit_usage` rows
- their Firebase identity

Deleting a resource the user owns drops it for everyone: editors lose access, and nothing is transferred
to another owner.

### Backend — admin deletion

An endpoint that deletes any account, named by its email address, performing exactly the same removal as
self-service deletion including the Firebase identity. The caller is an administrator acting under their
own Google identity.

## Anti-requirements

Explicitly out of scope:

- **Data export** before deletion — the user gets no copy of their recipes.
- **Undo, grace period, or scheduled deletion** — the deletion is immediate and final.
- **Soft delete / deactivate** — there is no state between "account exists" and "account gone".
- **Retention of anything** for records, analytics, or audit.
- **Ownership transfer** — a shared resource whose owner leaves is deleted, not handed to an editor.
- **Local device cleanup** — the sqflite shopping-list store and the log files on the device are left in
  place. Signing out is the only thing that happens on the device.
- **Notifying editors** that a resource they had access to has been deleted.

## Constraints & assumptions

- Identity is email-keyed throughout the backend. There is no users table, so "the account" is only the
  sum of the rows carrying that email; deletion has to be assembled from each module's own data.
- The backend has no notion of an administrator today — no roles, no privileged callers. Every caller is
  an email extracted from a JWT.
- Deleting a Firebase identity requires a recent sign-in for the account being deleted, which is why
  re-authentication is a requirement of the user-facing flow rather than optional friction.
- The backend currently only validates Firebase tokens; it never calls Firebase. Deleting an identity
  makes Firebase an outbound integration for the first time.
- Assumed: an account's entire footprint is the data listed under Requirements. Anything holding a user's
  email that is not in that list has been missed.

## Acceptance criteria

- [ ] A settings screen is reachable from the Main Screen overflow menu and offers Logout, Send logs, and
      Delete account.
- [ ] Logout and Send logs no longer appear in the Main Screen overflow menu.
- [ ] Delete account opens a confirmation dialog; dismissing it deletes nothing.
- [ ] Confirming requires a successful Google re-authentication before deletion runs.
- [ ] After deletion the app is on the Login Screen and the session is gone.
- [ ] After a self-deletion, the account's recipes, recipe images in S3, collections, shopping lists, meal
      plans, permission rows (owned and editor), invites (sent and received), and limit usage rows are all
      gone, and the Firebase identity no longer exists.
- [ ] A resource that the deleted user owned and had shared is gone for its editors too.
- [ ] The admin endpoint, given an email, produces the same end state for that account.
- [ ] Signing in again with the same Google account yields a fresh, empty account.

## Edge cases

- **Owner of shared resources deletes their account** — the resources are dropped and editors silently
  lose access. No warning, no transfer.
- **Editor deletes their account** — resources owned by others survive; only the deleted user's EDITOR
  rows disappear.
- **Pending invites in flight** — invites the user sent and invites addressed to them are both removed,
  so neither side is left with an invite pointing at a non-existent account.
- **Partial deletion** — if the database rows are removed but S3 objects or the Firebase identity are not,
  the orphans are accepted. No compensation, no retry, no reconciliation job is required.
- **A second device still signed in** — the deleted Firebase identity makes its auth state fail, and the
  app signs the user out on its own. No push or server-side session invalidation is expected.
- **Admin deletes an account whose owner is actively using the app** — treated the same as the case above.

## Integration points

Backend:

- `recipes` — recipes owned by the account, and `recipes.collections`
- `recipes.images` — image rows plus the S3 objects behind them, through the `config.s3` `S3Service` seam
- `shoppinglists` — lists owned by the account and their items
- `planning` — meal plans owned by the account and their entries
- `permissions` — `resource_permission` and `resource_invite` rows for the email, in both roles and both
  invite directions; `PermissionsFacade.resourceDeleted` is the existing per-resource cleanup path
- `limits` — `limit_usage` rows for the subject
- `config.security` — the caller's identity, and wherever the administrator check ends up living
- Firebase — new outbound call to delete the identity

Mobile:

- `features/auth` — re-authentication before deletion, and the sign-out that follows it
- `core` Main Screen — the overflow menu loses Logout and Send logs
- New settings screen and its `AppRoute` entry
- The `recipai/share` platform channel used by Send logs moves with it

## Open questions

- How is an email recognised as having admin rights? Deferred to design.
- Does the admin endpoint require anything beyond the caller's valid JWT, or is a re-authentication or
  second factor wanted there too?
- What does the app show if the user cancels the re-authentication prompt or it fails — silent return to
  settings, or an explicit message?
- If the backend deletion succeeds but the Firebase identity deletion fails, does the user still land on
  the Login Screen as though it worked?
