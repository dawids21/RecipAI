# User usage limits — High-level design

**Date:** 2026-08-13
**Status:** draft
**ADRs:** [docs/ADRs/0006-shared-limits-module.md](../../ADRs/0006-shared-limits-module.md)

## Summary

Introduce per-user, per-resource usage limits with runtime-changeable defaults and overrides, as
specified in [requirements.md](requirements.md). A new shared `limits` module owns limit
configuration, usage tracking and the verification rule for every limited resource; the modules that
perform limited operations ask it for permission before acting and tell it when a held resource is
released.

## Approach

### Chosen

**Full quota service — the limits module owns configuration, usage and verification.**

A new backend module is the single authority for "may this operation proceed?". It holds the limit
configuration for every limited resource — a default that applies when nothing more specific exists,
overridable per subject — and it holds the usage records that those limits are compared against. It
never learns what a recipe or an extraction is: a resource is an opaque key it defines, and the thing
a limit attaches to is an opaque **subject** supplied by the caller.

That subject indirection is what lets one model cover resources counted differently. For recipes,
collections, meal plans and extractions the caller passes the user's identity; for shopping-list items
it passes the list's identity. The limits module compares usage against the configured limit for that
subject and resource, and answers. Callers never compute a count and never interpret a limit.

Every limited resource is tracked the same way, because the requirements allow any resource to be
configured as either a **stock** cap (maximum held at once) or a **flow** cap (maximum consumed per
period), per subject. A resource configured as flow cannot be answered by counting what exists —
consumed extractions leave nothing behind — so consumption must be recorded regardless. Rather than
maintain a derived path for stock alongside a recorded path for flow, both read from the same usage
records: creation consumes, deletion releases, and a flow-configured resource simply never releases.

Reservation happens **before** the work, not after. For extraction this is the whole point: the
requirements state that all attempts count regardless of outcome, so budget is taken before the AI
call is made and is never refunded. A crash or timeout mid-call must not yield a free extraction.

Rejections surface as HTTP 429 carrying enough structured information for the client to explain the
refusal, replacing `planning`'s current 409.

**What this gives up.** Usage records can diverge from reality if a release is ever missed on a
delete path, and a stock-derived design could not have drifted at all. That is accepted: the release
points are bounded and known, and the seeding work described under *Rollout* doubles as the repair.
It also gives up correctness-by-construction when a subject is switched between stock and flow — the
accumulated record carries no meaningful interpretation across that switch and is corrected by hand.

### Rejected alternatives

- **Gatekeeper with caller-supplied counts** — the limits module owns the rule and the flow records,
  but each caller passes its own current holdings for stock resources. Structurally immune to drift
  and free to switch a subject between stock and flow, but the ask is non-uniform, and the count a
  caller passes is a contract the limits module cannot validate — a call site that counts with the
  wrong ownership predicate is a silent wrong answer. Lost to the value of a single uniform ask.
- **Thin kernel — limits supplies numbers, modules verify themselves** — lowest coupling and smallest
  blast radius, but with the stock/flow choice configurable for every resource, each of the five
  calling modules would carry window resolution, period bookkeeping and a concurrency guard. Five
  copies of the subtle part, against an anti-requirement that wants one place to edit.
- **Counting-port inversion** — modules register a counting capability that the limits module calls
  back into. Uniform call sites with derived usage, but it inverts the dependency direction the
  module-structure standard establishes and hides the actual counting behind startup wiring.
- **Cap the AI bill only, generalise later** — a per-user extraction budget plus a global daily
  ceiling addresses the stated motivation for a fraction of the work and would also blunt multi-account
  abuse, but it leaves the stock caps unbuilt and most acceptance criteria unmet. A scope reduction,
  not a design.

## Feature areas

### Limits module (new)

**Key behaviors.**
- Resolves the limit in force for a given subject and resource: the subject's own override if one
  exists, otherwise the default for that resource.
- Resolves whether that limit is a stock cap or a flow cap, and for a flow cap, over what period —
  including the no-period case, which expresses "N ever" and never resets.
- Restores a flow allowance lazily: usage carries its own period start, and a check that finds it
  elapsed treats usage as zero and restarts the period within the same indivisible step that reserves.
  No scheduler is introduced — the deployment has none, and a reset job that fails to run would deny
  users their allowance silently. The period is anchored to the subject's first use rather than a
  wall-clock boundary, which avoids imposing a timezone on users who have never supplied one. The
  accepted costs are that windows are unaligned across subjects, that an idle subject's period does not
  advance until they return, and that a subject can consume the tail of one period and the head of the
  next in quick succession.
- Answers whether an operation may proceed, and reserves the budget for it in the same indivisible
  step, so two concurrent requests from one subject near the cap cannot both be admitted.
- Records the release of a held unit when a stock resource is destroyed.
- Reports a subject's current standing for a resource, for display purposes. This is not a plain read:
  it must apply the same elapsed-period rule a check applies, or it will report a spent allowance that
  the next check would have restored.
- Recomputes usage for a resource from the owning module's authoritative data, re-runnably, as both
  the rollout seed and the repair for any divergence.
- Tolerates being over the cap: an over-limit subject is a normal state that blocks further
  consumption and nothing else.
- Changes to configuration take effect on the next request, with no caching layer between the stored
  configuration and the check.

### Extraction

**Key behaviors.**
- Both extraction endpoints identify the calling user; neither does today.
- Budget is reserved before the AI provider is called. A failed, garbage or abandoned extraction still
  consumes it, and nothing refunds it.
- The module gains its own exception handling, which it currently lacks entirely.

### Owner-scoped resources — recipes, collections, shopping lists, meal plans

**Key behaviors.**
- Creation asks the limits module first, keyed by the owning user, and is refused if the answer is no.
- Deletion tells the limits module the unit is released.
- Existing content stays fully readable and editable when the owner is over a limit.
- `planning` stops reading its own configured plan limit and routes through the shared mechanism
  instead, preserving the behavior rather than duplicating it.
- Sharing does not consume the recipient's budget; only the owner's records change.

### Shopping-list items

**Key behaviors.**
- The item cap is keyed by the list, not by the user, so each list is counted independently.
- Item creation and deletion consume and release against that list's records.
- Because the mobile client applies item edits locally and syncs them later, a create can be refused
  after the user has already seen it succeed offline; the client has to reconcile that.

### Rejection contract

**Key behaviors.**
- A refused operation returns HTTP 429 with enough structure for a client to state which resource was
  refused and what the standing is, rather than prose alone.
- `planning`'s existing 409 becomes 429; its dedicated exception is superseded by the shared one.
- A flow-cap refusal can indicate when the operation will become possible again; a stock-cap refusal
  cannot, and says so rather than inventing a retry time.

### Mobile

**Key behaviors.**
- Limits are never computed on the device — the client displays what the server reports.
- A refused action explains why, naming the resource and the standing.
- The recipe area surfaces the user's recipe standing, satisfying the requirement that the limit be
  visible somewhere; extraction shows its explanation only at the point of refusal.
- The planning repository's mapping of the old status code changes with the backend.

### Rollout

**Key behaviors.**
- Defaults apply to every existing user without a backfill of configuration, because the absence of an
  override *is* the default — no user table and no signup hook are needed for limits to apply.
- Usage records, unlike configuration, must be seeded from existing data at rollout, or every subject
  starts at zero used and can exceed its cap until reality catches up.
- The seeding logic is the same recompute exposed by the limits module, so it can be re-run.

## Out of scope

- **Multi-account abuse.** Per-user limits bound damage per account; a new sign-up resets every quota.
  A global ceiling across all users, and device attestation, are the structural answers and are not
  built here.
- **Cost-proportional budgeting.** An extraction consumes one unit regardless of what it actually cost
  the AI provider. Reserve-and-refund against real token usage is a later refinement.
- **Audit history.** Usage records answer "how much is used now", not "what did this user do". A
  durable event log can be added behind the same module boundary if that question ever needs answering.
- **Operator tooling.** Configuration is changed with direct database edits, per the anti-requirements;
  nothing here makes that ergonomic.
- **Extraction image size.** Confirmed during research that `/extract/image` validates MIME type only
  and is bounded by the framework's multipart cap rather than the 5 MB rule applied to recipe images.
  Left as-is per the anti-requirements.

## Open questions

- **Identity key.** The requirements say users are identified by the JWT subject claim, but every
  controller and permission table in the codebase keys on the email claim, and the module-structure
  standard mandates it. Leaning to email for consistency and joinability; the subject claim is more
  correct in principle since email can change. Needs settling before anything is built.
- **Concurrency mechanism.** Reservation must be indivisible; which mechanism achieves it is a
  task-design choice.
- **Shape of the standing read path.** Whether one call reports all of a user's standings or each is
  fetched per resource, and whether mobile fetches ahead to pre-emptively disable actions or only
  reacts to refusals.
- **Offline item refusals.** What the mobile client does with an item creation that the server refuses
  after it was already applied locally and shown to the user.
- **Recompute trigger.** Whether the re-runnable recompute is reachable at runtime or only as a
  repeatable migration.
- **Release on cascades.** Whether any indirect deletion path — event-driven cleanup, collection
  unsharing — destroys a counted unit, or whether all five direct delete paths are genuinely the
  complete set.
