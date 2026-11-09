# T1: Self-service account deletion endpoint — Implementation Plan

**Date:** 2026-09-04

## Required reading

**Docs & standards** (from `docs/INDEX.md`)

- `docs/backend/standards/module-structure.md` — the facade rule behind the three new facades, the
  application-service rule behind the permissions purge, the "a service owns its repositories" rule,
  and the `@Slf4j` logging levels every new method follows.
- `docs/backend/standards/java-patterns.md` — derived repository methods over `@Query`, and the
  package-private visibility rule (only the facades and `Settings*` types are new public surface).
- `docs/backend/standards/integration-tests.md` — `@IntegrationTest`, `TestIdentities.freshToken()`,
  `TestRestClients.forToken`, seeding and reading through the module's own surface, the
  `@Nested @LimitsEnabled` rule, and the "never add class-level `properties`" context-count rule.
- `docs/backend/modules/limits/module.md` — the config-subject vs usage-subject split, which is why
  the `SHOPPING_LIST_ITEM` usage row is keyed by a list UUID and unreachable from an email sweep.
- `docs/backend/modules/permissions/module.md` — the invite handshake and the module boundary the
  new `ownedResources` projection sits inside.

**Design & ADRs**

- `docs/tasks/2026-09-04-account-deletion/plans/T1-task-design.md` — the whole document; in
  particular *Interfaces and method signatures*, *Data flow* and *Decisions made*, which this plan
  does not re-decide.
- `docs/ADRs/0010-account-deletion-orchestrated-over-per-module-purges.md` — why the purge is
  orchestrated per module, why `permissions` goes last, and why no transaction spans the sequence.
- `docs/tasks/2026-09-04-account-deletion/HLD.md` > Feature areas > Backend — `settings` module, and
  > Backend — per-module account purge.
- `docs/tasks/2026-09-04-account-deletion/research/account-footprint.md` — `limit_config` overrides,
  list-UUID-keyed `SHOPPING_LIST_ITEM` usage, third parties' rows on the subject's resources.

**Code to mirror**

- `backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeService.java:205` (`deleteById`) — the
  delete path every recipe purge must route through: event, `permissionsFacade.resourceDeleted`,
  repository delete, S3 image delete, limits release.
- `backend/src/main/java/xyz/stasiak/recipai/permissions/PermissionsApplicationService.java`
  (`resourceDeleted`) — the sibling coordinated operation `accountDeleted` mirrors: `@Transactional`,
  one call into each service, one `log.info` at the end.
- `backend/src/main/java/xyz/stasiak/recipai/provisioning/ProvisioningFacade.java` — the minimal
  one-method facade shape; `RecipeFacade` / `PermissionsFacade` for the `@Slf4j` + `log.debug`
  delegation the three new facades follow.
- `backend/src/main/java/xyz/stasiak/recipai/permissions/InviteController.java` — a package-private
  `@RestController` reading `jwt.getClaimAsString("email")` and returning
  `ResponseEntity.noContent().build()`; the shape `SettingsController` copies.
- `backend/src/main/java/xyz/stasiak/recipai/permissions/ResourcePermissionRepository.java` — derived
  query naming over the `@EmbeddedId` (`findByIdEmailAnd…`, `deleteByIdResourceTypeAnd…`).
- `backend/src/test/java/xyz/stasiak/recipai/recipes/RecipeIntegrationTest.java` — the fresh-token
  `@BeforeEach`, the private `RestClient` helpers, and (from line 1399) the
  `@Nested @LimitsEnabled` class with its `setLimitQuota` `JdbcClient` helper and its comment
  explaining why `JdbcClient` is used at all.
- `backend/src/test/java/xyz/stasiak/recipai/planning/MealPlanIntegrationTest.java` — how meal-plan
  entries are created and read back through `GET /meal-plans/calendar`.
- `backend/http/collections.http` — the `.http` request-file conventions (`@baseUrl`, `@user`,
  `### ` headings).

## File inventory

**New module**

- **CREATE** `backend/src/main/java/xyz/stasiak/recipai/settings/SettingsController.java` —
  package-private `@RestController` on `/settings`, `DELETE /account` returning 204.
- **CREATE** `backend/src/main/java/xyz/stasiak/recipai/settings/AccountDeletionService.java` —
  package-private `@Service` owning the six-call purge order.

**New facades**

- **CREATE** `backend/src/main/java/xyz/stasiak/recipai/planning/PlanningFacade.java` — public
  one-method facade delegating to `MealPlanService`.
- **CREATE** `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListsFacade.java` —
  public one-method facade delegating to `ShoppingListService`.
- **CREATE** `backend/src/main/java/xyz/stasiak/recipai/recipes/collections/RecipesCollectionsFacade.java`
  — public one-method facade delegating to `RecipesCollectionService`.

**Purge entry points**

- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeFacade.java` — add
  `deleteAllByEmail`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/recipes/RecipeService.java` — add
  `@Transactional deleteAllByEmail` looping `deleteById`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/recipes/collections/RecipesCollectionService.java`
  — add package-private `@Transactional deleteAllByEmail`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListService.java` — add
  package-private `@Transactional deleteAllByEmail`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/planning/MealPlanService.java` — add
  package-private `@Transactional deleteAllByEmail` looping `delete(id, email)`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/limits/LimitsFacade.java` — add
  `deleteAllBySubject`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/limits/LimitService.java` — add
  `@Transactional deleteAllBySubject` sweeping usage and the config override.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/permissions/PermissionsFacade.java` — add
  `ownedResources` and `deleteAllByEmail`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/permissions/PermissionsApplicationService.java`
  — add `@Transactional accountDeleted`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/permissions/PermissionService.java` — add
  `ownedResources` and `clearForEmail`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/permissions/InviteService.java` — add
  `clearForEmail` clearing both directions.

**Repositories**

- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/permissions/ResourcePermissionRepository.java`
  — add `findByIdEmailAndIdResourceTypeAndRole`, `deleteByIdEmail`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/permissions/ResourceInviteRepository.java` —
  add `deleteByEmail`, `deleteByInvitedBy`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/limits/LimitUsageRepository.java` — add
  derived `deleteAllByIdSubject`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/limits/LimitConfigRepository.java` — add
  derived `deleteBySubject`, the table's first write method.

**Security**

- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/config/security/SecurityConfig.java` — add
  `"/settings/**"` to the `.authenticated()` matcher list.

**Tests and dev tooling**

- **CREATE** `backend/src/test/java/xyz/stasiak/recipai/settings/AccountDeletionIntegrationTest.java`
  — the end-state suite, including a `@Nested @LimitsEnabled` class for the quota override.
- **CREATE** `backend/http/settings.http` — the endpoint's entry in the local request suite.

No migration, no schema change, no new dependency, no change to `pom.xml`. No existing test file
changes behaviour: every module's current suite must keep passing untouched.

## Step-by-step plan

1. **Permissions: ownership projection and account purge** — add
   `findByIdEmailAndIdResourceTypeAndRole` and `deleteByIdEmail` to `ResourcePermissionRepository`;
   `deleteByEmail` and `deleteByInvitedBy` to `ResourceInviteRepository`. Add
   `PermissionService.ownedResources` (maps the found rows to `getId().resourceId()`, mirroring
   `accessibleResources`) and `PermissionService.clearForEmail`; add `InviteService.clearForEmail`
   calling both invite deletes. Coordinate them in `PermissionsApplicationService.accountDeleted`
   (`@Transactional`, `log.info` at the end, like `resourceDeleted`). Expose both on
   `PermissionsFacade` (`ownedResources` → `permissionService`, `deleteAllByEmail` →
   `applicationService`), each with the module's `log.debug` line.
   - Files: `permissions/ResourcePermissionRepository.java`,
     `permissions/ResourceInviteRepository.java`, `permissions/PermissionService.java`,
     `permissions/InviteService.java`, `permissions/PermissionsApplicationService.java`,
     `permissions/PermissionsFacade.java`
   - Verify: `./mvnw test -Dtest='PermissionsModuleArchitectureTest,InviteIntegrationTest'` passes —
     the ArchUnit run proves no new public type slipped in and neither service reached the other's
     repository; the integration run proves the new derived property paths resolve at context
     startup.

2. **Limits: subject sweep** — add derived `deleteAllByIdSubject` to `LimitUsageRepository` (a
   derived delete over the `@EmbeddedId`'s `subject` component, alongside the existing native
   statements) and `deleteBySubject` to `LimitConfigRepository`. Add
   `LimitService.deleteAllBySubject` (`@Transactional`, both deletes, one `log.debug` reporting the
   usage row count) and `LimitsFacade.deleteAllBySubject`. Keep the naming subject-based — no
   "email" anywhere in the module.
   - Files: `limits/LimitUsageRepository.java`, `limits/LimitConfigRepository.java`,
     `limits/LimitService.java`, `limits/LimitsFacade.java`
   - Verify: `./mvnw test -Dtest='LimitsModuleArchitectureTest,LimitsIntegrationTest'` passes.

3. **Four resource-module purges and the three new facades** — add `deleteAllByEmail` to
   `RecipeService`, `RecipesCollectionService`, `ShoppingListService` and `MealPlanService`, each
   `@Transactional` (using the `@Transactional` import the file already has — `jakarta.transaction`
   in `recipes` and `planning`, `org.springframework.transaction.annotation` in the others), each
   iterating `permissionsFacade.ownedResources(<MODULE>_RESOURCE, email)` and calling the module's
   existing per-resource delete (`deleteById(id, email)`, or `delete(id, email)` in `planning`).
   Add `RecipeFacade.deleteAllByEmail`, and create `RecipesCollectionsFacade`, `ShoppingListsFacade`
   and `PlanningFacade` as public `@Service @RequiredArgsConstructor @Slf4j` classes with one
   `log.debug` + delegation each. Keep every new service method package-private —
   `RecipesCollectionService` is public but its purge must not be reachable from `recipes`.
   - Files: `recipes/RecipeService.java`, `recipes/RecipeFacade.java`,
     `recipes/collections/RecipesCollectionService.java`,
     `recipes/collections/RecipesCollectionsFacade.java`, `shoppinglists/ShoppingListService.java`,
     `shoppinglists/ShoppingListsFacade.java`, `planning/MealPlanService.java`,
     `planning/PlanningFacade.java`
   - Verify: `./mvnw test -Dtest='RecipeIntegrationTest,RecipesCollectionIntegrationTest,ShoppingListIntegrationTest,MealPlanIntegrationTest'`
     passes unchanged.

4. **The `settings` module and the security matcher** — create `AccountDeletionService` with the six
   facade calls in the fixed order (`planning`, `shoppinglists`, `recipes`, `recipes.collections`,
   `limits`, `permissions`), `log.info` on entry and exit, and deliberately no `@Transactional`
   (leave the one-line comment pointing at ADR-0010). Create `SettingsController` on `/settings`
   with `DELETE /account`. Add `"/settings/**"` to the `.authenticated()` list in `SecurityConfig`.
   - Files: `settings/AccountDeletionService.java`, `settings/SettingsController.java`,
     `config/security/SecurityConfig.java`
   - Verify: `./mvnw test -Dtest=RecipAiApplicationTests` passes (context loads with the new beans),
     then against a locally running `dev` backend:
     `curl -sS -o /dev/null -w '%{http_code}\n' -X DELETE -H "Authorization: Bearer alice" localhost:8080/settings/account`
     prints `204`, and the same call without the header prints `401`.

5. **Integration suite** — write `AccountDeletionIntegrationTest` per the Test plan below: fresh
   tokens per role in one `@BeforeEach`, `TestRestClients.forToken` clients, seeding through the
   HTTP API, reading back through the API and the injected `LimitsFacade`.
   - Files: `src/test/java/xyz/stasiak/recipai/settings/AccountDeletionIntegrationTest.java`
   - Verify: `./mvnw test -Dtest=AccountDeletionIntegrationTest` passes.

6. **Local request suite** — add `settings.http` following the existing files' `@baseUrl` / `@user`
   header and `### ` comment style, with a warning that the call is destructive for `{{user}}`.
   - Files: `backend/http/settings.http`
   - Verify: the request runs from the IDE against a local `dev` backend and returns 204; then run
     the full `tasks.md` > T1 "How to verify" sequence.

## Test plan

**Unit tests**

_N/A — every behaviour here is a cross-module sequence over real rows; the project's only unit tests
(`ProvisioningServiceTest`, `LimitPeriodTest`) cover pure logic, and there is none new to cover._

**Integration tests**

`AccountDeletionIntegrationTest` (`@IntegrationTest`) — outer class, limits disabled, subjects
`owner`, `editor` and `stranger` minted fresh in one `@BeforeEach`:

- `shouldReturnNoContentWhenAccountOwnsNothing` — `DELETE /settings/account` as an untouched fresh
  subject returns 204.
- `shouldRejectUnauthenticatedDeletion` — the same call with no `Authorization` header returns 401.
- `shouldRemoveEveryOwnedResourceWhenAccountIsDeleted` — seed a recipe, a collection, a shopping
  list with two items and a meal plan; after the purge `GET /recipes`, `GET /collections`,
  `GET /shopping-lists` and `GET /meal-plans` are all empty for the caller.
- `shouldRemoveSharedResourceForItsEditorWhenOwnerIsDeleted` — `editor` accepts an invite to the
  owner's recipe; after the purge the recipe is absent from `editor`'s `GET /recipes` and
  `GET /recipes/{id}` as `editor` returns 404.
- `shouldRemoveInvitesAddressedToTheDeletedAccount` — `stranger` invites `owner` to a recipe; after
  the purge `GET /recipes/{id}/permissions` as `stranger` no longer lists the owner's pending row.
- `shouldRemoveInvitesSentByTheDeletedAccount` — the owner invites `stranger` to a recipe; after the
  purge `GET /invites` as `stranger` is empty.
- `shouldRewriteThirdPartyMealPlanEntriesWhenOwnersRecipeIsPurged` — the owner shares a recipe with
  `editor`, who accepts and adds it to their *own* meal plan; after the purge, reading
  `GET /meal-plans/calendar?startDate=…&endDate=…&planIds={editorPlanId}` as `editor` shows the entry
  with `placeholderText` equal to the recipe's name and `recipeId` and `servingSize` null. This is
  the case that fails if a purge stops routing through `RecipeService.deleteById`.
- `shouldLeaveOtherPeoplesResourcesIntact` — a shopping list owned by `stranger` that `owner` holds
  as EDITOR still resolves for `stranger` after the purge (`GET /shopping-lists/{id}` 200, items
  intact), while `owner`'s EDITOR row is gone.
- `shouldResetUsageWhenAccountIsDeleted` — after seeding a recipe, a collection, a meal plan and a
  shopping list with items, `limitsFacade.getBalance(ownerEmail, <resource>)` is empty for `RECIPE`,
  `RECIPES_COLLECTION`, `SHOPPING_LIST` and `MEAL_PLAN`, and
  `limitsFacade.getBalance(listId.toString(), "SHOPPING_LIST_ITEM")` is empty for the UUID of the
  purged list.
- `shouldSucceedWhenCalledTwice` — a second `DELETE /settings/account` for the same subject also
  returns 204, which is what makes ADR-0010's "call it again" repair safe.

`@Nested @LimitsEnabled class QuotaOverrideCleared`:

- `shouldRestoreDefaultQuotaWhenAccountIsDeleted` — seed a `limit_config` override for the owner's
  email with `JdbcClient` (the table has no write API — carry the one-line comment
  `integration-tests.md` asks for, as `RecipeIntegrationTest.setLimitQuota` does), assert
  `limitsFacade.getQuota(ownerEmail, "RECIPE")` reports the override, purge, then assert it reports
  the same value a second fresh subject with no override resolves to. Compare against that subject
  rather than the literal shipped default (5, from `V16__owner_scoped_limit_config.sql`), so an
  operator retuning the number does not break the test.

Existing suites act as regression coverage and must pass unchanged:
`RecipeIntegrationTest`, `RecipesCollectionIntegrationTest`, `ShoppingListIntegrationTest`,
`MealPlanIntegrationTest`, `InviteIntegrationTest`, `LimitsIntegrationTest`,
`LimitsApiIntegrationTest`, `PermissionsModuleArchitectureTest`, `LimitsModuleArchitectureTest`.

**Flutter widget/integration tests**

_N/A — T1 is backend only; the mobile flow is T4._

**Manual verification**

- The full `tasks.md` > T1 "How to verify" curl sequence against a local `dev` backend started with
  `./recipai.sh`.
- S3 prefix removal — `NoopS3Service` stands in for the bucket across the suite, so the only place
  the real delete is observed is a manual run against a configured bucket, or the `INFO`/`ERROR`
  lines `RecipeImagesService.deleteAllImages` emits.

## Verification checklist

- [ ] `./mvnw verify` passes — the whole suite, including both ArchUnit classes
- [ ] The Spring context still forks only the two expected variants: no `properties = `,
      `@TestPropertySource` or ad hoc `@Import` was added to an `@IntegrationTest` class, and the
      quota case lives in a `@Nested @LimitsEnabled` class
- [ ] No new public type outside the four facades (`LimitsModuleArchitectureTest` and
      `PermissionsModuleArchitectureTest` are the gate for two of the six modules)
- [ ] `tasks.md` > T1 "How to verify" succeeds end-to-end against a local `dev` backend
- [ ] A second `DELETE /settings/account` for an already-purged subject still returns 204
- [ ] `INFO` logs on the happy path are the two `AccountDeletionService` lines plus the modules'
      existing per-resource lines — no stack traces, no `ERROR`
- [ ] No new compiler warnings
- [ ] Task design's *Assumptions to verify* are resolved or explicitly deferred — assumption 4 (S3)
      is already confirmed in `RecipeImagesService.deleteAllImages:159`, which catches
      `S3StorageException` and logs; assumptions 1–3 are settled by the integration suite or
      accepted as stated
- [ ] No migration was added and `pom.xml` is untouched

## Risks surfaced during planning

- **Risk:** `PermissionService.clearForEmail` removes the subject's OWNER rows last, so any resource
  type whose purge is missing or silently skipped is left with no owner row at all.
  **Why it matters:** ownership lives only in `resource_permission`. A resource that survives the
  sequence with its OWNER row deleted is invisible to every list endpoint and undeletable through
  any API — worse than a partial purge, and not something re-running the endpoint repairs.
  **Mitigation:** the six calls are one method; keep them together and never let a resource module
  ship without its purge. Worth an ArchUnit or a comment in `AccountDeletionService` naming the
  invariant; raise with the user before adding a rule, since it is beyond the task design.

- **Risk:** four of the five new repository methods are derived queries over property paths that do
  not exist until Spring Data resolves them at context startup — `deleteAllByIdSubject` over
  `LimitUsage`'s `@EmbeddedId`, and `findByIdEmailAndIdResourceTypeAndRole` / `deleteByIdEmail` over
  `ResourcePermissionId`.
  **Why it matters:** a wrong path compiles cleanly and fails every `@IntegrationTest` in the JVM
  with a context-load error, which reads as an unrelated mass failure.
  **Mitigation:** steps 1 and 2 each end on an integration test rather than a compile, so the
  property paths are proven before anything is built on them.

- **Risk:** the quota-override test's obvious form asserts the shipped `RECIPE` default (5) as a
  literal after the override is purged.
  **Why it matters:** `integration-tests.md` explicitly warns against coupling a test to an
  operator-tunable production number.
  **Mitigation:** already folded into the test plan — assert the purged subject resolves to whatever
  a second fresh subject with no override resolves to, not to a literal.

- **Risk:** the task design lists a *Documentation* deliverable (`docs/backend/modules/settings/`,
  `docs/INDEX.md`, `docs/project/architecture.md`, five module docs).
  **Why it matters:** this plan covers code only; the doc refresh belongs to the `docs-updating`
  step, and dropping it here would lose it.
  **Mitigation:** run `docs-updating` after the implementation lands, with the task design's
  Documentation section as its input.
