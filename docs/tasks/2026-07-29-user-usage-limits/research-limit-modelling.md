# Modelling user usage limits in RecipAI — options research

**Date:** 2026-07-29
**Scope:** how to model limit configuration, usage measurement and enforcement for
[`requirements.md`](requirements.md). No option is recommended here — this is a menu.

## Summary

The question "one shared limits model or per-module configuration?" is really three independent
questions: where the **limit values** are stored, where **current usage** is measured, and who
**enforces** the comparison. Every workable design is a combination of answers to those three, and
the combinations differ mostly in how much coupling they add to the module boundaries the project
already enforces. The "single point of failure" worry is mostly misplaced for this deployment — a
single container against a single Postgres means a shared limits table is no less available than the
data it guards — but a shared *module* is a real coupling and blast-radius concern, and the size of
that concern scales with how much behaviour (counters, delete-side bookkeeping, enforcement) is moved
into it, not with the fact that it is shared.

Two findings apply to every option: the codebase identifies users by the JWT `email` claim, not the
`sub` claim the requirements assume; and the `extraction` module currently has no access to user
identity at all, so it needs plumbing regardless of which model is chosen.

---

## What exists today

### Limits already in the code

| Limit | Where | Storage | Per user? |
|---|---|---|---|
| 5 owned meal plans | `planning/MealPlanService.java:54-57` | `@ConfigurationProperties("recipai.meal-plan")` → `application.yml:41-42` | No — global |
| 2 images per recipe | `recipes/images/RecipeImages.java:23,45-46` | `private static final int MAX_IMAGES` | No — hardcoded in the aggregate |
| 5 MB per image | `recipes/images/ImageProcessingService.java:17,46` | `private static final long MAX_FILE_SIZE` | No — hardcoded |

The meal-plan limit is the only one that is configurable, and it is bound at startup by
`@ConfigurationProperties` — **changing it today requires a restart**, which the requirements
explicitly rule out for the new mechanism.

The image count limit is interesting as a counter-example: it lives inside the `RecipeImages` domain
object as an invariant, not as configuration. That is the "limit as aggregate invariant" style, and
it is the shape a per-module design naturally grows toward.

### Ownership counting

All four owner-scoped resources use the same shape: a `<resource>_permission` table with
`PRIMARY KEY (email, <resource>_id)` and `role IN ('OWNER','EDITOR')`
(`V1__initial_schema.sql`, `V11__meal_planning_schema.sql`, and the collections/shopping-list
migrations). Counting "how many X does this user own" is therefore always the same query against a
table whose primary key already has `email` as its leading column, so the count is index-supported.

Only `planning` has written that query — `MealPlanPermissionRepository.countOwnedByEmail`
(`planning/MealPlanPermissionRepository.java:16`). `recipes`, `recipes.collections` and
`shoppinglists` have no count query, and `ShoppingListItemRepository` has no per-list count either.
Whichever option is chosen, **four new count queries are needed**.

### Creation paths that need a check

- `RecipeService.save` (`recipes/RecipeService.java:85`)
- `RecipesCollectionService.create` (`recipes/collections/RecipesCollectionService.java:50`)
- `ShoppingListService.create` and `ShoppingListService.createItem`
  (`shoppinglists/ShoppingListService.java:34,60`)
- `MealPlanService.create` (`planning/MealPlanService.java:51`) — already checks
- `ExtractionController.extractFromText` / `extractFromImage` (`extraction/ExtractionController.java:23,29`)

### The extraction gap

`ExtractionController` takes no `Jwt` parameter at all — neither endpoint knows who is calling. The
module also has no `@ControllerAdvice`, unlike every other module. Adding a per-user limit to
extraction means adding identity plumbing and an exception handler to that module first. This is
independent of the modelling choice.

### Identity key mismatch

`docs/backend/standards/module-structure.md` mandates `jwt.getClaimAsString("email")`, and every
controller and permission table in the codebase keys on email. The requirements document says users
are identified by "Firebase JWT subject". Following the existing convention (email) keeps the limits
table joinable-by-eye with the permission tables; following `sub` is more correct in principle (email
can change) but would make the limits table the only one keyed differently. **This needs deciding
before any option is implemented** — it changes the primary key of whatever table is created.

### Client side

Mobile has no shared error model. Each repository maps status codes to strings inline — e.g.
`meal_plan_repository.dart:93-94` turns HTTP 409 into `Exception('Plan limit exceeded')`. There is no
parsing of the `ProblemDetail` body that `PlanningExceptionHandler` produces
(`planning/PlanningExceptionHandler.java:42-50`). Surfacing a useful "you have used 100 of 100
recipes" message requires either a convention for reading `ProblemDetail.detail`, or a structured
error body. Again, independent of the backend modelling choice.

---

## The three axes

Naming these separately makes the alternatives below fall out mechanically.

**Axis 1 — where the limit *values* live**
1. One table keyed `(user, resource)` in a shared module.
2. One table per module (`recipe_limit`, `extraction_limit`, …).
3. Properties for defaults + one table for per-user overrides (the Google Cloud model:
   `defaultLimit` overridden by `consumerOverride`, per the
   [service quota model](https://cloud.google.com/service-usage/docs/service-quota-model)).

**Axis 2 — how *current usage* is obtained**
1. **Derived** — `SELECT COUNT(*)` against the owning module's tables at check time. Always correct,
   impossible to drift, only works for stock caps.
2. **Materialised** — a counter row incremented on create and decremented on delete. Needed for flow
   caps (there is nothing to count — consumed extractions leave no row), optional for stock caps.
   Kubernetes' ResourceQuota uses exactly this split: `spec.hard` versus `status.used`, with a
   background controller that recomputes `used` because admission control alone cannot guarantee
   deletes were observed ([design proposal](https://github.com/kubernetes/design-proposals-archive/blob/main/resource-management/admission_control_resource_quota.md)).
   Any materialised-stock design inherits that reconciler obligation.
3. **Ledger** — append a row per consumption event and aggregate over a window. More storage, gives
   history and makes window semantics changeable after the fact.

**Axis 3 — who enforces**
1. The owning module's service, before the write (today's pattern).
2. A cross-cutting interceptor/aspect in front of the controller.
3. The database (constraint, trigger, or conditional `UPDATE`).

Note that axis 3 also determines concurrency safety. Requirement "Concurrent creation" (two requests
near the cap both passing a naive check) is unsolved by a plain COUNT-then-INSERT under Postgres'
default `READ COMMITTED`. The available fixes are a row lock on a per-user row (`SELECT … FOR UPDATE`),
a [Postgres advisory lock](https://firehydrant.com/blog/using-advisory-locks-to-avoid-race-conditions-in-rails/)
keyed on the user, `SERIALIZABLE` isolation, or a conditional counter update
(`UPDATE … SET used = used + 1 WHERE used < limit` — the SQL analogue of the compare-and-swap the
Kubernetes design uses). The first three serialise per user only, which is fine at this scale; a
shared counter row is a contention point but again, per user.

---

## Alternative A — Full quota service in a shared `limits` module

A new `xyz.stasiak.recipai.limits` module owns everything: `usage_limit(user, resource, kind, value,
period)` and `usage_counter(user, resource, window_start, used)`, plus a `LimitsFacade` exposing
`consume(user, Resource, amount)` and `release(user, Resource, amount)`. All six resources are
materialised counters. Every module calls `consume` before creating and `release` after deleting.

This is the shape the SaaS entitlement-platform vendors describe — configuration, enforcement and
metering as three layers of one service, deliberately decoupled from application code
([Stigg](https://www.stigg.io/blog-posts/entitlements-untangled-the-modern-way-to-software-monetization)).

**Pros**
- Genuinely one place to read and change a user's entire budget, which is what the "edit the database
  directly" anti-requirement makes most valuable.
- Flow caps and stock caps use identical machinery; the per-user stock-or-flow switch the
  requirements demand is a column, not a branch.
- Concurrency solved once, in the conditional `UPDATE`.
- "All extraction attempts count" is trivial: consume before calling Gemini, never release.

**Cons**
- Every module gains a write-path dependency on `limits`, and a *new obligation* on the delete path.
  Miss a `release` and the user is permanently poorer — the drift problem, which then requires a
  reconciliation job to fix, exactly as Kubernetes needed.
- Largest blast radius: a bug here breaks creation in all five modules.
- Counters for stock resources duplicate information the permission tables already hold — deriving
  would have been free and always correct.

---

## Alternative B — Shared config + shared flow counters, derived stock counts

Same `limits` module, but it stores only limit *values* and *flow* counters. Stock usage is never
materialised: the owning module asks `limitsFacade.limitFor(user, RECIPES)` and compares against its
own `countOwnedByEmail`.

**Pros**
- Drift is structurally impossible for the five stock resources.
- No delete-side obligation anywhere; no reconciler.
- Still a single table to `UPDATE` when raising someone's cap.

**Cons**
- Two mechanisms to understand — extraction consumes, everything else counts.
- The per-user stock-or-flow switch gets awkward: if a stock-kind resource can be reconfigured as
  flow, the module needs both code paths anyway (or the switch is restricted to extraction, which is
  the only resource the requirements actually exemplify with both kinds).
- Check-then-act race must still be solved in each of the five modules.

---

## Alternative C — Fully per-module, no shared module

Each module gets its own limit table, repository, defaults, exception and HTTP mapping —
`recipe_limit(email, max_owned)`, `extraction_limit(email, kind, value, period)` and so on.
`planning` keeps its existing exception and just swaps the properties read for a table read.

**Pros**
- Zero new coupling. Module boundaries stay exactly as
  `docs/backend/standards/module-structure.md` describes them, with no facade fan-in. This is the
  arrangement modular-monolith guidance is happiest with: each module owns its tables, cross-module
  access via APIs only, and shared modules kept tiny or absent
  ([Milan Jovanović](https://www.milanjovanovic.tech/blog/the-modular-monolith-boundary-i-couldnt-take-back)).
- Each limit can have semantics that fit its resource — per-list item counts, per-user owner counts
  and per-period extraction budgets stop having to share one schema.
- Blast radius per change is one module.

**Cons**
- The same ~80 lines (default resolution, override lookup, over-cap tolerance, concurrency guard)
  written five or six times, with five or six chances to get the defaults-fallback subtly different.
- Directly fights the "no admin UI, limits are changed by editing the database" anti-requirement: an
  operator raising one user's caps must know six tables instead of one.
- Adding a seventh limited resource is a new table and migration every time.

---

## Alternative D — Thin shared kernel: shared *values*, module-local everything else

One `user_limit(user, resource, kind, value, period)` table and a `limits` module whose entire public
surface is a read: `LimitsFacade.limitFor(user, Resource)` returning the resolved limit (override, or
default). No counters, no consume/release, no enforcement. Each module keeps its own count query, its
own exception, its own message and its own concurrency guard. Extraction additionally owns an
`extraction_usage` ledger inside the `extraction` module.

This is the "SharedKernel, kept deliberately tiny" prescription — cross-cutting *types and values* are
shared, cross-cutting *behaviour* is not.

**Pros**
- Single source of truth for the numbers (the operational property that matters) without a single
  point of behaviour.
- The shared component is a pure read of one table, so it is close to unfailable — the SPOF concern
  largely evaporates.
- Domain-specific error messages stay in the modules that own the domain.
- No drift, no reconciler, no delete-side obligation.

**Cons**
- The stock/flow interpretation logic lands in each module (or in a shared value object the modules
  each apply — which is the point at which this drifts toward B).
- The concurrency guard is still per module, five times.
- `limits` becomes a module every other module depends on, so it must never be allowed to grow. That
  is a discipline problem, not a design one, and worth an architecture test in CI to enforce.

---

## Alternative E — Properties for defaults, table for overrides only

Defaults extend the existing idiom into `recipai.limits.*` in `application.yml`; the database holds
only the rows for users who deviate. Composable with any of B/C/D for the enforcement side.

**Pros**
- Defaults are versioned, reviewable and diffable in git; the override table stays nearly empty.
- Mirrors the model Google Cloud uses for service quotas (default limit, overridden per consumer).
- Smallest migration.

**Cons**
- Changing a *default* needs a redeploy. The requirements only demand runtime change for a limit, and
  the acceptance criteria only exercise per-user raises — but the wording "changing a limit takes
  effect immediately" is ambiguous about defaults and should be clarified.
- If defaults must also be live-changeable, `@ConfigurationProperties` is the wrong vehicle: it binds
  at bean initialisation and needs `@RefreshScope` plus an explicit refresh event to pick anything up
  ([Baeldung](https://www.baeldung.com/spring-boot-properties-dynamic-update)). That is a meaningful
  amount of machinery relative to just reading a row.
- Two places to look when answering "why is this user's limit 5?".

---

## Alternative F — Cross-cutting enforcement (orthogonal add-on)

An `@Limited(Resource.RECIPES)` annotation on controller methods, with an aspect or
`HandlerInterceptor` reading the JWT and consulting whichever store A/B/D provides, plus one shared
`@ControllerAdvice` for the 409. Combines with any storage choice; it is an axis-3 decision only.

**Pros**
- Modules contain no limit code at all; a new limited endpoint is one annotation.
- One consistent HTTP response shape, which is exactly what the mobile client needs.

**Cons**
- The check happens *outside* the transaction that performs the insert, which widens the concurrency
  window rather than closing it.
- Per-list item limits need a path variable and a permission check, which is clumsy in an aspect.
- Hidden control flow; harder to unit-test than an explicit service call; against the grain of a
  codebase where every rule is an explicit line in a service method.

---

## Comparison

| | A: quota service | B: shared config + flow counters | C: per module | D: thin kernel | E: props + overrides |
|---|---|---|---|---|---|
| New shared module | yes, large | yes, medium | no | yes, tiny | optional |
| Stock usage source | counters | derived | derived | derived | derived |
| Drift possible | yes → needs reconciler | no | no | no | no |
| Delete-path obligation | yes | no | no | no | no |
| One place to edit a user's limits | yes | yes | no | yes | mostly |
| Duplication across modules | none | some | high | some | some |
| Blast radius of a bug | all write paths | all write paths | one module | read only | read only |
| Runtime change without restart | yes | yes | yes | yes | per-user yes, defaults no |
| Fits existing module conventions | weakest | medium | strongest | strong | strong |

---

## On the single-point-of-failure question

Worth separating three different things that "SPOF" can mean here:

1. **Availability.** Not a real risk. The backend is a single Spring Boot container against a single
   Postgres (`docs/project/architecture.md`); a shared `user_limit` table is precisely as available as
   `recipes`. There is no network hop and nothing new that can be down independently.
2. **Blast radius.** Real, and proportional to how much *behaviour* is centralised. A shared table
   read (D) can realistically only fail by returning a wrong number. A shared consume/release
   protocol (A) can fail by leaking budget, double-charging, deadlocking, or drifting — and it does so
   on every module's write path simultaneously.
3. **Coupling.** Real, and permanent. Every module that imports `LimitsFacade` gains a dependency edge
   that will not be removed later. Modular-monolith practice accepts this for a shared kernel provided
   it stays small and domain-free, and recommends enforcing the boundary with architecture tests in CI
   rather than trusting discipline.

So the honest framing is not "single source of truth versus single point of failure" but "how much
behaviour is worth centralising to get the single source of truth". The truth (the numbers) can be
centralised almost for free; the behaviour is where the cost sits.

---

## Decisions every option still needs

- **Identity key** — `email` (codebase convention) or `sub` (requirements' wording). Changes the PK.
- **Flow window semantics** — the requirements defer this. Fixed calendar-day windows are simplest and
  align with how a user thinks about "2 per day", at the cost of the boundary-burst effect (up to 2×
  the limit across a midnight boundary); sliding windows and token buckets avoid that at the cost of
  more state ([Arcjet](https://blog.arcjet.com/rate-limiting-algorithms-token-bucket-vs-sliding-window-vs-fixed-window/)).
  For a 2/day AI budget the boundary burst costs two extra Gemini calls, which may simply not matter.
  Timezone still has to be picked — UTC day is the cheap answer.
- **Caching** — "takes effect immediately" is satisfied by reading the row per request; that is one
  indexed lookup on a write path that is already doing several. Any cache introduces a staleness
  window that has to be justified against the acceptance criterion.
- **Concurrency guard** — pick one of the four mechanisms in Axis 3 and apply it consistently.
- **Extraction prerequisites** — `Jwt` parameter on both endpoints, a module `@ControllerAdvice`, and a
  consume-before-call ordering so failed calls still charge.
- **Migration for existing users** — with a defaults-fallback (no row = default), rollout needs no
  backfill at all, which satisfies "at rollout every existing user lands on the defaults" for free.
  Any design that requires a row per user needs a backfill *and* a hook for new signups, which is
  awkward given there is no user table and no signup event.
- **Client error contract** — decide whether the limit response carries structured fields (limit,
  used, resource) so mobile can render "42/100", or whether `ProblemDetail.detail` prose is enough.
  The requirements' UI question ("persistent indicator vs warning near the cap") depends on this, and
  a persistent indicator implies a read endpoint for budgets, which none of the alternatives above
  includes.

---

## Open questions

- Does "changing a limit takes effect immediately" cover changing a *default*, or only a per-user
  value? Alternative E hinges on this.
- Is the per-user stock-or-flow switch genuinely needed for all six resources, or only for
  extraction? The requirements state it generally but only exemplify it for extraction, and the answer
  significantly changes how much schema and branching each option needs.
- Do `/extract/image` uploads bypass `ImageProcessingService`'s 5 MB check? From
  `ExtractionController.extractFromImage` they clearly do — it validates MIME type only, and the
  effective cap is Spring's `spring.servlet.multipart.max-file-size: 10MB` (`application.yml:7-9`).
  The requirements list this as needing verification; it is now verified, but out of scope per the
  anti-requirements.

## Sources

### Codebase
- `backend/src/main/java/xyz/stasiak/recipai/planning/MealPlanService.java`,
  `MealPlanPermissionRepository.java`, `MealPlanProperties.java`, `PlanningExceptionHandler.java` —
  the only existing configurable limit, and the pattern the requirements say to follow.
- `backend/src/main/java/xyz/stasiak/recipai/recipes/images/RecipeImages.java`,
  `ImageProcessingService.java` — hardcoded limits as aggregate invariants.
- `backend/src/main/java/xyz/stasiak/recipai/extraction/ExtractionController.java`,
  `ExtractionService.java` — the identity and exception-handler gap.
- `backend/src/main/resources/db/migration/V1__initial_schema.sql`, `V11__meal_planning_schema.sql` —
  the uniform `(email, resource_id)` permission-table shape that makes ownership counting cheap.
- `backend/src/main/resources/application.yml` — current limit configuration and multipart caps.
- `mobile/lib/features/planning/meal_plan_repository.dart` — how a limit rejection surfaces today.
- `docs/backend/standards/module-structure.md`, `java-patterns.md`, `configuration-profiles.md` — the
  facade, exception-handler and configuration conventions any option must respect.

### External
- [Kubernetes ResourceQuota admission control design proposal](https://github.com/kubernetes/design-proposals-archive/blob/main/resource-management/admission_control_resource_quota.md) — hard/used split, compare-and-swap on admission, and why a background reconciler is unavoidable once usage is materialised.
- [Google Cloud service quota model](https://cloud.google.com/service-usage/docs/service-quota-model) — default limit plus per-consumer override resolution, the model behind Alternative E.
- [Stigg — Entitlements untangled](https://www.stigg.io/blog-posts/entitlements-untangled-the-modern-way-to-software-monetization) — the configuration/enforcement/metering three-layer split and the argument for centralising it.
- [Milan Jovanović — The modular monolith boundary I couldn't take back](https://www.milanjovanovic.tech/blog/the-modular-monolith-boundary-i-couldnt-take-back) — shared-kernel sizing, module-owns-its-tables, and enforcing boundaries with architecture tests.
- [Arcjet — Rate limiting algorithms: token bucket vs sliding window vs fixed window](https://blog.arcjet.com/rate-limiting-algorithms-token-bucket-vs-sliding-window-vs-fixed-window/) — window semantics and the fixed-window boundary-burst tradeoff for the flow caps.
- [FireHydrant — Using advisory locks to avoid race conditions](https://firehydrant.com/blog/using-advisory-locks-to-avoid-race-conditions-in-rails/) — Postgres advisory locks as a per-user serialisation point for check-then-act.
- [Baeldung — Changing Spring Boot properties at runtime](https://www.baeldung.com/spring-boot-properties-dynamic-update) — why `@ConfigurationProperties` cannot satisfy "no restart" without `@RefreshScope` and an explicit refresh event.
