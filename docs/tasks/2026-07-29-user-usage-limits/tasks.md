# User usage limits — Tasks

**Date:** 2026-08-17

## Summary

- **T1:** Limits module foundation and the AI extraction budget
- **T2:** Owner-scoped caps for recipes, collections and shopping lists
- **T3:** Meal plan cap migrated onto the shared mechanism
- **T4:** Per-list shopping-list item cap
- **T5:** Mobile — refusal messaging and recipe standing

## Cross-task notes

- **T1 carries the infrastructure.** The limits schema (configuration and usage records), resolution,
  the indivisible reserve step and the 429 contract all land in T1 because extraction is the first
  and cheapest consumer of them — it reserves and never releases, so T1 needs no release path and no
  recompute. Every later task adds a consumer, not a mechanism.
- **The identity key must be settled before T1 starts.** `HLD.md` > Open questions > *Identity key*
  is explicitly marked as blocking. Whatever is chosen becomes the subject key baked into the usage
  and configuration records, and changing it later means a data migration across every resource.
- **429 is a breaking change for the mobile client.** T3 flips `planning`'s 409 to 429, so the
  targeted client-side mapping fix ships inside T3 — it cannot wait for T5. Between T1 and T5 the app
  shows a generic error for every other refusal; that is accepted and closed out by T5.
- **Release paths must be audited, not assumed.** `HLD.md` > Open questions > *Release on cascades*
  is unresolved. T2 owns the audit for owner-scoped resources and T4 for items; a missed release path
  is the failure mode ADR-0006 names as the principal cost of the design.
- **Parallelism.** T3 and T4 are independent of each other and can run concurrently once T2 has
  merged (both need the release and recompute mechanism T2 introduces). T5 needs T2 for the recipe
  standing; the rest of its refusal messaging benefits from T3 and T4 but does not depend on them.

---

## T1: Limits module foundation and the AI extraction budget

**User-visible outcome**

An API consumer calling `/extract/text` or `/extract/image` with curl is refused with HTTP 429 once
they have used their configured extraction allowance, and an operator who edits the limit in the
database sees the change take effect on the very next request with no restart.

**Scope**

- The new `limits` module in full: configuration storage (default per resource, override per
  subject), usage records, override-then-default resolution, stock-versus-flow resolution including
  the no-period "N ever" case, lazy period restart, and the check-and-reserve step as one indivisible
  operation — `HLD.md` > Feature areas > *Limits module (new)*
- Identity plumbing on both extraction endpoints, which currently identify no one, and the module's
  first exception handling — `HLD.md` > Feature areas > *Extraction*
- Reservation before the AI provider call, with no refund on failure or abandonment
- The shared 429 response and its structure, including the flow-cap retry indication and the stock-cap
  "no retry time" case — `HLD.md` > Feature areas > *Rejection contract*
- The architecture test that holds the `limits` module free of domain knowledge, per ADR-0006 >
  Consequences

**Out of scope**

- Release of a held unit and the recompute/seed — no stock resource exists yet; introduced in T2
- Any owner-scoped resource — covered in T2 and T3
- The per-list item cap — covered in T4
- The standing read path and all client-side messaging — covered in T5
- `planning`'s existing 409 and its `maxOwnedPlans` property — covered in T3, untouched here

**Depends on:** none

**HLD references**

- `HLD.md` > Feature areas > *Limits module (new)*
- `HLD.md` > Feature areas > *Extraction*
- `HLD.md` > Feature areas > *Rejection contract*
- `HLD.md` > Open questions > *Identity key*, *Concurrency mechanism*
- `docs/ADRs/0006-shared-limits-module.md`

**How to verify**

With an extraction limit configured to 2 for a test user: two `curl -X POST .../extract/text` calls
succeed, the third returns 429 with a body naming the extraction resource and the user's standing.
Then `UPDATE` the limit to 5 directly in Postgres and repeat the third call without restarting the
application — it succeeds. Separately, point `/extract/text` at a URL that makes the AI call fail and
confirm the standing still advanced by one.

**Risks / unknowns**

- The identity key decision blocks the schema (see cross-task notes).
- The indivisible reserve is the part most likely to be got subtly wrong and the hardest to test;
  `HLD.md` > Open questions > *Concurrency mechanism* leaves the mechanism to task design. Two
  concurrent requests from one subject at the cap must not both be admitted.
- Lazy period restart has to be applied inside the same step that reserves, or an elapsed period is
  seen as spent.

---

## T2: Owner-scoped caps for recipes, collections and shopping lists

**User-visible outcome**

A user is refused creation of a recipe, collection or shopping list once they hold their configured
maximum, keeps full read and edit access to everything they already own, and frees capacity again by
deleting one.

**Scope**

- Creation checks keyed by the owning user, and release on deletion, for `recipes`,
  `recipes.collections` and `shoppinglists` (the list itself) — `HLD.md` > Feature areas >
  *Owner-scoped resources*
- The re-runnable recompute that rebuilds a resource's usage from the owning module's authoritative
  data, and its use to seed these three resources at rollout — `HLD.md` > Feature areas > *Limits
  module (new)*, *Rollout*
- An audit of every path that destroys a counted unit for these three resources, including indirect
  and cascading deletions, so no release point is missed
- Confirming that sharing changes only the owner's records and never the recipient's

**Out of scope**

- Meal plans — covered in T3, even though the shape is identical, because the migration off
  `maxOwnedPlans` and the 409→429 flip are a distinct change with a client-side counterpart
- Shopping-list *items* — covered in T4; this task caps only the number of lists
- Whether the recompute is reachable at runtime or only as a repeatable migration —
  `HLD.md` > Open questions > *Recompute trigger*, a task-design decision here
- Any client-side display or messaging — covered in T5
- The 5 MB image cap and the per-recipe image count, which stay as they are per the anti-requirements

**Depends on:** T1

**HLD references**

- `HLD.md` > Feature areas > *Owner-scoped resources — recipes, collections, shopping lists, meal plans*
- `HLD.md` > Feature areas > *Limits module (new)* — the recompute and release behaviors
- `HLD.md` > Feature areas > *Rollout*
- `HLD.md` > Open questions > *Release on cascades*, *Recompute trigger*
- `docs/ADRs/0006-shared-limits-module.md` > Consequences

**How to verify**

Set a test user's recipe limit to 3. Create three recipes with curl; the fourth returns 429. `GET`
and `PUT` on an existing recipe still succeed. Delete one recipe and the next creation succeeds.
Then lower the limit to 1 in the database and confirm all three recipes remain readable and editable
while creation stays refused. Finally, run the recompute against a database seeded with pre-existing
recipes and confirm the usage record matches the actual owned count, and that running it a second
time changes nothing.

**Risks / unknowns**

- Ownership predicates differ per resource; the recompute must use the same predicate the creation
  check assumes, or the seed and the live count disagree.
- Recipe deletion also destroys images in S3 — confirm whether that path is the same code path the
  release hooks into.

---

## T3: Meal plan cap migrated onto the shared mechanism

**User-visible outcome**

A user at their meal plan limit is refused with the same 429 contract every other resource uses and
sees the correct explanation in the app, and an operator raises that user's plan limit by editing the
database instead of redeploying with a new `maxOwnedPlans` value.

**Scope**

- `planning` stops reading its configured plan limit and routes creation through the shared mechanism
  instead, preserving the existing behavior rather than duplicating it; deletion releases —
  `HLD.md` > Feature areas > *Owner-scoped resources*
- Removal of `MealPlanProperties.maxOwnedPlans` and `MealPlanLimitExceededException`, superseded by
  the shared refusal — `HLD.md` > Feature areas > *Rejection contract*
- Seeding meal plan usage with the T2 recompute
- The matching client-side change in `mobile/lib/features/planning/meal_plan_repository.dart`, which
  maps the old 409 today and must not regress when the backend flips to 429 —
  `HLD.md` > Feature areas > *Mobile*

**Out of scope**

- Generic 429 handling shared across all mobile features, and the standing display — covered in T5;
  this task changes only the planning repository's status-code mapping
- Any other owner-scoped resource — covered in T2

**Depends on:** T2

**HLD references**

- `HLD.md` > Feature areas > *Owner-scoped resources — recipes, collections, shopping lists, meal plans*
- `HLD.md` > Feature areas > *Rejection contract*
- `HLD.md` > Feature areas > *Mobile*
- `docs/ADRs/0006-shared-limits-module.md` — the 409→429 consequence

**How to verify**

With the plan limit set in the database rather than in application configuration, create meal plans
until refused: the response is 429, not 409. Raise the limit with SQL and create another without a
restart. In the mobile app, attempt a meal plan creation past the limit and confirm the user still
sees the limit explanation rather than a generic failure. The existing `MealPlanIntegrationTest`
coverage of the limit behavior still passes in migrated form.

**Risks / unknowns**

- Backend and client must ship together; a backend-only deploy leaves the app showing a generic error
  for plan refusals.

---

## T4: Per-list shopping-list item cap

**User-visible outcome**

A user adding items to a shopping list is refused once that individual list is full, while their
other lists remain unaffected.

**Scope**

- Item creation and deletion consuming and releasing against the list's own records, with the list's
  identity as the subject rather than the user's — `HLD.md` > Feature areas > *Shopping-list items*
- Seeding item usage per existing list via the T2 recompute
- Release on every path that removes an item, including list deletion if it destroys items
- Deciding how the mobile client reconciles a create that the server refuses after it was already
  applied locally and shown to the user — `HLD.md` > Open questions > *Offline item refusals*

**Out of scope**

- The cap on the number of lists a user owns — covered in T2
- Cross-list item totals; the requirement is explicitly per list
- General 429 messaging in the app — covered in T5, beyond the offline reconciliation behavior this
  task must define

**Depends on:** T2

**HLD references**

- `HLD.md` > Feature areas > *Shopping-list items*
- `HLD.md` > Open questions > *Offline item refusals*
- `docs/ADRs/0006-shared-limits-module.md` — the opaque-subject rationale

**How to verify**

Set the item limit to 5. Add five items to list A with curl; the sixth returns 429. Adding an item to
list B still succeeds, confirming the counts are independent. Delete an item from list A and the next
add succeeds. In the app, add items past the cap while offline, then reconnect and confirm the client
resolves the refusal without silently losing or duplicating items.

**Risks / unknowns**

- The client applies item edits locally and syncs later, so a refusal arrives after the user believed
  the action succeeded. This is the only place in the feature where the refusal is not synchronous
  with the user's action, and it interacts with the existing shopping-list sync design
  (`docs/ADRs/0003`, `0004`, `0005`).

---

## T5: Mobile — refusal messaging and recipe standing

**User-visible outcome**

A user sees their recipe standing in the recipe area, and any blocked action — creating a recipe,
collection, list or plan, or running an extraction — explains which resource was refused and what
their current standing is, rather than failing with a generic error.

**Scope**

- The standing read path: the limits module reporting a subject's current standing for a resource,
  applying the same elapsed-period rule a check applies, plus whatever endpoint exposes it —
  `HLD.md` > Feature areas > *Limits module (new)*
- Shared client-side handling of the 429 contract, with the server's reported values displayed rather
  than anything computed on the device — `HLD.md` > Feature areas > *Mobile*
- Recipe standing surfaced in `features/recipe`, satisfying the requirement that a limit be visible
  somewhere
- Refusal explanations at the point of blockage in `features/extraction`, `features/collection`,
  `features/shopping_list` and `features/planning`
- Settling `HLD.md` > Open questions > *Shape of the standing read path* — one call for all standings
  versus per resource, and whether the app fetches ahead to pre-emptively disable actions or only
  reacts to refusals

**Out of scope**

- Any change to how limits are enforced or counted — the backend rules are complete after T4
- Pre-emptive disabling of actions, unless the standing-read-path decision above chooses it
- An admin or self-serve surface for raising limits, excluded by the anti-requirements

**Depends on:** T2 (recipe standing and its refusals); T3 and T4 for complete coverage of plan and
item refusals

**HLD references**

- `HLD.md` > Feature areas > *Mobile*
- `HLD.md` > Feature areas > *Limits module (new)* — the standing report behavior
- `HLD.md` > Feature areas > *Rejection contract*
- `HLD.md` > Open questions > *Shape of the standing read path*

**How to verify**

In the app with a recipe limit of 3 and two recipes owned, the recipe area shows the standing.
Creating a third updates it; attempting a fourth shows a message naming recipes and the standing.
With the extraction budget exhausted, running a URL extraction shows an explanation of the block
rather than a generic failure. Raising the limit in the database and reopening the screen shows the
new standing without reinstalling or restarting the app.
