# ADR-0007: Sharing is owned end-to-end by a shared permissions module, with role composition left to the composing module

**Date:** 2026-08-26
**Status:** proposed
**Related ADRs:** [ADR-0006](0006-shared-limits-module.md)

## Context

RecipAI has four shareable resource types — recipes, recipes collections, shopping lists and meal plans —
and sharing is implemented four separate times, once per module, with no shared abstraction on the backend.
Each module owns a structurally identical `*_permission` table keyed on `(email, resource_id)` with a role
of `OWNER` or `EDITOR`, its own copy of the `UserRole` enum, its own shared-user DTO and request records,
and its own copy of the share / unshare / list-shared-users logic. Permissions are keyed on email string;
there is no users table anywhere in the backend, because identity lives entirely in Firebase Authentication
and the backend is a pure resource server that trusts and stores the `email` claim.

Enforcement is equally scattered. There is no method security and no resource-level filter: every service
method re-resolves the caller's permission itself, and the four modules do it in three slightly different
shapes. Recipes uniquely computes a fallback — a caller who can reach a recipe's collection gets a synthetic
`EDITOR` on the recipe, never materialised as a row. The permission tables are load-bearing beyond access
control: the `limits` module rebuilds recorded usage by counting `role = 'OWNER'` rows across all four.

Two forces made this worth changing now rather than later.

**Sharing is becoming a two-step handshake.** Ahead of a public release, sharing must stop granting access
immediately: entering an email creates a pending invite that grants nothing, and the invitee accepts or
declines. Pending state must be invisible to every list query, every access check, and the ownership count
`limits` depends on. Built on the existing structure, that handshake — and the accept, decline and cancel
flows behind it — would be written four times, and its correctness would rest on no read path anywhere
forgetting to exclude pending rows. A single missed filter grants access to someone who never consented,
which is precisely the failure the feature exists to prevent, and it is silent.

**The duplication has no owner.** Nothing about sharing is shared on the backend today, not even the role
enum, so "all four resources behave identically" is held up by discipline alone. Adding a second
four-fold-duplicated concern on top of the first entrenches that.

The counter-force is blast radius. The permission tables are the system of record for ownership; moving them
moves the input to quota accounting at the same time. Schedule was not a constraint here, so the choice was
made on structure rather than speed.

## Decision

Introduce a shared `permissions` module that becomes the **system of record for who may do what with a
shareable resource**. It owns three things:

- **Granted permissions**, replacing the four per-module permission tables.
- **Pending invites** — an invite carries the target email, the role to grant on accept, who sent it, and a
  display label supplied by the inviting module. A pending invite confers no access of any kind. Accepting
  grants the permission at the invite's role; declining or cancelling destroys the invite, leaving no trace.
- **The role predicates** — the `hasEditorRights()` / `hasOwnerRights()` questions each module reimplements
  today. Callers ask whether this caller may perform this operation on this resource and act on the answer;
  they no longer inspect a role and decide for themselves.

Because invites and permissions live in one store, accepting is a local state change: no event is published
and nothing calls back into a domain module to create the permission.

**The module knows resource types as opaque keys and holds no domain knowledge.** It does not learn what a
recipe is, and specifically it does not learn that recipes belong to collections. Recipes' collection-derived
access is *composition of two role answers*, and composition stays with the module that knows the rule:
`recipes` asks about the recipe and about its collection and combines the answers itself. This is the same
domain-free boundary ADR-0006 draws for `limits`, one rung looser — this module decides whether an operation
is permitted, where `limits` only counts.

Existing permission rows migrate with no change in meaning: everyone who has access at rollout keeps it, at
the same role, with no invite involved. `limits`' usage recompute reads ownership from the new store instead
of counting `OWNER` rows across four tables. All four resource types cut over in one release.

The module's internal data model and the exact shape of its API are deliberately left open by this decision.

## Alternatives considered

- **A pending state on the existing permission rows** — add a pending/granted state to each of the four
  tables and teach every read path, plus the `limits` recompute, to exclude pending. The cheapest viable
  option, and the read paths needing a filter are few and module-scoped. Rejected not on cost but on what it
  leaves behind: the four-fold duplication survives, the accept flow is written four times, and the change
  buys no structural improvement at a moment when there was time to take one. Its fail-open failure mode was
  a secondary concern.
- **Four per-module invite tables** — mirror the existing duplication, giving each module its own invite
  table and endpoints. The same kind of duplication as above but more of it, four new tables alongside the
  four permission tables, and no natural home for the invitee's inherently cross-resource list of pending
  invites.
- **An invites-only shared module** — own invites centrally but leave permissions where they are. Pays the
  cost of a new module without the benefit: authorization stays spread across four modules, and accepting
  needs cross-module machinery — an event or a callback — to create a permission in another module's table.
- **Per-sharer consent instead of per-resource invites** — the invitee approves a *person* once, after which
  that person's shares land directly. Less code and a better experience for a household sharing many
  recipes, but coarser consent and a different feature than the one specified.
- **Pulling recipes' collection-derived access into the shared module** — would remove the last special case
  and make the module answer every access question outright. Rejected because it would make the module the
  place that knows how recipes relate to collections, which is exactly the domain knowledge the boundary
  keeps out.

## Consequences

- There is one place that answers "may this caller do this", one implementation of sharing, and one
  implementation of the invite handshake. All four resource types behave identically by construction rather
  than by discipline.
- **A pending invite cannot leak access**, because pending is not a permission and no read path can mistake
  it for one. This property survives future changes without requiring anyone to remember a filter.
- **Ownership moves.** The permission tables stop being the system of record, and `limits`' usage recompute
  is repointed at the same time. A defect in the migration affects access control and quota accounting
  together — the principal cost of this decision, accepted because there was time to do it carefully.
- Four modules gain a permanent dependency on the `permissions` module, and a bug in it breaks access to
  everything. As with `limits`, the module must stay free of domain knowledge for that edge to remain
  acceptable, and an architecture test rather than discipline is the way to hold that line.
- **Recipes keeps a special case.** It asks twice and composes, so "what may this user do" is centralised
  for three resource types and composed by the caller for the fourth. This was chosen over giving the module
  domain knowledge; materialising collection-derived access as real rows would remove it, but that is a
  behavioural change to how collection access propagates and belongs in its own task.
- The four duplicated role enums, shared-user DTOs and share-request records become collapsible into the
  module's own types, which may change endpoint response shapes and therefore the mobile client.
- Adding a shareable resource type in future means registering a type key and asking the module — no new
  permission table, no new sharing logic, and invites work for it immediately.
- Nothing here forecloses richer authorization later — more roles, per-operation grants, or group
  subjects — behind the same module boundary.
