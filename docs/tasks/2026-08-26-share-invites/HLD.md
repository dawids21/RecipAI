# Share invites — High-level design

**Date:** 2026-08-26
**ADRs:** `docs/ADRs/0007-shared-permissions-module.md`, `docs/ADRs/0008-invite-label-snapshot.md`

## Summary

Sharing becomes a two-step handshake: entering an email creates a pending invite that grants nothing, and
the invitee must accept before a permission exists. The invite requirement is taken as the occasion to
collapse the four duplicated per-module permission implementations into one shared `permissions` module that
owns permissions, invites and the role predicates — so the handshake is written once and behaves identically
for recipes, collections, shopping lists and meal plans.

## Approach

### Chosen

**Unify sharing, with invites as a first-class state inside it.**

A new shared `permissions` module becomes the system of record for who may do what with a shareable
resource. It owns three things: the granted permissions that the four `*_permission` tables hold today, the
pending invites this task introduces, and the role predicates each module currently reimplements — the
`hasEditorRights()` / `hasOwnerRights()` questions. A module no longer inspects a role and decides for
itself; it asks the module whether this caller may perform this operation on this resource, and acts on the
answer.

Because invites and permissions live in one place, accepting is a local state change rather than a
cross-module handshake: there is no event to publish and no callback into a domain module to create the
permission. Pending is structurally distinct from granted, so no existing read path can leak access by
forgetting a filter — the property the whole feature rests on holds by construction rather than by audit.

**The boundary is drawn deliberately.** The module knows resource *types* as opaque keys and knows nothing
about what they mean. In particular it does not learn that recipes belong to collections: recipes' synthetic
`EDITOR` for a caller who can reach the recipe's collection is composition of two role answers, and
composition stays with the module that knows the rule. `recipes` asks twice — once for the recipe, once for
its collection — and combines the answers itself. This mirrors the domain-free boundary ADR-0006 established
for `limits`, one rung looser: this module answers "may this caller do this", `limits` only counts.

The module's data model and API shape are **not decided here** — whether permissions and invites share a
store, how resource references are keyed, and what the facade offers are task-design questions.

**What this gives up relative to A** (a pending flag on the existing permission rows, the runner-up): speed
and blast radius. A would have shipped materially sooner and touched only the queries that read permissions.
This choice instead migrates the system of record for ownership — which is also the input to `limits`' usage
recompute — and rewrites the access checks in all four modules, in exchange for removing the duplication
permanently and making the invite flow exist once instead of four times.

### Rejected alternatives

- **Pending state on the existing permission rows (A)** — the cheapest viable option and a real contender:
  the read paths needing a new filter are few and module-scoped. Rejected not on cost but on what it leaves
  behind — the four-fold duplication survives, the accept flow is written four times, and the feature buys
  no structural improvement at a moment when there is time to take one. Its fail-open failure mode (a missed
  filter silently granting unconsented access) was a secondary concern.
- **Four per-module invite tables (B)** — the same kind of duplication as A but more of it, plus four more
  tables alongside the four permission tables, and no natural home for the invitee's inherently
  cross-resource list.
- **An invites-only shared module (C)** — pays the cost of a new module without the benefit: authorization
  stays spread across four modules, and accepting needs cross-module machinery to create a permission in
  someone else's table. If a new module is warranted, it should own the whole concern.
- **Per-sharer consent instead of per-resource invites (E)** — one accept per relationship rather than per
  resource; less code and arguably a better experience for a household, but it answers a different question
  than the requirements ask and gives up per-resource control.
- **The wide reading of the chosen approach** — pulling recipes' collection-derived access into the shared
  module along with everything else. Rejected because it would make the module the place that knows how
  recipes relate to collections, giving it exactly the domain knowledge the boundary exists to keep out.

## Feature areas

### Shared `permissions` module (new)

**Key behaviors.**
- Records who holds what role on a resource, for every shareable resource type, as the single system of
  record — replacing the four per-module permission tables.
- Answers whether a given caller may perform a given operation on a given resource, so callers act on a
  decision rather than interpreting a role themselves.
- Holds a pending invite carrying the target email, the role to grant, who sent it, and a display label
  supplied by the inviting module (see ADR-0008). A pending invite confers no access of any kind.
- Grants the permission at the invite's role on accept, and destroys the invite on decline or cancel —
  leaving no trace either way.
- Answers, for one email across all resource types, what invites are waiting — the query behind the
  invitee's list and the in-app indicator.
- Refuses a second invite to an email that already has a pending invite for that resource, and refuses an
  invite to an email that already holds a permission on it.
- Keeps an invite alive when the sender loses their own access; the sender is recorded for display only.
- Removes every permission and every pending invite for a resource when the owning module reports it
  deleted.
- Treats resource types as opaque and holds no knowledge of what any of them mean.

### Permission migration and the four resource modules

**Key behaviors.**
- Existing permission rows in all four tables move into the new module's ownership with no change in
  meaning: everyone who has access today keeps it, at the same role, with no invite involved.
- Each module's share endpoint stops writing a permission and creates an invite instead, accepting a role
  that the backend stores and honours on accept.
- Each module's access checks and role predicates are replaced by asking the module, including the
  owner-only guards on delete.
- `recipes` keeps its collection-derived access rule, composing two role answers itself.
- Unshare, who may share, and every other aspect of already-granted access are unchanged in behaviour.
- Each module's delete path reports the deletion so permissions and pending invites are cleaned up.
- All four ship in one release; the migration is sequenced internally — establish the module and move
  shopping lists first as the simple case, then recipes as the hard one, then collections and meal plans.

### Quota accounting

**Key behaviors.**
- `limits`' usage recompute reads ownership from the new system of record instead of counting `OWNER` rows
  across four permission tables.
- Neither a pending invite nor an accepted share consumes anyone's allowance; only ownership does, exactly
  as today.

### Invitee-facing surface (mobile)

**Key behaviors.**
- An in-app indicator shows that invites are waiting, and clears once none remain. It is the only discovery
  mechanism — nothing is sent outside the app.
- Opening it lists every pending invite across all four resource types, each showing what it is, its stored
  label, and who sent it.
- The invitee accepts or declines each invite. Accepting makes the resource appear and behave exactly as a
  shared resource does today; declining removes the invite from both sides.
- Until answered, the resource is absent from every list and unreadable.

### Sharer-facing surface (mobile)

**Key behaviors.**
- The shared-users view of each resource lists pending invites alongside users who already have access,
  visibly distinct from them.
- A pending invite can be cancelled, after which it disappears for the invitee too.
- All four features reach this through the one shared sharing dialog they already funnel through.
- The client always sends `EDITOR`; there is no role picker.

## Out of scope

- **The module's data model and API shape** — deliberately left to task-design or a task of its own.
- **Materialising collection-derived recipe access as real permission rows.** It would remove recipes'
  special case entirely, but it is a behavioural change to how collection access propagates and belongs in
  its own task.
- **Extending the module beyond the four current resource types**, and any new role beyond `OWNER` and
  `EDITOR`.
- **A user or account registry.** Invites remain keyed on email, and an invite to an address that has never
  signed up waits until that person does.
- Everything the requirements list as an anti-requirement: expiry, out-of-band notification, a role picker,
  sender blocking, decline reasons, decline visibility, and retroactive consent for permissions existing at
  rollout.

## Open questions

- **Does the migration run as a data migration or a recompute?** The permission tables are the system of
  record for ownership and the input to `limits`' usage rebuild; whether the new store is populated by
  copying rows or by a repeatable recompute in the `limits` style affects how the cutover is verified.
- **What the module answers for a resource type it has never seen a permission for** — a genuine "no
  access", or an error signalling a wiring mistake.
- **Whether the four modules' identical `UserRole`, `SharedUserDto` and `Share*Request` types collapse into
  the module's own public types**, and whether that changes any endpoint's response shape.
- **The path naming inconsistency** — recipes expose `/shared_users` while the other three expose `/users`.
  Whether to unify it while touching all four, and what that costs the mobile client.
- **Where the invitee's surface and its indicator live in the app shell** — the bottom navigation has three
  items and there is no existing badge or indicator pattern anywhere in the app to copy.
- **Whether the invitee's list needs its own refresh trigger** or is loaded on the same schedule as the
  indicator's count.
