# T2: Recipes migrated, with collection-derived access composed by `recipes` — Review Findings (iteration 2)

**Date:** 2026-08-27

## Context for the fixing agent

The "share invites" feature turns sharing into a two-step handshake: `POST /<resource>/{id}/share`
creates a pending invite that grants nothing, and the invitee must accept before a permission exists.
The work is sequenced across five tasks; **T2** is the second backend task. It migrates recipe
permissions out of the module-local `recipe_permission` table into the shared `permissions` module
established by T1, and — the part unique to this task — keeps recipes' *collection-derived* access
working by composing two role answers inside `recipes` rather than teaching the `permissions` module
that recipes belong to collections (`docs/ADRs/0007-shared-permissions-module.md`).

Defining artifacts, all present:

- `docs/tasks/2026-08-26-share-invites/requirements.md`
- `docs/tasks/2026-08-26-share-invites/HLD.md`
- `docs/tasks/2026-08-26-share-invites/tasks.md` (see the **T2** section)
- `docs/tasks/2026-08-26-share-invites/plans/T2-task-design.md`
- `docs/tasks/2026-08-26-share-invites/plans/T2-implementation-plan.md`

**This is the second review pass.** T2's substance was committed as `9c2bb3d feat(backend): share
recipes by invite`. A first review raised three should-fixes and four nits against that work; a fix
pass then addressed them, and this file replaces the first review's findings. All seven earlier
findings were re-verified against the code and are resolved or deliberately skipped — see
**Iteration 1 fixes** below. The two findings in **Findings** are new, and both live in code the fix
pass itself touched.

Working tree at the time of this review: the fix pass uncommitted (`RecipeService.java`, the two
integration test files, five docs files) plus this file untracked. Backend suite green —
`./mvnw -o test` from `backend/` gives **320 tests, 0 failures, 2 skipped**. Nothing here is blocking.

## What was reviewed

The uncommitted fix pass, read against the committed T2 diff it repairs:

- `backend/src/main/java/.../recipes/RecipeService.java` (MODIFIED) — a comment on `findAll`
  explaining the deliberate absence of `findAllUnassigned`'s empty-map short-circuit, and the
  `recipesCollectionId` guard in `updateById` changed from `!=` to `.equals`.
- `backend/src/test/java/.../recipes/RecipeIntegrationTest.java` (MODIFIED) — `findPendingInviteId`
  and `acceptPendingRecipeInvite` take a `label`/`recipeName` and match on it; the
  delete-cancels-invite assertion filters on this recipe's label; eleven call sites updated.
- `backend/src/test/java/.../planning/MealPlanIntegrationTest.java` (MODIFIED) — the same helper
  change, plus three imports replacing inline FQNs.
- `docs/INDEX.md` (MODIFIED) — the recipes `db.md` line drops `recipe_permission` and points at
  `permissions/db.md`.
- `docs/backend/modules/{recipes/db.md,recipes/module.md,permissions/db.md,permissions/module.md}`
  (MODIFIED) — history-narrating sentences and `docs/tasks/` links rewritten as present-tense facts.

## Iteration 1 fixes

All verified against the code, not taken from the fix pass's own report.

- **S1 (invite lookup depended on JUnit method order) — fixed, and the dependence is genuinely gone.**
  `shareRecipe` passes `recipe.getName()` as the invite label (`RecipeService.java:284`), so the label
  is a real discriminator. No two *concurrently pending* invites for `user2@example.com` share one:
  the only invite the outer class leaves behind is `"Duplicate Invite Test"`, and `LimitsEnforced`'s
  `@AfterEach` deletes its recipes, which cancels their invites. The `&&` short-circuits before
  `label()`, so non-RECIPE invites cannot NPE.
- **S2 (`docs/INDEX.md` advertised a removed section) — fixed.** `INDEX.md:52-53` now lists only the
  tables `recipes/db.md` documents. The two-line wrap matches the convention T1 set at `:66`, `:68`,
  `:71`.
- **S3 (docs narrated the change and cited `docs/tasks/`) — fixed for the four files this task owns.**
  No narrating phrase or task reference remains under `docs/backend/modules/recipes/` or
  `.../permissions/`. The two T1 leftovers (`limits/db.md:62`, `shopping-lists/db.md:35`) remain;
  `git blame` puts both in `d22088f`, so they are outside this task's diff, as the finding allowed.
  They are still live `CLAUDE.md` violations for T3's docs pass to sweep.
- **N1 (inline FQNs) — fixed.** Three imports, no collision.
- **N2 (undocumented `findAll` asymmetry) — fixed; see N5 for the half that remains.**
- **N3 (`assertThat(false).isTrue()` → `fail(...)` sweep) — skipped, per instruction.**
- **N4 (`!=` on UUIDs) — fixed, but the fix changed behaviour; see S4.**

## Findings

### Should-fix

**S4. The `.equals` fix silently changes an authorization outcome, and nothing covers it**

- **Where:** `backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeService.java:168`
- **What:** The old guard compared two `UUID` *references* with `!=`, so it was always true and
  `recipesCollectionService.findById(request.recipesCollectionId(), userEmail)` ran on every update
  that carried a collection id. That call throws `RecipesCollectionAccessDeniedException` for a user
  with no permission on the collection (`recipes/collections/RecipesCollectionService.java:48-54`).
  With the guard now comparing values, an update that leaves the recipe in the collection it is
  already in skips the validation entirely.
- **Why it matters:** This flips a user-visible authorization result. A non-owner EDITOR of a recipe
  that lives in a collection they cannot see previously received **403** when they echoed the
  recipe's existing `collectionId` back in an update — which a client naturally does, since
  `RecipeDetailsDto` exposes `collectionId` even when `collectionName` is null (asserted at
  `RecipeIntegrationTest.java:1085`). That update now **succeeds**. The new behaviour is very likely
  the correct one: the caller holds EDITOR on the recipe, is not moving it, and the request's
  collection id is discarded for non-owners anyway (`RecipeService.java:174-176`). But the change
  arrived as a nit whose stated justification was "harmless beyond a wasted query", it is not
  mentioned in `T2-task-design.md`, and no test exercises the path — the one EDITOR-update test
  (`RecipeIntegrationTest.java:526`) passes `null` as the collection id.
- **Fix should achieve:** The changed behaviour is pinned by a test, or it is not changed. A test
  should establish that an EDITOR can update a recipe that sits in a collection they have no
  permission on, so that a future revert to reference comparison — or any re-tightening of the guard —
  fails loudly instead of silently restoring a 403 on a legitimate edit.

### Nits

**N5.** `backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeService.java:55-57` — the new comment
explains why `findAll` must not short-circuit on an empty access map, which is the part that stops a
future reader "tidying up" the asymmetry with `findAllUnassigned`. It does not mention the second
half the original nit named: that passing an empty collection into `findAllByUserEmail` relies on
Hibernate 6 rendering an empty `IN` list as a false predicate rather than emitting invalid SQL. A
clause on that would make the whole dependency visible in one place.

## Conformance check

The conformance and acceptance-criteria walk was completed in the first pass against the committed
diff and is not repeated here; it found the criteria met, no out-of-scope substance, and no design
divergence. This pass re-checked only what the fix touched:

- **Acceptance criteria** — unaffected. The fix pass changes no production behaviour except the
  `updateById` collection guard (S4), which is outside T2's criteria entirely; the test changes
  preserve every assertion, altering only how each test locates its own invite.
- **Out of scope** — the `updateById` guard change is the only production-code change, and it is
  unrelated to share invites. It came in as an accepted nit, so it is deliberate rather than
  accidental, but it is unpinned (S4).
- **Design divergence** — none. `T2-task-design.md` does not speak to either changed line.
- **Not checkable** — unchanged from the first pass: the two pre-ship manual probes that need real
  production data, namely the duplicate-owner check that `V21__` would abort on via
  `uq_resource_permission_owner`, and the byte-identical `limit_usage` comparison across the `RECIPE`
  slice repoint. The HLD is explicit that both are verified by hand.

## Documentation check

`CLAUDE.md` scopes documentation to `docs/`, with `docs/INDEX.md` as the mandatory entry point and
the per-module `module.md` / `api.md` / `db.md` sets as the substance.

- `docs/INDEX.md` — **in sync**. The stale `recipe_permission` entry is gone.
- `docs/backend/modules/recipes/{module.md,db.md}` — **in sync**, and now framed in the present tense
  with no task references. The claim that `recipes` neither reads nor writes `recipe_permission` was
  verified: no `.java` or non-historical `.sql` file under `backend/src/main/` touches either legacy
  table.
- `docs/backend/modules/permissions/{module.md,db.md}` — **in sync**, same framing check.
- `docs/backend/modules/recipes/api.md`, `docs/backend/modules/limits/{module.md,db.md}` — unchanged
  by this pass and still accurate.
- `docs/backend/modules/limits/db.md:62`, `docs/backend/modules/shopping-lists/db.md:35` — **stale
  framing**, both citing `docs/tasks/2026-08-26-share-invites/tasks.md` from module documentation.
  Pre-existing from T1 (`d22088f`), outside this diff, and left for T3's docs pass.
- Pre-existing drift, not this task's: `recipes/module.md`'s file tree still lists
  `RecipeIngredientsResult.java` / `RecipeWithIngredients.java` while `RecipeFacade` returns
  `RecipeInfoResult` / `RecipeInfo`.

## Not reviewed

- **Mobile.** Untouched by design. The three client-visible breaks T2 introduces — `/shared_users` →
  `/permissions`, the now-required `role` field, and share/unshare moving 200 → 204 — are recorded in
  `tasks.md` > T5. Verified that they are written down; the Dart code was not checked against them.
- **Production data.** See **Not checkable**.
- **Manual API walkthrough.** Verification was the automated suite (`./mvnw -o test` from `backend/`:
  320 tests, 0 failures, 2 skipped) plus reading; `backend/http/` was not run against a live
  `dev`-profile backend.
