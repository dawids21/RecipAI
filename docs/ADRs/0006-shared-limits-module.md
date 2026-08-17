# ADR-0006: Usage limits are owned end-to-end by a shared limits module keyed by an opaque subject

**Date:** 2026-08-13
**Status:** proposed
**Related ADRs:** None

## Context

RecipAI is published to the app store, so unknown users can reach the paths that cost money: AI
extraction calls are billed per request, and stored recipe images consume object storage and
bandwidth. The application needs per-user caps on the resources that drive those costs — AI
extractions, and the count of recipes, collections, shopping lists, meal plans and items per list a
user holds.

The limits that already exist are ad-hoc and global. `planning` reads an owner plan limit from
application configuration, which is bound at startup and therefore cannot be changed without a
restart. `recipes.images` hardcodes a per-recipe image count and a per-image size in the code. None
vary per user, and extraction has no limit at all. The operational requirement is that a limit can be
raised or lowered by editing the database directly, taking effect on the next request, with no admin
interface and no redeploy.

Two properties of the requirement shape the design more than anything else:

**Any resource can be configured as either kind, per user.** A limit is either a *stock* cap — the
maximum held at one time — or a *flow* cap — the maximum consumed per period, including the degenerate
"N ever" with no period. That choice is per user per resource: one user may have "5 extractions ever"
and another "2 per day", and the same freedom applies to recipes. This matters because a
flow-configured resource cannot be answered by counting what exists — consumed extractions leave
nothing to count — so consumption has to be recorded no matter what. The question is therefore not
whether to record usage, but whether to *also* maintain a second, derived path for the stock case.

**Not every limit is scoped to a user.** The per-list item cap counts items within one shopping list,
while every other cap counts things a user owns. A model keyed strictly by user cannot express both.

The application is a single Spring Boot container against a single Postgres, so a shared store is
exactly as available as the data it guards; availability is not a real concern here. Coupling and
blast radius are. The codebase enforces module boundaries with package-private internals and public
facades for cross-module access, and every module that consults a shared limits component gains a
permanent dependency edge on it.

## Decision

Introduce a shared `limits` module that owns **configuration, usage tracking and verification** for
every limited resource. Modules that perform limited operations ask it for permission and tell it when
a held unit is released; they never compute a count, never read a limit value, and never interpret
stock-versus-flow semantics.

The module is generic. A resource is an opaque key the module defines, and a limit attaches to an
opaque **subject** supplied by the caller — the user's identity for owner-scoped resources, the list's
identity for the per-list item cap. The module never learns what it is counting.

**All resources are tracked the same way.** Usage is recorded, not derived: creation consumes,
deletion releases, and a flow-configured resource simply never releases. There is no second derived
path for stock caps.

**Reservation precedes the work and is indivisible.** The check and the reservation are one step, so
two concurrent requests from one subject near its cap cannot both be admitted. For extraction, budget
is taken before the AI provider is called and is never refunded — a failed, garbage or abandoned
extraction still consumes it, because the cost is incurred on attempt.

**Configuration resolution is override-then-default,** with the absence of an override meaning the
default. No configuration is cached; the stored value is read per check, so an edit takes effect on the
next request.

**Refusals return HTTP 429** carrying structured information about the resource and the subject's
standing, not prose alone. This replaces the 409 that `planning` returns today.

**The module exposes a re-runnable recompute** that rebuilds usage for a resource from the owning
module's authoritative data. It is both the rollout seed and the repair for divergence.

`planning` migrates onto this mechanism and loses its own configured limit rather than keeping it
alongside.

## Alternatives considered

- **Gatekeeper with caller-supplied counts** — the module owns the rule and the flow records, but each
  caller passes its own current holdings for stock resources. Structurally immune to drift, needs no
  rollout seed, and makes switching a subject between stock and flow free. Rejected because the ask
  becomes non-uniform and the passed count is a contract the module cannot validate: a call site that
  counts with the wrong ownership predicate produces a silently wrong answer.
- **Thin kernel — the module supplies limit values only, each module verifies itself.** Lowest coupling
  and smallest blast radius. Rejected because a per-user-configurable kind forces window resolution,
  period bookkeeping and a concurrency guard into all five calling modules — five copies of the part
  most likely to be got subtly wrong — and it leaves no single place to answer "what is this user
  allowed to do".
- **Counting-port inversion** — modules register a counting capability the limits module calls back
  into, giving uniform call sites with derived usage. Rejected because it inverts the dependency
  direction the module-structure convention establishes and hides the counting behind startup wiring.
- **Per-module limit storage, no shared module.** Preserves module boundaries exactly. Rejected because
  an operator raising one user's caps would have to know six tables instead of one, which directly
  fights the "edit the database by hand" operating model.
- **A rate-limiting library.** The established Java option stores opaque serialised bucket state, which
  cannot be edited by hand with SQL. It also addresses only the flow caps and none of the stock ones.
- **Cap the AI spend only, generalise later** — a per-user extraction budget plus a global daily
  ceiling. Cheaper and would also blunt multi-account abuse, but leaves the storage-driven caps unbuilt.

## Consequences

- There is one place to read and change everything a subject is allowed to do, and one place that
  understands stock, flow, windows, defaults and overrides. Adding a limited resource adds no counting
  logic.
- Call sites are uniform. A module asks and acts; it holds no limit knowledge of its own.
- **Usage can diverge from reality.** A release missed on a delete path leaves a user permanently
  poorer, and nothing detects it automatically. The mitigation is the re-runnable recompute; the
  obligation is that every path destroying a counted unit must release. This is the principal cost of
  the decision and was accepted deliberately over the drift-proof alternative.
- **Rollout requires seeding.** Configuration needs no backfill, because no override means the default —
  which matters given there is no user table and no signup hook. Usage records get no such
  exemption and must be seeded from existing data, or every subject starts at zero used.
- **Switching a subject between stock and flow corrupts its accumulated record**, since the same
  record means "currently held" under one kind and "consumed this period" under the other. Accepted:
  the switch is rare and the record is corrected by hand at the same time as the configuration.
- Five modules gain a permanent dependency on `limits`, and a bug in it breaks creation across all of
  them. The module must stay free of any domain knowledge for that edge to remain acceptable; an
  architecture test is the way to hold that line rather than discipline.
- The 429 is a breaking change for the mobile client, which currently maps `planning`'s 409 to a
  hardcoded message. Backend and client change together.
- Reserving before the work means a request that fails for an unrelated reason after reservation still
  costs the user a unit. For extraction that is the requirement; for the other resources it is a small
  unfairness accepted in exchange for one uniform ordering rule.
- Extraction needs identity plumbing and an exception handler before any of this applies to it; it has
  neither today.
- Nothing here forecloses a richer model later — a durable event log, or cost-proportional budgeting
  against real token usage — behind the same module boundary.
