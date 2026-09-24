# ADR-0010: Account deletion is orchestrated over per-module purges, not a bulk delete

**Date:** 2026-09-04
**Status:** accepted
**Related ADRs:** [ADR-0006: Usage limits are owned end-to-end by a shared limits module](0006-shared-limits-module.md),
[ADR-0007: Sharing is owned end-to-end by a shared permissions module](0007-shared-permissions-module.md)

## Context

RecipAI has no users table. Identity is an email extracted from a JWT, and an account is only the sum of the
rows carrying that email plus everything reachable from them. Recipes, collections, shopping lists and meal
plans carry no owner column at all — ownership is resolved entirely through the `permissions` module's rows.
So "delete this account" cannot be a single operation on a single aggregate; it has to be assembled.

Assembling it looks deceptively easy, because most of the data hangs off foreign keys that cascade. The trap
is what the tables do not show. Deleting a recipe today publishes an event that `planning` listens for, which
rewrites every meal-plan entry pointing at that recipe — including entries in *other people's* plans — into a
placeholder carrying the recipe's name; there is no foreign key from an entry to a recipe, so without that
event those entries are left holding ids that resolve to nothing. Deleting a recipe also removes its images
from S3, which no cascade can do. Deleting any resource clears the permission rows and pending invites that
*third parties* hold on it, which are rows that do not carry the departing user's email at all. And deleting a
shopping list clears item-count usage that is recorded against the list's own identifier rather than against
the owner's email, so an email-keyed sweep of the usage table misses it.

A bulk purge — a class issuing delete-by-email and delete-by-owned-id statements across every table — would
be the smallest and fastest thing to write, and would have to re-implement every one of those behaviours by
hand, in one place, with nothing to keep it in step as the modules that own them change.

The system already has an answer for cross-module work of this kind. Modules expose a public facade and keep
their services package-private; nothing reaches into another module's repositories. ADR-0006 and ADR-0007
established that `limits` and `permissions` own their concerns end to end and are asked, never bypassed.

## Decision

A `settings` module owns the account-deletion endpoints and orchestrates the purge. Every module holding
account data exposes a `deleteAllByEmail` entry point on its facade and removes what it knows for that email,
asking `permissions` for ownership where it needs to.

`settings` holds no queries, no table knowledge and no notion of what any other module stores. Its only
domain knowledge is the order of the calls, which is stated in exactly one place: the resource modules first,
then `limits`, then `permissions` last — last because `permissions` is what tells every other module which
resources the email owns, so removing its rows first would blind them.

Each module's purge routes through that module's existing per-resource delete path rather than around it, so
the event, the S3 cleanup, the limits release and the third-party permission and invite clearing all happen
exactly as they do for a single deletion.

Transaction boundaries stay per module, matching every other delete in the system. There is no transaction
spanning the whole sequence.

## Alternatives considered

- **Bulk purge in one class** — fastest and least code, but concentrates four modules' storage details in one
  place, has no mechanism to stay in sync with them, and silently loses the recipe-deleted event, leaving other
  users' meal plans holding unresolvable recipe ids.
- **An `AccountDeleted` event each module listens for** — keeps the orchestrator free of all domain knowledge,
  but turns the ordering constraint into an invisible, unenforced convention, and nests the existing
  recipe-deleted event inside another event listener.
- **`permissions` driving deletion through per-type handlers modules register** — puts the walk where the
  ownership knowledge already lives, but widens the charter of the module every other module depends on, and
  adds an extension point with four entries to serve a single caller.

## Consequences

Every invariant already encoded in the per-resource delete paths holds for account deletion for free, and
keeps holding as those paths change. A module that later gains user-owned data has one obvious place to
implement its own removal, and the orchestration stays legible in a single sequence.

The cost is that deletion is many per-resource operations rather than a handful of statements, which is
acceptable for an operation a user performs once.

Ownership checking is not weakened by this. A module's purge iterates exactly the resources `permissions`
reports the email as owning, so each resource it deletes satisfies the owner check its normal delete path
enforces — including when an administrator triggers the purge, since that check is against the subject email
rather than the caller. What the purge entry point does not establish is who may ask for it: it takes its
subject as an argument instead of deriving it from the token, so that guard belongs to the endpoints in
`settings`, which admit either the caller acting on their own identity or a caller holding the admin authority.

Because the boundaries are per module, a failure part-way through leaves earlier modules purged and later ones
untouched, and nothing records that the account is half gone — no marker, no retry, no reconciliation job. An
account in that state is repaired by calling the deletion again, which is safe: every module's purge succeeds
against an email that has nothing left to remove.
