# T1: Backend version-gated item endpoints — Implementation Plan

**Date:** 2026-07-01
**Status:** draft

## Required reading

**Docs & standards** (from `docs/INDEX.md`)

- `docs/backend/standards/java-patterns.md` — record DTOs with bean validation, package-private visibility, entity conventions.
- `docs/backend/standards/module-structure.md` — RESTful naming, per-module `@ControllerAdvice`, SLF4J `log.debug` pattern.
- `docs/backend/standards/integration-tests.md` — Testcontainers + `RestClient` + `TestSecurityConfiguration` pattern, `shouldXxxWhenYyy` naming.
- `docs/backend/modules/shopping-lists/api.md` and `db.md` — the API/schema being extended; both must be updated by this task.

**Design & ADRs**

- `plans/T1-task-design.md` — the design this plan implements; all decisions (412 body shape, `baseVersion` placement, gate mechanics) are settled there.
- `docs/ADRs/0003-shopping-list-full-refresh-over-delta.md` — why per-item `version` is the only coordination state and `GET /shopping-lists/{id}` stays unchanged.
- `../hld.md` §1 — endpoint responsibilities; note the §1.2 "server assigns position" wording the design deviates from (client supplies position).

**Code to mirror**

- `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListController.java` — handler style: `jwt.getClaimAsString("email")`, `log.debug`, `ResponseEntity` status codes, `{id}` path-variable naming (use `{id}/items/{itemId}`, not the design's `{listId}`).
- `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListService.java` — `jakarta.transaction.Transactional` (not Spring's), `log.debug` first line, `toItemDto` to reuse, the duplicated permission check in `findById`/`updateById` to extract.
- `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListItem.java` — package-private constructor, `setPosition` scale normalisation (`HALF_UP`; scale widens to 12 in this task), the `@UniqueConstraint` to remove.
- `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListsExceptionHandler.java` — `ProblemDetail` mapping to extend (note: the module uses `ProblemDetail`, not the `ErrorResponse` shown in `module-structure.md`; follow the module).
- `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/exception/ShoppingListNotFoundException.java` — public exception shape with message-building constructor.
- `backend/src/test/java/xyz/stasiak/recipai/shoppinglists/ShoppingListIntegrationTest.java` — private HTTP-helper style, `restClient(token)` per-user clients, try/`fail`/catch assertion pattern for error statuses.
- `backend/src/main/resources/db/migration/V5__rename_list_id_and_update_position.sql` — declares `uk_shopping_list_items_list_position`, the exact constraint V14 drops.

## File inventory

All Java paths relative to `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/` unless noted.

- **CREATE** `backend/src/main/resources/db/migration/V14__drop_item_position_unique.sql` — drops `uk_shopping_list_items_list_position`; widens `position` to `NUMERIC(21, 12)`.
- **MODIFY** `ShoppingListItem.java` — remove the `@UniqueConstraint` (keep the plain `@Table(name = …)`); `position` column and `normalizePositionScale` move to precision 21 / scale 12.
- **MODIFY** `ShoppingListItemRepository.java` — rename finder to `findByShoppingListIdOrderByPositionAscIdAsc`; add `findByIdAndShoppingListId`.
- **MODIFY** `ShoppingListService.java` — extract `requireEditorPermission`; add `createItem` / `updateItem` / `deleteItem`; update finder call.
- **MODIFY** `ShoppingListController.java` — add POST/PUT/DELETE handlers under `/{id}/items`.
- **CREATE** `dto/CreateShoppingListItemRequest.java` — request record: name, quantity, unit, position; no baseVersion.
- **CREATE** `dto/UpdateShoppingListItemRequest.java` — request record: baseVersion + full mutable item state.
- **CREATE** `exception/ItemNotFoundException.java` — public RuntimeException → 404.
- **CREATE** `exception/ItemVersionConflictException.java` — public RuntimeException carrying the winning `ShoppingListItemDto` → 412.
- **MODIFY** `ShoppingListsExceptionHandler.java` — 404 `ProblemDetail` handler; 412 handler returning the raw winning DTO.
- **CREATE** `backend/src/test/java/xyz/stasiak/recipai/shoppinglists/ShoppingListItemIntegrationTest.java` — integration tests for the three endpoints.
- **MODIFY** `docs/backend/modules/shopping-lists/api.md` — document the three item endpoints and their 412 contract.
- **MODIFY** `docs/backend/modules/shopping-lists/db.md` — remove the `UNIQUE(shopping_list_id, position)` line; position becomes `NUMERIC(21,12)`, a non-unique sort key with ties broken by id.
- **MODIFY** `docs/backend/modules/shopping-lists/codebase_structure.md` — add the four new source files.

No dependency changes — everything needed (Spring Web, Data JPA, Validation, Flyway, Testcontainers) is already in `backend/pom.xml`.

## Step-by-step plan

All Maven commands run from `backend/`.

1. **Drop the position uniqueness constraint and widen its scale (migration rides first)** — create V14 dropping `uk_shopping_list_items_list_position` and altering `position` to `NUMERIC(21, 12)` (same 9 integer digits, doubled fractional scale for midpoint headroom — settled with user); update the entity to match (`@Column(precision = 21, scale = 12)`, no `@UniqueConstraint` — `ddl-auto: validate` would otherwise fail startup, and `normalizePositionScale` to scale 12); rename the read-path finder to `findByShoppingListIdOrderByPositionAscIdAsc` and update its single caller in `ShoppingListService.findById` so ordering stays stable under ties.
   - Files: `V14__drop_item_position_unique.sql`, `ShoppingListItem.java`, `ShoppingListItemRepository.java`, `ShoppingListService.java`
   - Verify: `./mvnw test` — context boots against the migrated Testcontainers schema and all existing tests pass.

2. **Create-item endpoint end-to-end** — add `CreateShoppingListItemRequest`; extract `private ShoppingListPermission requireEditorPermission(UUID listId, String userEmail)` from the duplicated check in `findById`/`updateById` and re-use it in both; add `@Transactional createItem` (construct via the existing entity constructor, `save`, map with `toItemDto`, return version 0); add the `POST /{id}/items` handler returning 201.
   - Files: `dto/CreateShoppingListItemRequest.java`, `ShoppingListService.java`, `ShoppingListController.java`, `ShoppingListItemIntegrationTest.java`
   - Verify: `./mvnw test -Dtest=ShoppingListItemIntegrationTest` — create cases pass (201 with `version: 0`, item appears in `GET /shopping-lists/{id}`, tie-break ordering case); `./mvnw test` — existing tests still green after the permission-helper refactor.

3. **Update-item endpoint with the version gate** — add `UpdateShoppingListItemRequest`, both exceptions, and the two handler mappings; add `@Transactional updateItem` implementing the design's pseudo-code: permission check → `findByIdAndShoppingListId` (add to repository) → explicit version precheck → apply fields → `saveAndFlush` inside try/catch on `ObjectOptimisticLockingFailureException` re-reading into the same 412. Compare versions with `equals`, not `!=` — two boxed `Long`s outside the integer cache compare unequal by reference. Add the `PUT /{id}/items/{itemId}` handler.
   - Files: `dto/UpdateShoppingListItemRequest.java`, `exception/ItemNotFoundException.java`, `exception/ItemVersionConflictException.java`, `ShoppingListsExceptionHandler.java`, `ShoppingListItemRepository.java`, `ShoppingListService.java`, `ShoppingListController.java`, `ShoppingListItemIntegrationTest.java`
   - Verify: `./mvnw test -Dtest=ShoppingListItemIntegrationTest` — update cases pass, including 412 whose body parses as `ShoppingListItemDto` (winning item).

4. **Delete-item endpoint** — add `@Transactional deleteItem(listId, itemId, baseVersion, userEmail)` with the same precheck + versioned `delete`/`flush` race net; add the `DELETE /{id}/items/{itemId}?baseVersion={n}` handler (`@RequestParam long baseVersion`) returning 204.
   - Files: `ShoppingListService.java`, `ShoppingListController.java`, `ShoppingListItemIntegrationTest.java`
   - Verify: `./mvnw test -Dtest=ShoppingListItemIntegrationTest` — delete cases pass (204 then absent from GET; 412 after concurrent edit; 404 when already gone).

5. **Module docs** — document the three endpoints in `api.md` (statuses, `baseVersion` placement, raw-item 412 body), fix `db.md` (constraint gone, non-unique position + id tie-break), add the new files to `codebase_structure.md`.
   - Files: `docs/backend/modules/shopping-lists/api.md`, `db.md`, `codebase_structure.md`
   - Verify: docs match the implemented behaviour; `./mvnw test` for a final full run.

## Test plan

**Unit tests**

_N/A — the module has no unit-test layer; per `integration-tests.md` all backend behaviour is exercised through `@SpringBootTest` integration tests._

**Integration tests**

`ShoppingListItemIntegrationTest` (`@SpringBootTest` + Testcontainers + `TestSecurityConfiguration`, mirroring `ShoppingListIntegrationTest` helpers):

Create:
- creates item with 201 and `version: 0`, echoing name/quantity/unit/position (position normalised to scale 12)
- created item appears in the next `GET /shopping-lists/{id}`
- two items created at the **same position** both succeed (constraint gone) and GET returns them ordered by `(position, id)`
- returns 400 when name is blank / when quantity is negative / when position is missing; null quantity and unit are accepted
- returns 404 when the list does not exist; 403 when the caller has no permission on the list
- a shared EDITOR can create items

Update:
- updates all fields with 200 and bumped version when baseVersion matches
- second update using the returned version succeeds (sequential edits chain)
- returns 412 with the **winning item as the body** (parsed as `ShoppingListItemDto`) when baseVersion is stale; stored item is unchanged
- position-only updates on two **different** items both return 200
- two moves of the **same** item — the stale one returns 412
- returns 404 when the item id belongs to a different list; 404 when the item does not exist; 403 without permission; 400 on validation failure
- direct repository check of the race net: load the entity, bump it via HTTP, then `saveAndFlush` the stale instance and assert `ObjectOptimisticLockingFailureException` (test class shares the package, so the package-private repository is injectable) — verifies the design's Hibernate assumption

Delete:
- deletes with 204 at the current version; item absent from the next GET
- returns 412 with the winning item when the item was edited after the read (edit-wins-over-delete)
- returns 404 when deleting an already-deleted item; 400 when `baseVersion` query param is missing; 403 without permission

**Flutter widget/integration tests**

_N/A — backend-only task; the mobile side is T2–T4._

**Manual verification**

- The `tasks.md` curl scenarios are all encoded as integration tests above; a manual curl pass against a locally running app (compose + real JWT) is optional confirmation only.

## Verification checklist

- [ ] `./mvnw test` — all new and existing backend tests pass (no formatter/lint plugin is configured for the backend; no new compiler warnings instead)
- [ ] V14 applies cleanly to a fresh database (implicitly proven by the Testcontainers boot; Flyway here is forward-only, no down migration)
- [ ] `tasks.md` > T1 "How to verify" scenarios each map to a passing integration test (stale update → 412 + winner; different-item moves both 200; same-item move 412; delete 204/absent; delete-after-edit 412)
- [ ] Task-design assumptions resolved: create returns `version: 0` (asserted); versioned delete/OOLFE race net (asserted via the direct-repository case); raw DTO body at 412 alongside `ProblemDetail` siblings (asserted by parsing the 412 body); position-constraint dependents (resolved during planning — only the entity and V5 reference it); negative-quantity 400 / null-quantity accepted (asserted)
- [ ] `docs/backend/modules/shopping-lists/{api,db,codebase_structure}.md` updated

## Risks surfaced during planning

- **Risk:** the fractional-position rebalancing question from `tasks.md` — repeated midpoint insertion in one gap eventually exhausts fractional precision and two positions round to the same value.
  **Why it matters:** it was explicitly deferred to implementation planning.
  **Mitigation:** settled with user — widen `position` to scale 12 in V14 (~40 halvings of headroom per gap), and T1 does no server-side rebalancing: positions are client-owned, non-unique, tie-broken by id, so crowding degrades only to id-order ties, never to errors. Re-spreading positions (ordinary gated updates) remains a client-side concern for T2+.
- **Risk:** the version gate compares two boxed `Long`s; `!=` silently compares references for values outside the ±127 cache.
  **Why it matters:** the gate would start falsely rejecting once a list accumulates 128+ versions — a subtle, late-appearing bug.
  **Mitigation:** use `equals` in the precheck (called out in step 3) and keep the sequential-updates integration test, which chains real bumped versions.
- **Risk:** `ShoppingListIntegrationTest` has no `@AfterEach` cleanup even though `integration-tests.md` requires one; each test isolates by asserting only on its own ids.
  **Why it matters:** the new test file must pick a side.
  **Mitigation:** settled with user — no `@AfterEach`; mirror the existing file's per-test-data isolation style.
