# T1: Self-service account deletion endpoint — Task Design

**Date:** 2026-09-04

## Summary

A new `settings` module exposes `DELETE /settings/account`, takes the subject from the caller's own
token, and runs one fixed sequence of facade calls: `planning`, `shoppinglists`, `recipes`,
`recipes.collections`, then `limits`, then `permissions`. Each of those six modules gains a purge
entry point on its facade that iterates the resources `permissions` reports the email as owning and
deletes each one through the module's *existing* per-resource delete path, so the `RecipeDeleted`
event, the S3 prefix delete, the limits release and `PermissionsFacade.resourceDeleted` all fire
exactly as they do for a single deletion. No migration, no new table, no schema change.

## Components and responsibilities

### New module

- **`SettingsController`** (CREATE, `backend/src/main/java/xyz/stasiak/recipai/settings/`) —
  package-private `@RestController` on `/settings`. Reads `jwt.getClaimAsString("email")` and calls
  `AccountDeletionService`. Returns `204 No Content`. Nothing else lives on it in T1.
- **`AccountDeletionService`** (CREATE, same package) — package-private `@Service` owning the purge
  order, and that is its entire domain knowledge: no repositories, no entity imports, no notion of
  what any other module stores. Takes an email rather than a `Jwt`, which is the seam T2's admin
  endpoint reuses unchanged. Deliberately **not** `@Transactional` — per ADR-0010 the boundaries stay
  per module and nothing spans the sequence.

The module has no `dto/`, no `exception/`, no exception handler and no facade: it raises no
module-specific exception and nothing outside it calls in.

### Modified — security

- **`SecurityConfig`** (MODIFY, `config/security/SecurityConfig.java`) — `"/settings/**"` joins the
  `.authenticated()` matcher list. `.anyRequest().denyAll()` and the rest of the chain are untouched.

### New facades — modules that have none today

`recipes.collections`, `shoppinglists` and `planning` expose no facade, and their services are
package-private (`RecipesCollectionService` is public but is a `recipes`-internal collaborator, not a
cross-module surface). `settings` must not reach any of them directly, so each gains a facade holding
exactly one method in T1.

- **`RecipesCollectionsFacade`** (CREATE, `recipes/collections/`)
- **`ShoppingListsFacade`** (CREATE, `shoppinglists/`)
- **`PlanningFacade`** (CREATE, `planning/`)

Each is a public `@Service` that logs at `debug` and delegates to its module's service, matching
`RecipeFacade` and `PermissionsFacade`.

### Modified — the six purge entry points

- **`RecipeFacade` / `RecipeService`** (MODIFY, `recipes/`) — `deleteAllByEmail` iterates the
  `RECIPE` ids the email owns and calls the existing `deleteById(id, email)` for each, so images and
  their S3 objects go, `RecipeDeleted` publishes, and per-recipe permissions and invites are cleared.
- **`RecipesCollectionsFacade` / `RecipesCollectionService`** (MODIFY) — same shape over
  `RECIPES_COLLECTION` through `deleteById(id, email)`. Recipes belonging to other people that sat in
  a deleted collection are detached by `recipes.recipes_collection_id ON DELETE SET NULL`, not
  deleted.
- **`ShoppingListsFacade` / `ShoppingListService`** (MODIFY) — same shape over `SHOPPING_LIST`
  through `deleteById(id, email)`. Items go by `ON DELETE CASCADE` and the per-list
  `SHOPPING_LIST_ITEM` usage row by the `limitsFacade.clear(id.toString(), …)` that path already
  makes.
- **`PlanningFacade` / `MealPlanService`** (MODIFY) — same shape over `MEAL_PLAN` through the
  existing `delete(id, email)` (note the method is `delete`, not `deleteById`). Entries go by
  `ON DELETE CASCADE`.
- **`LimitsFacade` / `LimitService`** (MODIFY, `limits/`) — `deleteAllBySubject` removes every
  `limit_usage` row for the subject across every resource, and the subject's `limit_config` override
  row if one exists, so a re-registered account resolves the defaults again. Named for a *subject*,
  not an email: `LimitsModuleArchitectureTest` forbids the module any domain knowledge, and "email"
  is domain.
- **`PermissionsFacade` / `PermissionsApplicationService` / `PermissionService` / `InviteService`**
  (MODIFY, `permissions/`) — `deleteAllByEmail` removes every row carrying the email: permission rows
  in both roles, invites addressed to them, and invites they sent. It touches both services, so the
  `@Transactional` boundary sits on `PermissionsApplicationService`, alongside `resourceDeleted` and
  the other four coordinated operations. `PermissionsFacade` also gains `ownedResources`, the
  OWNER-only projection the four resource purges are built on.

### Repositories

- **`ResourcePermissionRepository`** (MODIFY) — `findByIdEmailAndIdResourceTypeAndRole`,
  `deleteByIdEmail`.
- **`ResourceInviteRepository`** (MODIFY) — `deleteByEmail`, `deleteByInvitedBy`.
- **`LimitUsageRepository`** (MODIFY) — `deleteAllByIdSubject`, derived over the embedded id's
  `subject` component.
- **`LimitConfigRepository`** (MODIFY) — `deleteBySubject`. This is the table's first write method;
  it has no other write API by design.

### Tests

- **`AccountDeletionIntegrationTest`** (CREATE, `src/test/java/xyz/stasiak/recipai/settings/`) —
  `@IntegrationTest`, fresh tokens per role via `TestIdentities.freshToken()`, clients via
  `TestRestClients.forToken`, seeding and reading back through the HTTP API and injected facades per
  `docs/backend/standards/integration-tests.md`. Cases:
  - `shouldSucceedWhenAccountOwnsNothing` — 204 against an untouched fresh subject.
  - `shouldRemoveEveryOwnedResourceWhenAccountIsDeleted` — a recipe, a collection, a shopping list
    with items and a meal plan; afterwards all four list endpoints are empty for the caller.
  - `shouldRemoveSharedResourceForItsEditorWhenOwnerIsDeleted` — a third party accepts an invite to
    the owner's recipe; after the purge their list no longer shows it and fetching it is a 404.
  - `shouldRemovePendingInvitesInBothDirections` — an invite the subject sent is gone from the
    recipient's `/invites`; an invite addressed to the subject is gone from the sender's
    `/recipes/{id}/permissions`.
  - `shouldRewriteThirdPartyMealPlanEntriesWhenOwnersRecipeIsPurged` — the entry becomes a
    placeholder carrying the recipe's name, with `recipeId` and `servingSize` cleared. This is the
    test that fails if a purge ever stops routing through `RecipeService.deleteById`.
  - `shouldLeaveOtherPeoplesResourcesIntact` — a resource the subject held only as EDITOR survives;
    only their EDITOR row goes.
  - `shouldResetUsageWhenAccountIsDeleted` — `LimitsFacade.getBalance` is empty for the email, and
    for the UUID of a shopping list they owned (`SHOPPING_LIST_ITEM`).
  - A `@Nested @LimitsEnabled` class for the override: seed a `limit_config` row for the subject with
    `JdbcClient` (the table has no write API — leave the one-line comment the standard asks for),
    then assert `LimitsFacade.getQuota` reports the shipped default after the purge.

  S3 object removal is not asserted: `NoopS3Service` stands in for the bucket across the suite.

### Documentation

- **`docs/backend/modules/settings/module.md`, `api.md`** (CREATE) — the module's description, file
  tree, purge order and the endpoint contract.
- **`docs/INDEX.md`, `docs/project/architecture.md`** (MODIFY) — the new module's index entry and its
  one-line role.
- **`docs/backend/modules/{recipes,shopping-lists,planning,permissions,limits}/module.md`** (MODIFY)
  — each facade's new entry point in the file tree, and the behaviour lines for `ownedResources`, the
  permissions account purge and the limits subject purge.

## Interfaces and method signatures

```java
// settings — new module
@RestController @RequestMapping("/settings")
class SettingsController {
    @DeleteMapping("/account")
    ResponseEntity<Void> deleteOwnAccount(@AuthenticationPrincipal Jwt jwt);   // 204
}

@Service
class AccountDeletionService {
    void deleteAccount(String email);          // no @Transactional — see ADR-0010
}
```

```java
// the six purge entry points, on facades only
public class RecipeFacade              { public void deleteAllByEmail(String email); }
public class RecipesCollectionsFacade  { public void deleteAllByEmail(String email); }   // new class
public class ShoppingListsFacade       { public void deleteAllByEmail(String email); }   // new class
public class PlanningFacade            { public void deleteAllByEmail(String email); }   // new class
public class LimitsFacade              { public void deleteAllBySubject(String subject); }
public class PermissionsFacade         { public void deleteAllByEmail(String email); }
```

```java
// permissions — the ownership projection the four resource purges iterate
public class PermissionsFacade {
    public Set<UUID> ownedResources(String resourceType, String email);
}

class PermissionService {
    Set<UUID> ownedResources(String resourceType, String email);
    void clearForEmail(String email);
}

class InviteService {
    void clearForEmail(String email);          // both directions: addressed to, and sent by
}

class PermissionsApplicationService {
    @Transactional void accountDeleted(String email);
}
```

```java
// module services — each @Transactional, each private to its module
class RecipeService            { @Transactional void deleteAllByEmail(String email); }
class RecipesCollectionService { @Transactional void deleteAllByEmail(String email); }
class ShoppingListService      { @Transactional void deleteAllByEmail(String email); }
class MealPlanService          { @Transactional void deleteAllByEmail(String email); }
class LimitService             { @Transactional void deleteAllBySubject(String subject); }
```

```java
// repositories
interface ResourcePermissionRepository extends JpaRepository<ResourcePermission, ResourcePermissionId> {
    List<ResourcePermission> findByIdEmailAndIdResourceTypeAndRole(String email, String resourceType, ResourceRole role);
    void deleteByIdEmail(String email);
}

interface ResourceInviteRepository extends JpaRepository<ResourceInvite, UUID> {
    void deleteByEmail(String email);
    void deleteByInvitedBy(String invitedBy);      // unindexed column — see Decisions made
}

interface LimitUsageRepository extends JpaRepository<LimitUsage, LimitUsageId> {
    long deleteAllByIdSubject(String subject);   // subject is a component of the embedded id
}

interface LimitConfigRepository extends JpaRepository<LimitConfig, UUID> {
    void deleteBySubject(String subject);
}
```

## Data flow

1. `DELETE /settings/account` reaches `SettingsController` through the chain's new `/settings/**`
   authenticated matcher. The controller takes `email` from the `email` claim — the same way every
   other controller does — and calls `AccountDeletionService.deleteAccount(email)`.
2. **`planning`** — `PlanningFacade.deleteAllByEmail` → `MealPlanService`, one transaction: for each
   `MEAL_PLAN` id in `ownedResources`, the existing `delete(id, email)` clears the plan's permissions
   and invites, deletes the plan (entries cascade) and releases one `MEAL_PLAN` unit.
3. **`shoppinglists`** — same shape over `SHOPPING_LIST`. Each `deleteById` also clears the list's own
   `SHOPPING_LIST_ITEM` usage row, which is keyed by the list's UUID and is unreachable from an
   email-keyed sweep.
4. **`recipes`** — same shape over `RECIPE`. Each `deleteById` publishes `RecipeDeleted`, clears the
   recipe's permissions and invites, deletes the row (images cascade) and deletes the
   `recipes/<id>/` S3 prefix. The events are `BEFORE_COMMIT`, so `MealPlanService.handleRecipeDeleted`
   runs once at the end of this module's single transaction and rewrites the matching entries into
   named placeholders — by then only *other people's* plans hold any, because step 2 already removed
   the subject's own.
5. **`recipes.collections`** — same shape over `RECIPES_COLLECTION`. The subject's own recipes are
   already gone, so the `ON DELETE SET NULL` detach touches only third parties' recipes that sat in a
   deleted collection, which is the behaviour to keep.
6. **`limits`** — `deleteAllBySubject(email)` sweeps whatever the per-resource releases left: the
   `EXTRACTION` row (never released), any row a release decremented to zero without removing, and the
   subject's `limit_config` override.
7. **`permissions`** — `deleteAllByEmail(email)`, last, because steps 2–5 depend on it to answer
   "what does this email own". One transaction removes the subject's permission rows in both roles,
   the invites addressed to them, and the invites they sent.
8. The controller returns `204`. Nothing is reported about how much was removed.

Branch — **nothing owned:** every step's `ownedResources` returns an empty set, every delete-by-email
removes zero rows, and the response is still `204`. No step treats "nothing to do" as an error.

Branch — **failure part-way:** the module that threw rolls back, the modules before it stay purged,
the modules after it are untouched, and the caller gets the failing module's status. Nothing records
the partial state; the repair is to call the endpoint again (ADR-0010), which is safe because every
purge succeeds against an email with nothing left to remove.

## Pseudo-code

The purge order, stated in exactly one place:

```
AccountDeletionService.deleteAccount(email):
    log.info("Deleting account {}", email)

    # Resource modules first: each asks permissions what the email owns.
    planningFacade.deleteAllByEmail(email)
    shoppingListsFacade.deleteAllByEmail(email)
    recipeFacade.deleteAllByEmail(email)              # RecipeDeleted fires here
    recipesCollectionsFacade.deleteAllByEmail(email)  # after recipes: only 3rd-party rows to detach

    # Then usage, which the deletes above have been releasing as they went.
    limitsFacade.deleteAllBySubject(email)

    # permissions LAST: it is what told every module above what to delete.
    permissionsFacade.deleteAllByEmail(email)

    log.info("Deleted account {}", email)
```

Every resource module's purge is the same four lines — shown for `recipes`, the one with the most
hanging off its delete path:

```
@Transactional
RecipeService.deleteAllByEmail(email):
    for id in permissionsFacade.ownedResources(RECIPE_RESOURCE, email):
        deleteById(id, email)      # unchanged path: RecipeDeleted, S3 prefix,
                                   # permissionsFacade.resourceDeleted, limits release
```

The owner check inside `deleteById` passes by construction — `ownedResources` returns exactly the ids
whose OWNER row carries this email — and it keeps passing under T2's admin endpoint, because the
check is against the subject, not the caller. A resource the subject only holds as EDITOR is never
in the set and is never deleted; its EDITOR row goes in step 7.

The two sweeps, which take no per-resource path because there is none to take:

```
@Transactional
LimitService.deleteAllBySubject(subject):
    usageRows = limitUsageRepository.deleteAllByIdSubject(subject)
    limitConfigRepository.deleteBySubject(subject)      # the per-user override, if any
    log.debug("Cleared {} usage rows and any quota override for subject {}", usageRows, subject)

@Transactional
PermissionsApplicationService.accountDeleted(email):
    permissionService.clearForEmail(email)   # OWNER rows and EDITOR rows alike
    inviteService.clearForEmail(email)       # deleteByEmail + deleteByInvitedBy
    log.info("Cleared permissions and invites for {}", email)
```

## Decisions made

- **`DELETE /settings/account`, with `/settings/**` added to the security chain** — names the module
  and mirrors the mobile settings screen the endpoint serves. `/users/**` stays as it is.
- **Purge order `planning → shoppinglists → recipes → collections → limits → permissions`** — meal
  plans before recipes so `RecipeDeleted` has none of the subject's own entries left to rewrite into
  placeholders that are about to be deleted; recipes before collections so a collection delete has no
  owned recipe left to detach. Neither pairing changes the end state, only the churn to reach it.
- **No index on `resource_invite.invited_by`** — the table holds only *pending* invites (rows go on
  accept, decline, cancel and resource deletion), so it stays small; a sequential scan on a
  once-per-account operation is cheaper than the write amplification an extra index puts on every
  share. Revisit if the table ever grows.
- **`PermissionsFacade.ownedResources(resourceType, email)`** — "what does this email own" is a
  permissions question, and pushing the OWNER filter down keeps the same three-line stream out of
  four modules. It is a projection of a query the module already runs, so it adds no table knowledge
  anywhere.
- **A resource that `permissions` reports as owned but whose row is gone fails the purge** — drift is
  a bug and should be loud rather than silently tolerated. Note the consequence under *Assumptions to
  verify*: this is not a case a `try`/`catch` in the loop could soften, because the delete path joins
  the purge's transaction and a throw marks it rollback-only regardless.
- **`limits` names its entry point `deleteAllBySubject`, not `deleteAllByEmail`** — the module takes
  opaque subjects and `LimitsModuleArchitectureTest` enforces that it holds no domain knowledge.
- **Three new one-method facades rather than calling package-private services** — `recipes.collections`,
  `shoppinglists` and `planning` have no facade today, and `docs/backend/standards/module-structure.md`
  makes the facade the only cross-module surface.
- **The permissions purge sits on `PermissionsApplicationService`** — it touches both
  `PermissionService` and `InviteService`, which is exactly the shape the standard's application-service
  rule covers, and neither service may see the other's repository (ArchUnit enforces it).
- **The two email/subject sweeps derive their deletes rather than writing bulk statements** — the
  rows per account number in the single digits, and `docs/backend/standards/java-patterns.md` keeps
  `@Query` for what cannot be derived. `LimitUsageRepository`'s existing native statements are there
  because an `ON CONFLICT` upsert and a `GREATEST` decrement have no derived form, not as a house
  style to follow.
- **`AccountDeletionService` is not `@Transactional`** — per ADR-0010, boundaries stay per module and
  nothing spans the sequence.
- **The endpoint returns 204 with no body** — it reports nothing about how much was removed,
  consistent with every other delete in the system.
- **`AccountDeletionService` logs the email at INFO on entry and exit** — consistent with the grant,
  invite, share and unshare lines already there; the HLD explicitly accepts emails surviving in logs.

## Assumptions to verify

- **Assumption:** the "fail loudly on drift" choice is acceptable in the state it can leave an
  account in.
  **If wrong:** an account holding an OWNER row whose resource row is gone can never be deleted
  through the API — the module purge fails, and re-running it, which ADR-0010 names as the repair for
  a partial purge, fails identically. Recovery is a hand-run SQL delete of the stale row. The fix, if
  this ever bites, is to pre-filter each purge to ids whose row exists (`findAllById` over the owned
  set) rather than to catch the exception, which cannot work inside the joined transaction.
- **Assumption:** `RecipeDeleted` firing at `BEFORE_COMMIT` of one module-wide transaction, rather
  than once per recipe, is behaviourally identical.
  **If wrong:** the meal-plan placeholder rewrite lands at a different moment than expected. The
  third-party placeholder test is what catches this.
- **Assumption:** `settings` needs no exception handler in T1.
  **If wrong:** it needs a `SettingsExceptionHandler` the moment the module raises an exception of
  its own — which T2 is likely to, for an unknown or malformed admin subject.
- **Assumption:** S3 prefix deletion during a purge behaves as it does for a single recipe delete,
  swallowing an `S3StorageException` after logging.
  **If wrong:** a bucket failure could abort a purge mid-module. The integration suite cannot see
  this either way, because `NoopS3Service` stands in for the bucket.

## Required reading

- `docs/ADRs/0010-account-deletion-orchestrated-over-per-module-purges.md` — why the purge is
  orchestrated per module and why `permissions` goes last; the source of the ordering constraint.
- `HLD.md` > Feature areas > Backend — `settings` module, and > Backend — per-module account purge —
  the behaviours each entry point owes.
- `docs/tasks/2026-09-04-account-deletion/research/account-footprint.md` — the three things a naive
  reading of the requirements misses: `limit_config` overrides, list-UUID-keyed `SHOPPING_LIST_ITEM`
  usage, and third parties' rows on the subject's resources.
- `backend/…/recipes/RecipeService.java` `deleteById` — the delete path with the most behaviour
  hanging off it (event, S3, permissions, limits); the pattern every purge must route through.
- `backend/…/permissions/PermissionsApplicationService.java` `resourceDeleted` — the sibling
  coordinated operation the account purge mirrors.
- `docs/backend/standards/module-structure.md` — the facade rule behind the three new facades, and
  the application-service rule behind the permissions purge.
- `docs/backend/standards/integration-tests.md` — fresh subjects, `TestRestClients`, seeding through
  the module's own surface, and the `@Nested @LimitsEnabled` rule the quota-override test needs.
- `backend/…/limits/LimitsModuleArchitectureTest.java` and
  `backend/…/permissions/PermissionsModuleArchitectureTest.java` — the public-type and dependency
  rules both new entry points must not break.
