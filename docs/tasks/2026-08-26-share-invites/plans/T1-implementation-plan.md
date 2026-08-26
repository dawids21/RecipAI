# T1: `permissions` module, shopping lists migrated, and the invite handshake — Implementation Plan

**Date:** 2026-08-26

## Required reading

**Docs & standards** (from `docs/INDEX.md`)

- `docs/backend/standards/module-structure.md` — the facade-per-module rule, the
  `@ControllerAdvice` per module, the `/{id}/share` sub-action path convention, and the
  `log.debug` / `log.warn` / `log.info` split this module's logging must follow.
- `docs/backend/standards/java-patterns.md` — records for DTOs, the JPA entity shape
  (`@GeneratedValue(strategy = GenerationType.UUID)`, inline `createdAt`, explicit
  `equals`/`hashCode`), and the package-private-unless-crossing-a-boundary visibility rule the
  architecture test will enforce.
- `docs/backend/standards/integration-tests.md` — `@SpringBootTest(RANDOM_PORT)` +
  `TestcontainersConfiguration` + `TestSecurityConfiguration` + `RestClient`, the
  `shouldXxxWhenYyy` naming, and the rule that a test seeds and reads through the module's own
  business surface rather than through `JdbcClient`.
- `docs/backend/modules/limits/module.md` — the sibling shared module: what "module boundary"
  means here, how the facade is described, and how the repeatable recompute is documented.
- `docs/backend/modules/shopping-lists/{module.md,api.md,db.md}` — what this task's changes make
  stale.
- `docs/project/local-development.md` — `./recipai.sh start-backend`, the dev-profile
  `Bearer alice` → `alice@local.test` bypass, and the `backend/http/` suite conventions.

**Design & ADRs**

- `plans/T1-task-design.md` — the whole document; in particular **Interfaces and method
  signatures**, **Schema**, **Data flow** and **Pseudo-code**, which this plan assumes rather than
  re-derives.
- `tasks.md` > T1 — Scope, Out of scope, and the eight-step **How to verify** that gates the task.
- `docs/ADRs/0007-shared-permissions-module.md` — the domain-free boundary the architecture test
  holds, and why composition stays with the composing module (matters for T2, constrains T1's
  facade shape).
- `docs/ADRs/0008-invite-label-snapshot.md` — the label is supplied by the inviting module and
  stored as an opaque string, never refreshed.

**Code to mirror**

- `backend/src/main/java/xyz/stasiak/recipai/limits/LimitsFacade.java` — `@Service`
  `@RequiredArgsConstructor` `@Slf4j` public facade that logs and delegates to a package-private
  service; the exact shape `PermissionsFacade` copies.
- `backend/src/main/java/xyz/stasiak/recipai/limits/LimitsExceptionHandler.java` —
  `@RestControllerAdvice` with package-private handler methods building a `ProblemDetail` with
  `forStatusAndDetail(...)`, `setTitle(...)` and `setProperty(...)`; the shape
  `PermissionsExceptionHandler` copies. Copy the body, **not** the
  `ResponseEntity<ProblemDetail>` return type — that only exists so the 429 branch can add a
  `Retry-After` header, and it forces the status to be written twice. Return a bare `ProblemDetail`
  as `ShoppingListsExceptionHandler` does; Spring takes the status from the `ProblemDetail` itself.
- `backend/src/main/java/xyz/stasiak/recipai/limits/LimitsController.java` — package-private
  `@RestController`, email from `jwt.getClaimAsString("email")`; the shape `InviteController`
  copies.
- `backend/src/main/java/xyz/stasiak/recipai/limits/LimitExceededException.java` — a public
  exception carrying structured data with accessor methods (no `get` prefix); the shape
  `InviteRefusedException` copies.
- `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListPermission.java` and
  `ShoppingListPermissionId.java` — the `@EmbeddedId` + `@Embeddable record` composite-key pattern
  and the `hasOwnerRights()` / `hasEditorRights()` predicates that move into `ResourcePermission`.
- `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListPermissionRepository.java` —
  the `@Modifying @Query("DELETE FROM …")` and explicit-`@Query` idioms
  `ResourcePermissionRepository` follows, including the existing `ORDER BY slp.role DESC`.
- `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListService.java` — every call
  site this task rewrites: `requireEditorPermission`, `requireOwnerEmail`, `deleteById`,
  `shareShoppingList`, `unshareShoppingList`, `getSharedUsers`, `create`.
- `backend/src/test/java/xyz/stasiak/recipai/limits/LimitsModuleArchitectureTest.java` — the two
  ArchUnit rules, verbatim except for the package and the public-type predicate.
- `backend/src/test/java/xyz/stasiak/recipai/shoppinglists/ShoppingListIntegrationTest.java` — the
  suite `InviteIntegrationTest` mirrors: `restClient(token)` helpers, the three
  `TestSecurityConfiguration` tokens, `RestClientResponseException` status assertions, and the
  `@Nested @TestPropertySource` limits block.
- `backend/src/main/resources/db/migration/V15__limits_schema.sql` — migration style: bare table
  names (Flyway's `default-schema: recipai` supplies the schema), inline `CHECK`, named
  constraints.
- `backend/src/main/resources/db/migration/R__recompute_limit_usage.sql` — the FLOW-exclusion
  `COALESCE(override, default) IS DISTINCT FROM 'FLOW'` pattern that must survive the repointing.
- `backend/http/shopping-lists.http` and `backend/http/limits.http` — `@name` captures,
  `@var = {{name.response.body.$.id}}`, and the explanatory `###` comments.

## File inventory

**New module — `backend/src/main/java/xyz/stasiak/recipai/permissions/`**

- **CREATE** `PermissionsFacade.java` — public facade; the only cross-module surface.
- **CREATE** `PermissionService.java` — package-private; granted-permission reads, grant, revoke, cascade.
- **CREATE** `InviteService.java` — package-private; invite create, accept, decline, pending list.
- **CREATE** `InviteController.java` — package-private `/invites` controller; invitee surface only.
- **CREATE** `ResourcePermission.java` — entity over `resource_permission`, owns the role predicates.
- **CREATE** `ResourcePermissionId.java` — `@Embeddable record (email, resourceType, resourceId)`.
- **CREATE** `ResourcePermissionRepository.java` — role lookup, accessible projection, listing, owner email, cascade delete.
- **CREATE** `ResourceInvite.java` — entity over `resource_invite`, UUID primary key.
- **CREATE** `ResourceInviteRepository.java` — by-email, per-resource, existence check, deletes.
- **CREATE** `ResourceRole.java` — public enum `OWNER`, `EDITOR`.
- **CREATE** `PermissionDto.java` — public record `(email, role, pending)`.
- **CREATE** `ShareRequest.java` — public record `(email, role)`, bean-validated.
- **CREATE** `UnshareRequest.java` — public record `(email)`, bean-validated.
- **CREATE** `PendingInviteDto.java` — public record; the invitee's list entry.
- **CREATE** `ResourceAccessDeniedException.java` — public, mapped to 403.
- **CREATE** `InviteNotFoundException.java` — public, mapped to 404.
- **CREATE** `InviteRefusedException.java` — public, mapped to 409, carries `Reason`.
- **CREATE** `PermissionsExceptionHandler.java` — package-private `@RestControllerAdvice` for the three.

**Migrations**

- **CREATE** `backend/src/main/resources/db/migration/V20__resource_permission_and_invite.sql` — both tables, their indexes, and the shopping-list permission copy.
- **MODIFY** `backend/src/main/resources/db/migration/R__recompute_limit_usage.sql` — the `SHOPPING_LIST` slice and the owner joins inside the `SHOPPING_LIST_ITEM` slice read `resource_permission`.

**Shopping lists — modified**

- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListService.java` — asks `PermissionsFacade` for every access decision; `share` creates an invite with the list name as its label.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListRepository.java` — permission join replaced by `findByIdInOrderByCreatedAtAsc`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListController.java` — shared request records; `getSharedUsers` renamed to `getPermissions`, returns `PermissionDto`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListsExceptionHandler.java` — the `ShoppingListAccessDeniedException` branch goes.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/dto/ShoppingListDto.java` — `role` becomes `ResourceRole`.
- **MODIFY** `backend/src/main/java/xyz/stasiak/recipai/config/security/SecurityConfig.java` — `/invites/**` added to the authenticated matcher.

**Shopping lists — deleted**

- **DELETE** `…/shoppinglists/ShoppingListPermission.java` — superseded by `ResourcePermission`.
- **DELETE** `…/shoppinglists/ShoppingListPermissionId.java` — superseded by `ResourcePermissionId`.
- **DELETE** `…/shoppinglists/ShoppingListPermissionRepository.java` — no reader left.
- **DELETE** `…/shoppinglists/UserRole.java` — superseded by `ResourceRole`.
- **DELETE** `…/shoppinglists/dto/SharedUserDto.java` — superseded by `PermissionDto`.
- **DELETE** `…/shoppinglists/dto/ShareShoppingListRequest.java` — superseded by `ShareRequest`.
- **DELETE** `…/shoppinglists/dto/UnshareShoppingListRequest.java` — superseded by `UnshareRequest`.
- **DELETE** `…/shoppinglists/exception/ShoppingListAccessDeniedException.java` — superseded by `ResourceAccessDeniedException`.

**Tests**

- **CREATE** `backend/src/test/java/xyz/stasiak/recipai/permissions/PermissionsModuleArchitectureTest.java` — the two ArchUnit rules retargeted.
- **CREATE** `backend/src/test/java/xyz/stasiak/recipai/permissions/InviteIntegrationTest.java` — the handshake, both refusal rules, cancel, and the delete cascade over HTTP.
- **MODIFY** `backend/src/test/java/xyz/stasiak/recipai/shoppinglists/ShoppingListIntegrationTest.java` — sharing tests assert the handshake; helpers and imports move to the shared types and the `/permissions` path; the recompute tests are left untouched.

**HTTP suite**

- **CREATE** `backend/http/invites.http` — list, accept, decline as `bob`.
- **MODIFY** `backend/http/shopping-lists.http` — `share` carries a role, `/users` becomes `/permissions` and shows `pending`, and the comments say what an invite does.

**Standards**

- **MODIFY** `docs/backend/standards/module-structure.md` — the exception-handler section states the return type: a bare `ProblemDetail`, with `ResponseEntity` reserved for a header or a non-`ProblemDetail` body.

**Docs** (named as deliverables by `task-design.md` > Modified and by `tasks.md` > Cross-task notes; produced by the `docs-updating` step at the end of the task)

- **CREATE** `docs/backend/modules/permissions/module.md`, `api.md`, `db.md`.
- **MODIFY** `docs/INDEX.md` — the `permissions` module entry.
- **MODIFY** `docs/backend/modules/shopping-lists/{module.md,api.md,db.md}` — permissions moved out, share is now an invite, `/users` is renamed to `/permissions` and grows `pending`.
- **MODIFY** `docs/backend/modules/limits/{module.md,db.md}` — the recompute's `SHOPPING_LIST` and `SHOPPING_LIST_ITEM` slices read the new store.

## Step-by-step plan

1. **Schema** — add `V20__resource_permission_and_invite.sql` exactly as `task-design.md` > Schema
   specifies: `resource_permission` (composite PK, `idx_resource_permission_resource`, the partial
   `uq_resource_permission_owner`), `resource_invite` (UUID PK, `uq_resource_invite_target`,
   `idx_resource_invite_email`, `idx_resource_invite_resource`), then the
   `INSERT … SELECT … FROM shopping_list_permission` copy under `'SHOPPING_LIST'`. Bare table names
   — Flyway's `default-schema: recipai` supplies the schema. Leave `shopping_list_permission` in
   place. Before writing it, run the ownership check from **Risks** below against production.
   - Files: `backend/src/main/resources/db/migration/V20__resource_permission_and_invite.sql`
   - Verify: `cd backend && ./mvnw test -Dtest=RecipAiApplicationTests` — Flyway applies V20 against
     a fresh Testcontainers Postgres and the context boots.

2. **Public types and exceptions** — `ResourceRole`, `PermissionDto`, `ShareRequest`
   (`@NotBlank @Email String email`, `@NotNull ResourceRole role`), `UnshareRequest`,
   `PendingInviteDto`, `ResourceAccessDeniedException`, `InviteNotFoundException`,
   `InviteRefusedException` with its public nested `Reason` enum and a `reason()` accessor
   (mirroring `LimitExceededException`'s accessor style). Nothing depends on them yet.
   - Files: the eight new files under `…/permissions/`
   - Verify: `cd backend && ./mvnw -q compile`

3. **Persistence** — `ResourcePermissionId` (`@Embeddable record (String email, String resourceType,
   UUID resourceId) implements Serializable`), `ResourcePermission` (`@EmbeddedId`,
   `@Enumerated(EnumType.STRING) @Column(nullable = false) ResourceRole role`,
   `@Column(nullable = false, updatable = false) Instant createdAt = Instant.now()`,
   `hasOwnerRights()` / `hasEditorRights()`, explicit `equals`/`hashCode`), `ResourceInvite`
   (`@Id @GeneratedValue(strategy = GenerationType.UUID)`, `resourceType`, `resourceId`, `email`,
   `role`, `invitedBy`, `label`, `createdAt`, package-private business constructor), and both
   repositories. Follow the design's query list; two deviations from its sketch, both deliberate:
   - the per-resource listing keeps today's **`ORDER BY p.role DESC`** as an explicit `@Query` —
     `@Enumerated(EnumType.STRING)` sorts on the string, where `EDITOR` precedes `OWNER` (see
     **Risks**);
   - `ResourceInviteRepository` exposes `saveAndFlush` usage in step 5, so no extra method is
     needed there, but it does need `existsByResourceTypeAndResourceIdAndEmail`,
     `findByEmailOrderByCreatedAtDesc`, `findByResourceTypeAndResourceIdOrderByCreatedAtAsc`,
     `deleteByResourceTypeAndResourceIdAndEmail` and a `@Modifying` per-resource delete.
   - Files: `ResourcePermission.java`, `ResourcePermissionId.java`,
     `ResourcePermissionRepository.java`, `ResourceInvite.java`, `ResourceInviteRepository.java`
   - Verify: `cd backend && ./mvnw test -Dtest=RecipAiApplicationTests` — `ddl-auto: validate`
     confirms the entities match V20's columns.

4. **`PermissionService`** — package-private `@Service`, `@RequiredArgsConstructor`, `@Slf4j`,
   `org.springframework.transaction.annotation.Transactional` (the import `limits` uses). Implements
   `roleOf` (with the WARN on a never-seen resource type), `requireEditor` / `requireOwner`
   throwing `ResourceAccessDeniedException`, `accessibleResources` mapping the `List<Object[]>`
   projection into a `Map<UUID, ResourceRole>`, `ownerEmail`, `grantOwner`, `getPermissions`
   (granted first, then the resource's pending invites by age, each as
   `new PermissionDto(email, role, true)`), `revoke` and `resourceDeleted` — both exactly as the
   design's pseudo-code, with the self-unshare guard checked **before** the lookup.
   - Files: `…/permissions/PermissionService.java`
   - Verify: `cd backend && ./mvnw -q compile`

5. **`InviteService`** — package-private `@Service`; `create`, `accept`, `decline`,
   `findPendingFor` per the design's pseudo-code. Two implementation notes the pseudo-code leaves
   open: `create` must use **`saveAndFlush`** inside the `try` so
   `uq_resource_invite_target` surfaces as a catchable `DataIntegrityViolationException` rather
   than at transaction commit (see **Risks**); and `accept` inserts the permission through
   `PermissionService` so the OWNER-uniqueness and logging stay in one place.
   - Files: `…/permissions/InviteService.java`
   - Verify: `cd backend && ./mvnw -q compile`

6. **Facade, controller, exception handler, security** — `PermissionsFacade` (public `@Service`,
   logs then delegates, exactly `LimitsFacade`'s shape) with the ten methods from the design;
   `InviteController` (`@RequestMapping("/invites")`, package-private, three endpoints, email from
   `jwt.getClaimAsString("email")`, accept/decline returning `ResponseEntity.noContent()`);
   `PermissionsExceptionHandler` mapping 403 / 404 / 409 with titles `Resource Access Denied`,
   `Invite Not Found`, `Invite Refused`, the last carrying
   `problemDetail.setProperty("reason", ex.reason())` — each handler method returns a bare
   `ProblemDetail`, never `ResponseEntity<ProblemDetail>`, so the status is stated once; `/invites/**`
   added to `SecurityConfig`'s authenticated matcher.
   - Files: `…/permissions/PermissionsFacade.java`, `…/permissions/InviteController.java`,
     `…/permissions/PermissionsExceptionHandler.java`,
     `backend/src/main/java/xyz/stasiak/recipai/config/security/SecurityConfig.java`
   - Verify: `cd backend && ./mvnw test -Dtest=RecipAiApplicationTests`, then with the app running
     (`./recipai.sh start-backend`) `curl -sS -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer bob" localhost:8080/invites`
     returns `200` (not `403`).

7. **Architecture test** — copy `LimitsModuleArchitectureTest` into
   `…/permissions/PermissionsModuleArchitectureTest.java`, retarget both rules to `..permissions..`,
   and list the public types in `IS_A_SHARED_PUBLIC_TYPE`: `PermissionsFacade`, `ResourceRole`,
   `PermissionDto`, `ShareRequest`, `UnshareRequest`, `PendingInviteDto`,
   `ResourceAccessDeniedException`, `InviteNotFoundException`, `InviteRefusedException`, and
   `Reason` (the nested enum is a class of its own to ArchUnit — see **Risks**).
   - Files: `backend/src/test/java/xyz/stasiak/recipai/permissions/PermissionsModuleArchitectureTest.java`
   - Verify: `cd backend && ./mvnw test -Dtest=PermissionsModuleArchitectureTest`

8. **Migrate `ShoppingListService` and its surface; delete the old types** — one commit, because
   the deletions break compilation until every call site moves.
   - `create`: `permissionsFacade.grantOwner(SHOPPING_LIST_RESOURCE, savedList.getId(), userEmail)`.
   - `requireEditorPermission` becomes a private helper that keeps the **existence check first**
     (`shoppingListRepository.existsById` → `ShoppingListNotFoundException`) and then returns
     `permissionsFacade.requireEditor(...)`. Ordering is load-bearing: the facade cannot tell a
     missing list from a missing permission, and `shouldReturn404WhenShoppingListNotFound` asserts
     404, not 403.
   - `requireOwnerEmail` → `permissionsFacade.ownerEmail(...).orElseThrow(() -> new ShoppingListNotFoundException(listId))`.
   - `findById` passes the role `requireEditor` returned straight into `toDto`.
   - `findAll`: `Map<UUID, ResourceRole> access = permissionsFacade.accessibleResources(...)`;
     return `List.of()` when empty; otherwise `shoppingListRepository.findByIdInOrderByCreatedAtAsc(access.keySet())`.
   - `deleteById`: existence check, `requireOwner`, `permissionsFacade.resourceDeleted(...)`,
     `shoppingListRepository.deleteById(id)`, then the two unchanged `limitsFacade` calls last.
   - `shareShoppingList(ShareRequest, UUID, String)`: load the list (`findById`, 404 if absent),
     `requireEditor`, then `permissionsFacade.invite(SHOPPING_LIST_RESOURCE, id, request.email(),
     request.role(), list.getName(), requesterEmail)`.
   - `unshareShoppingList`: existence check, `requireEditor`, `permissionsFacade.revoke(...)`.
   - `getSharedUsers` → `getPermissions`: existence check, `requireEditor`, then
     `permissionsFacade.getPermissions(...)`.
   - `ShoppingListRepository`: drop the `@Query` join, add `findByIdInOrderByCreatedAtAsc(Collection<UUID> ids)`.
   - `ShoppingListDto.role` → `ResourceRole`; controller signatures take `ShareRequest` /
     `UnshareRequest` and `getPermissions` returns `List<PermissionDto>` on the renamed
     `GET /shopping-lists/{id}/permissions` path (was `/{id}/users`); the handler loses its
     access-denied branch.
   - Delete the eight superseded files.
   - Files: `ShoppingListService.java`, `ShoppingListRepository.java`, `ShoppingListController.java`,
     `ShoppingListsExceptionHandler.java`, `dto/ShoppingListDto.java`, plus the eight deletions
   - Verify: `cd backend && ./mvnw -q compile` succeeds and
     `grep -rn "shopping_list_permission\|ShoppingListPermission" backend/src/main/java` returns nothing.

9. **Repoint the recompute's shopping-list slices** — in `R__recompute_limit_usage.sql`, the
   `SHOPPING_LIST` `INSERT` reads `FROM resource_permission p WHERE p.resource_type = 'SHOPPING_LIST'
   AND p.role = 'OWNER'`, and both owner joins in the `SHOPPING_LIST_ITEM` slice (the `DELETE`'s
   correlated subquery on `p.resource_id::text = u.subject` and the `INSERT`'s
   `JOIN … ON p.resource_id = i.shopping_list_id`) gain the same `resource_type` filter. The
   `RECIPE`, `RECIPES_COLLECTION` and `MEAL_PLAN` slices are untouched. Preserve the FLOW-exclusion
   `COALESCE` blocks verbatim.
   - Files: `backend/src/main/resources/db/migration/R__recompute_limit_usage.sql`
   - Verify: `cd backend && ./mvnw test -Dtest=ShoppingListIntegrationTest` — the existing
     `LimitsEnforced` recompute tests (`shouldRepairDriftToActualOwnedCountViaRecompute`,
     `shouldReproducePerListItemCountsViaRecompute`, `shouldChangeNothingOnSecondRecomputeRun`,
     the two FLOW-sparing tests) pass unchanged.

10. **Rework the shopping-list suite** — update helpers (`shareShoppingList` now sends
    `new ShareRequest(email, ResourceRole.EDITOR)`, `getSharedUsers` → `getPermissions` returning
    `List<PermissionDto>` from the renamed `/shopping-lists/{id}/permissions` path), and rewrite the
    sharing tests to the handshake per the **Test plan** below. Add an `acceptInvite(client, inviteId)` helper on top of `GET /invites` so a test can put
    a second user into the granted state the item and delete tests already assume.
    - Files: `backend/src/test/java/xyz/stasiak/recipai/shoppinglists/ShoppingListIntegrationTest.java`
    - Verify: `cd backend && ./mvnw test -Dtest=ShoppingListIntegrationTest`

11. **`InviteIntegrationTest`** — the new suite, cases per the **Test plan**.
    - Files: `backend/src/test/java/xyz/stasiak/recipai/permissions/InviteIntegrationTest.java`
    - Verify: `cd backend && ./mvnw test -Dtest=InviteIntegrationTest`, then `./mvnw test`

12. **HTTP suite** — `backend/http/invites.http` (list as `bob`, `@name` capture of the first
    invite's id, accept, decline) and the share / unshare / permissions blocks in
    `shopping-lists.http` updated for the renamed path, the role field and the `pending` flag, with
    comments saying that share now creates an invite and that unshare cancels a pending one.
    - Files: `backend/http/invites.http`, `backend/http/shopping-lists.http`
    - Verify: run both files top to bottom against `./recipai.sh start-backend`; every request
      returns its documented status.

13. **Record the exception-handler return-type standard** — `docs/backend/standards/module-structure.md`
    documents the handler-per-module pattern but not what a handler returns, and the codebase is split:
    `ShoppingListsExceptionHandler` returns `ProblemDetail`, `LimitsExceptionHandler` returns
    `ResponseEntity<ProblemDetail>` and states the status twice. Extend the "Exception Handler per
    Feature Module" section with the rule this task follows — return a bare `ProblemDetail` and let
    Spring take the status from it; reach for `ResponseEntity` only when the response needs a header
    (as `limits`' 429 does for `Retry-After`) or a body that is not a `ProblemDetail` (as the
    shopping-list 412 does). Note that the section's own example still shows the older
    `ResponseEntity<ErrorResponse>` shape and should be brought in line. Converting
    `LimitsExceptionHandler`'s `LimitConfigurationMissing` branch is not part of T1.
    - Files: `docs/backend/standards/module-structure.md`
    - Verify: the rule reads unambiguously against all three existing handlers — `permissions`,
      `shoppinglists` and `limits` — without any of them being a documented exception.

14. **Docs** — run the `docs-updating` step for the files listed in the inventory.
    - Files: `docs/backend/modules/permissions/{module.md,api.md,db.md}`, `docs/INDEX.md`,
      `docs/backend/modules/shopping-lists/*`, `docs/backend/modules/limits/*`
    - Verify: `docs/INDEX.md` links resolve; no doc still describes `shopping_list_permission` as
      the shopping-list system of record.

## Test plan

**Unit tests**

_N/A — the project has no service-level unit tests for this kind of logic; `permissions` is
exercised through its HTTP surface and through `ShoppingListIntegrationTest`, per
`docs/backend/standards/integration-tests.md`._

**Integration tests**

`InviteIntegrationTest` (`@SpringBootTest(RANDOM_PORT, properties = "recipai.limits.enabled=false")`
+ `TestcontainersConfiguration` + `TestSecurityConfiguration`; `user1@example.com` shares,
`user2@example.com` is invited):

- `shouldNotGrantAnyAccessWhileInviteIsPending` — after sharing, user2's `GET /shopping-lists`
  omits the list and `GET /shopping-lists/{id}` returns 403.
- `shouldListPendingInviteWithLabelAndSender` — user2's `GET /invites` has one entry whose
  `resourceType` is `SHOPPING_LIST`, `label` is the list's name, `invitedBy` is
  `user1@example.com`, `role` is `EDITOR`, and which carries no resource id.
- `shouldGrantAccessWhenInviteIsAccepted` — after accept, the list appears in user2's
  `/shopping-lists`, `GET /shopping-lists/{id}` returns it with `role = EDITOR`, and user2 can
  create an item in it.
- `shouldRemoveInviteFromBothSidesWhenAccepted` — user2's `/invites` is empty and the list's
  `/permissions` shows user2 as `pending = false`.
- `shouldLeaveResourceInvisibleWhenInviteIsDeclined` — after decline, `/invites` is empty, the list
  is still 403 for user2, and `/permissions` no longer lists user2 at all.
- `shouldCancelPendingInviteWhenSharerUnshares` — `POST /shopping-lists/{id}/unshare` with user2's
  email while the invite is pending removes it from user2's `/invites` and from `/permissions`.
- `shouldRefuseSecondInviteWhenOneIsAlreadyPending` — 409 with `reason = ALREADY_INVITED`.
- `shouldRefuseInviteWhenTargetAlreadyHasAccess` — accept first, then re-share: 409 with
  `reason = ALREADY_HAS_ACCESS`.
- `shouldRefuseInviteToTheResourceOwner` — inviting `user1@example.com` to their own list is
  `ALREADY_HAS_ACCESS`.
- `shouldReturn404WhenAcceptingAnInviteBelongingToSomeoneElse` — the third token
  (`user@example.com`) accepting user2's invite gets 404, and the invite still stands for user2.
- `shouldReturn404WhenAcceptingAnUnknownInviteId` — a random UUID is 404.
- `shouldReturn404WhenDecliningAnAlreadyAnsweredInvite` — accept then decline the same id.
- `shouldRemovePendingInviteWhenResourceIsDeleted` — user1 deletes the list; user2's `/invites` is
  empty.
- `shouldListInvitesOnlyForTheCallingEmail` — two lists invited to two different addresses; each
  caller sees only their own.
- `shouldShowPendingAndGrantedTogetherInPermissions` — user1 invites user2 and accepts nothing,
  then invites `user@example.com` and has it accepted: `/permissions` returns owner first, then the
  granted editor, then the pending entry, with `pending` set correspondingly.

`ShoppingListIntegrationTest` (modified):

- `shouldShareAndUnshareShoppingLists` — rewritten: share creates a pending entry (user2 still 403);
  accept; assert `/permissions` is `[user1 OWNER not-pending, user2 EDITOR not-pending]`; unshare;
  assert 403 again.
- `shouldAllowEditorsToShareAndUnshare` — user2 accepts first, then invites `user@example.com`;
  `/permissions` has three entries, the third `pending = true`; user2 cancels it; two remain.
- `shouldPreventUnsharingOwner` — unchanged in intent, still 403, now from
  `ResourceAccessDeniedException`.
- `shouldAllowEditorToUnshareThemselves` — **inverted** to
  `shouldPreventEditorFromUnsharingThemselves` (403), per the design's deliberate behaviour change.
- `shouldPreventOwnerFromUnsharingThemselves` — unchanged.
- `shouldHandleDuplicateShareAsNoOp` — **replaced** by `shouldRefuseDuplicateShare` (409).
- `shouldHandleUnshareNonExistentAsNoOp` — unchanged (still a no-op, now through `revoke`).
- `shouldAllowSharedUserToViewList`, `shouldPreventSharedUserFromDeletingList`,
  `shouldAllowSharedEditorToCreateItem`, `shouldReturnOwnerConfiguredQuotaWhenReadBySharedEditorNotEditorsOwnOverride`,
  and the `shouldLeaveRecipientBalanceUntouchedOnShareAndUnshare` limits test — each gains an accept
  step after the share, since sharing alone no longer grants access.
- New: `shouldNotCountPendingInviteTowardsRecipientQuota` — inside `LimitsEnforced`, sharing without
  accepting leaves the recipient's `SHOPPING_LIST` balance untouched *and* the owner's unchanged.

No new recompute test is added. The existing `LimitsEnforced` recompute tests —
`shouldRepairDriftToActualOwnedCountViaRecompute`, `shouldClearUsageForSubjectThatOwnsNothing`,
`shouldChangeNothingOnSecondRecomputeRun`, `shouldReproducePerListItemCountsViaRecompute`,
`shouldChangeNothingOnSecondItemRecomputeRun` and the FLOW-sparing pair — already create their lists
through the API, which after step 8 writes
`resource_permission`, so they assert exactly what a new test would: the repointed recompute counts
the new store and reproduces the same numbers. What they cannot cover is whether V20's *copy* of
pre-existing rows was faithful, because an integration test has no pre-migration rows to copy —
that check belongs against a production snapshot, and stays in **Manual verification** below.

`PermissionsModuleArchitectureTest` — no class in `..permissions..` depends on another
`xyz.stasiak.recipai` package; only the ten listed types are public.

**Flutter widget/integration tests**

_N/A — T1 is backend only; the mobile catch-up is T4 and T5._

**Manual verification**

- `tasks.md` > T1 > How to verify, steps 1–8, against `./recipai.sh start-backend` with
  `Bearer alice` / `Bearer bob`, driven from `backend/http/shopping-lists.http` and
  `backend/http/invites.http`.
- Step 8 specifically: snapshot `limit_usage` on a database with pre-existing shopping lists, apply
  V20, run the repeatable recompute, and diff.
- Confirm the WARN from `roleOf` fires exactly once for a deliberately misspelled resource type and
  does not fire during a normal run.

## Verification checklist

- [ ] `cd backend && ./mvnw -q compile` — no new warnings.
- [ ] `cd backend && ./mvnw test` — all new and existing tests pass.
- [ ] `V20__resource_permission_and_invite.sql` applies cleanly to a fresh database (covered by
      `RecipAiApplicationTests`) **and** to a copy of production data with existing
      `shopping_list_permission` rows.
- [ ] `resource_permission` row count equals `shopping_list_permission` row count after V20.
- [ ] `PermissionsModuleArchitectureTest` passes — the boundary and the public-type list hold.
- [ ] `grep -rn "ShoppingListPermission\|shoppinglists.UserRole\|SharedUserDto" backend/src` returns
      nothing under `shoppinglists`.
- [ ] `tasks.md` > T1 > How to verify, steps 1–8, succeed end to end.
- [ ] `backend/http/shopping-lists.http` and `backend/http/invites.http` run top to bottom.
- [ ] `GET /shopping-lists/{id}/users` is gone (404) and `GET /shopping-lists/{id}/permissions`
      serves the list — the rename T5 depends on.
- [ ] `docs/backend/standards/module-structure.md` states the handler return-type rule, and
      `PermissionsExceptionHandler` follows it.
- [ ] `task-design.md` > Assumptions to verify: each is confirmed, or carried into **Risks** below
      with a decision.
- [ ] Logs at `INFO` on the happy path are clean; the `roleOf` unknown-type WARN is silent.
- [ ] `shopping_list_permission` is still present and no longer read or written — the drop is T3's.

## Risks surfaced during planning

- **Risk:** `ORDER BY p.role ASC` does **not** put `OWNER` first. `task-design.md` reasons from the
  enum's ordinal, but `@Enumerated(EnumType.STRING)` stores and sorts the string, and `EDITOR`
  precedes `OWNER` alphabetically — which is why the existing
  `ShoppingListPermissionRepository.findAllByShoppingListId` uses `ORDER BY slp.role DESC`.
  **Why it matters:** the design's own assumption list flags this, and getting it wrong silently
  reverses `/permissions` for every one of the four modules once T2 and T3 land on the same query.
  **Mitigation:** use an explicit `@Query` with `ORDER BY p.role DESC` (not a derived
  `…OrderByRoleAsc` method name) and assert the order in
  `shouldShowPendingAndGrantedTogetherInPermissions`.

- **Risk:** `InviteService.create`'s `catch (DataIntegrityViolationException)` never fires as
  written. The design's pseudo-code calls `inviteRepository.save(...)`, which under JPA defers the
  `uq_resource_invite_target` violation to flush — i.e. to the commit of `create`'s own
  `@Transactional` boundary, outside the `try`. The concurrent-share loser would then get a 500
  instead of the intended 409, and the transaction would already be marked rollback-only.
  **Why it matters:** the constraint is the design's stated backstop for the race the in-code check
  cannot cover; a backstop that produces a 500 is not one.
  **Mitigation:** use `saveAndFlush` inside the `try`. Covered indirectly by
  `shouldRefuseSecondInviteWhenOneIsAlreadyPending`; the true concurrent case is not worth an
  automated test here.

- **Risk:** `InviteRefusedException.Reason` is a public nested enum, and ArchUnit's `classes()`
  treats it as a class in `..permissions..` with `simpleName` `Reason`.
  **Why it matters:** `onlyTheFacadeAndSharedTypesArePublic` fails on a type the design intends to
  be public, and the obvious "fix" — making `Reason` package-private — would break the public
  `reason()` accessor.
  **Mitigation:** add `simpleName("Reason")` to `IS_A_SHARED_PUBLIC_TYPE` and note in the test why.

- **Risk:** `ShoppingListService`'s access checks currently return 404 before 403 by checking
  `shoppingListRepository.existsById` first, but `PermissionsFacade.requireEditor` cannot
  distinguish a missing list from a missing permission and always throws 403.
  **Why it matters:** dropping the existence check turns `shouldReturn404WhenShoppingListNotFound`,
  `shouldReturn404WhenUpdatingNonExistentShoppingList`, `shouldReturn404WhenDeletingNonExistentShoppingList`
  and `shouldReturn404ForUnknownListId` into 403s — a silent API contract change the mobile client
  keys off.
  **Mitigation:** keep the existence check in every `ShoppingListService` path, before the facade
  call; step 8 spells this out. `shareShoppingList` gets it for free by loading the list for its
  label.

- **Risk:** the recompute's `SHOPPING_LIST_ITEM` slice joins the permission table twice — once in
  the `DELETE`'s correlated subquery and once in the `INSERT`'s `JOIN` — and the design's
  description mentions only "the owner join".
  **Why it matters:** repointing one and not the other leaves the `DELETE` resolving the owner's
  FLOW configuration from a table that stops being written, so item usage rows silently stop being
  refreshed for lists shared after T1.
  **Mitigation:** step 9 names both sites; `shouldReproducePerListItemCountsViaRecompute` and
  `shouldSpareListWhoseOwnerIsFlowConfiguredFromItemRecompute` cover them.

- **Risk:** three of the design's Assumptions to verify remain open at planning time — the
  one-OWNER-per-list check against production data (which gates `uq_resource_permission_owner`), the
  claim that nothing outside `ShoppingListService` reads `shopping_list_permission`, and the
  `limit_usage` byte-identity check.
  **Why it matters:** the first aborts the migration at deploy time rather than in CI; the second is
  a silent stale read.
  **Mitigation:** run
  `SELECT shopping_list_id FROM recipai.shopping_list_permission GROUP BY shopping_list_id HAVING count(*) FILTER (WHERE role = 'OWNER') <> 1;`
  against production **before** writing V20, and drop the partial index into a follow-up migration if
  it returns rows. The second assumption is discharged by the `grep` in the checklist — planning
  confirmed only `ShoppingListRepository.findAllByUserEmail` and the recompute read the table, and
  no Java outside `xyz.stasiak.recipai.shoppinglists` references any shopping-list permission type.
  The third is discharged by the existing `LimitsEnforced` recompute tests for the repointing, and
  by the production-snapshot diff in **Manual verification** for the copy — the two halves the design
  folded into one assumption, which no single integration test can cover.

- **Risk:** from T1 until T5, sharing a shopping list from the mobile app appears to do nothing —
  the invitee never shows up in "Shared with".
  **Why it matters:** it is the expected intermediate state (`tasks.md` > Cross-task notes), but on
  a branch that anyone might deploy it looks like a regression.
  **Mitigation:** none needed in code; keep the branch unreleased, and say so in the PR description.
