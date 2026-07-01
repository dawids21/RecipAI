# T1 — Backend Version-Gated Item Endpoints — Task Design

**Date:** 2026-06-28
**Status:** draft
**Task:** T1 in `tasks.md`
**Builds on:** `../hld.md` (HLD §1), `../requirements.md` (§2), `docs/ADRs/0003-shopping-list-full-refresh-over-delta.md`

## Summary

T1 adds three **detail-level item endpoints** to the existing
`shopping-lists` module — create, update, delete — each routed through a
per-item **version gate** implementing **first-action-wins** (req §2). A
mutating call carries the **base version** it acted on; the server accepts only
if the stored item is still at that version (then bumps it), otherwise rejects
with **412 + the current winning item** so the client can roll back. Create
never conflicts. Delete is hard and version-gated. The existing
`GET /shopping-lists/{id}` is the full-list read and is left unchanged. The
schema groundwork (fractional `position`, `@Version`) already exists from
migration `V5`; T1 adds the endpoints and **drops the position uniqueness
constraint** that would otherwise contradict req §2.4 / §2.7.

## Components and responsibilities

All in module `xyz.stasiak.recipai.shoppinglists`
(`backend/src/main/java/.../shoppinglists/`). Item operations live **on the
existing `ShoppingListService`**, which already owns
`ShoppingListItemRepository` and the `toItemDto` mapping — no new service class.

- **`ShoppingListController`** (MODIFY, `ShoppingListController.java`) — add three
  package-private handlers under the nested `…/items` path. Each extracts the
  user email from the JWT, `log.debug`s, and delegates to the service. Mirrors
  the existing handler style.
- **`ShoppingListService`** (MODIFY, `ShoppingListService.java`) — add
  `createItem` / `updateItem` / `deleteItem` (all `@Transactional`) plus a
  private `requireEditorPermission(listId, userEmail)` helper extracted from the
  duplicated access check already inlined in `findById` / `updateById`. Owns the
  version-gate logic.
- **`ShoppingListItemRepository`** (MODIFY, `ShoppingListItemRepository.java`) —
  add `Optional<ShoppingListItem> findByIdAndShoppingListId(UUID id, UUID
  shoppingListId)` (item scoped to its list → clean 404, no cross-list writes),
  and change the read-path finder to tie-break on id now that positions are
  non-unique: `findByShoppingListIdOrderByPositionAscIdAsc`.
- **`ShoppingListItem`** (MODIFY, `ShoppingListItem.java`) — remove the
  `@UniqueConstraint(... "position")` from the `@Table` annotation. Required:
  `ddl-auto: validate` will fail startup if the entity declares a constraint the
  (migrated-away) schema no longer has.
- **`CreateShoppingListItemRequest`** (CREATE, `dto/`) — request record:
  `name`, `quantity`, `unit`, `position`. **No base version** (creates never
  conflict). Bean-validation on components.
- **`UpdateShoppingListItemRequest`** (CREATE, `dto/`) — request record:
  `baseVersion` + the full mutable item state (`name`, `quantity`, `unit`,
  `checked`, `position`). A full-field PUT applied uniformly through the gate.
- **`ShoppingListItemDto`** (REUSE, `dto/ShoppingListItemDto.java`) — already
  holds `id, name, quantity, unit, checked, position, version`. Serves the 201
  create body, the 200 update body, **and the 412 conflict body** (raw winning
  item).
- **`ItemNotFoundException`** (CREATE, `exception/`) — `public`, extends
  `RuntimeException`. Item absent from the list → 404.
- **`ItemVersionConflictException`** (CREATE, `exception/`) — `public`, extends
  `RuntimeException`, **carries the winning `ShoppingListItemDto`**. Mapped to
  412 with that item as the body.
- **`ShoppingListsExceptionHandler`** (MODIFY, `ShoppingListsExceptionHandler.java`)
  — add a 404 `ProblemDetail` handler for `ItemNotFoundException` (consistent
  with the module's existing handlers) and a **412 handler returning the raw
  winning item DTO** for `ItemVersionConflictException`.
- **`V14__drop_item_position_unique.sql`** (CREATE,
  `src/main/resources/db/migration/`) — drops
  `uk_shopping_list_items_list_position`. The "migration that rides in this task"
  (`tasks.md` Scope). Next free version is V14 (highest existing is V13).

## Interfaces and method signatures

### Endpoints (nested under the list)

```
POST   /shopping-lists/{listId}/items                  body CreateShoppingListItemRequest
       -> 201 ShoppingListItemDto (version 0)

PUT    /shopping-lists/{listId}/items/{itemId}         body UpdateShoppingListItemRequest
       -> 200 ShoppingListItemDto (bumped version)
       -> 412 ShoppingListItemDto (winning item)       on stale baseVersion

DELETE /shopping-lists/{listId}/items/{itemId}?baseVersion={n}
       -> 204 No Content
       -> 412 ShoppingListItemDto (winning item)       on concurrent edit
       -> 404                                           item absent

(all) -> 403 if caller lacks editor rights; 404 if the list does not exist
```

`baseVersion` is a request-body field on PUT and a **query param** on DELETE
(DELETE has no body). Carrying it in the payload — rather than an `If-Match`
header — matches the HLD's "each mutating call carries the base version" framing
and the codebase's plain-JSON style; `If-Match`/ETag was considered and rejected
as machinery the project doesn't otherwise use.

### DTOs

```java
public record CreateShoppingListItemRequest(
    @NotBlank @Size(max = 255) String name,
    @PositiveOrZero BigDecimal quantity,   // nullable
    @Size(max = 64) String unit,           // nullable
    @NotNull BigDecimal position
) {}

public record UpdateShoppingListItemRequest(
    @NotNull Long baseVersion,
    @NotBlank @Size(max = 255) String name,
    @PositiveOrZero BigDecimal quantity,   // nullable
    @Size(max = 64) String unit,           // nullable
    @NotNull Boolean checked,
    @NotNull BigDecimal position
) {}
```

### Service

```java
@Transactional ShoppingListItemDto createItem(UUID listId, CreateShoppingListItemRequest req, String userEmail)
@Transactional ShoppingListItemDto updateItem(UUID listId, UUID itemId, UpdateShoppingListItemRequest req, String userEmail)
@Transactional void               deleteItem(UUID listId, UUID itemId, long baseVersion, String userEmail)

private ShoppingListPermission requireEditorPermission(UUID listId, String userEmail)  // throws ShoppingListNotFound / ShoppingListAccessDenied
```

### Exception → response mapping (handler)

```java
@ExceptionHandler(ItemNotFoundException.class)        // -> 404 ProblemDetail (module convention)
@ExceptionHandler(ItemVersionConflictException.class) // -> ResponseEntity.status(PRECONDITION_FAILED).body(ex.winningItem())
```

## Data flow

**Update (the representative gated path):**

1. Controller extracts `userEmail`, delegates to `updateItem`.
2. `requireEditorPermission(listId, userEmail)` — verifies the list exists and
   the caller has editor rights (reuses `ShoppingListNotFoundException` /
   `ShoppingListAccessDeniedException`).
3. `findByIdAndShoppingListId(itemId, listId)` → `ItemNotFoundException` (404) if
   absent.
4. **Gate:** if `item.version != req.baseVersion` → throw
   `ItemVersionConflictException(toItemDto(item))` → 412 + winning item.
5. Apply the new field values; `saveAndFlush` bumps `@Version`. A concurrent
   commit landing between steps 3–5 surfaces here as
   `ObjectOptimisticLockingFailureException` → re-read and convert to the same
   412 (see pseudo-code).
6. Return the saved item as `ShoppingListItemDto` (200).

**Create:** steps 1–2, then construct `new ShoppingListItem(listId, name,
quantity, unit, position)` (constructor normalises position scale, sets
`checked=false`), `save` (Hibernate inits `@Version` to 0), return 201. No gate.

**Delete:** steps 1–4 (gate), then hard `delete(item)` (Hibernate's versioned
`DELETE … WHERE id=? AND version=?`), 204. Race handling per pseudo-code.

The **client supplies `position`** on create (decision below); the server stores
it verbatim. Reorder is an ordinary `updateItem` changing only `position`. The
read path (`findById` → GET) now orders by `(position ASC, id ASC)` for a stable
order under non-unique positions.

## Pseudo-code

Version gate with the JPA optimistic-lock race net (update and delete share the
shape):

```
updateItem(listId, itemId, req, userEmail):
    requireEditorPermission(listId, userEmail)
    item = repo.findByIdAndShoppingListId(itemId, listId)
               .orElseThrow(ItemNotFoundException)

    if item.version != req.baseVersion:
        throw ItemVersionConflictException(toItemDto(item))   # stale base -> 412 + winner

    item.setName(req.name);         item.setQuantity(req.quantity)
    item.setUnit(req.unit);         item.setChecked(req.checked)
    item.setPosition(req.position)                            # setter normalises scale
    try:
        saved = repo.saveAndFlush(item)                       # @Version bumps; flush surfaces races
    except ObjectOptimisticLockingFailureException:
        winner = repo.findByIdAndShoppingListId(itemId, listId)
        if winner.isEmpty(): throw ItemNotFoundException      # deleted out from under us
        throw ItemVersionConflictException(toItemDto(winner)) # edited concurrently -> 412 + winner
    return toItemDto(saved)

deleteItem(listId, itemId, baseVersion, userEmail):
    requireEditorPermission(listId, userEmail)
    item = repo.findByIdAndShoppingListId(itemId, listId).orElseThrow(ItemNotFoundException)
    if item.version != baseVersion:
        throw ItemVersionConflictException(toItemDto(item))   # edit-wins-over-delete (req §2.6)
    try:
        repo.delete(item); repo.flush()                       # versioned DELETE … WHERE version=?
    except ObjectOptimisticLockingFailureException:
        winner = repo.findByIdAndShoppingListId(itemId, listId)
        if winner.isEmpty(): throw ItemNotFoundException       # already gone -> 404 (goal met anyway)
        throw ItemVersionConflictException(toItemDto(winner))  # edited first -> 412
```

`createItem` needs no pseudo-code — construct, `save`, map.

## Decisions made

- **Drop the position uniqueness constraint** (migration V14, and remove the
  `@UniqueConstraint` from the entity). Concurrent appends/reorders can
  legitimately collide on a position; a DB constraint would reject one, breaking
  req §2.7 (creates never conflict) and §2.4 (different-item moves both succeed).
  Position becomes a non-unique sort key, ties broken by id. *(Settled with user.)*
- **Client supplies `position` on create.** The offline store (T2) already
  computes a local fractional position to render order before any server contact;
  sending it keeps position client-owned and uniform with reorder, and preserves
  an offline reorder made before the first sync. Deviates from HLD §1.2's "server
  assigns a position" wording. *(Settled with user.)*
- **412 carries the raw winning `ShoppingListItemDto`** (not a `ProblemDetail`
  envelope). The rollback value is first-class and parsed by the client exactly
  like any item; the conflict exception is handled separately from the module's
  `ProblemDetail`-mapped errors. *(Settled with user.)*
- **Version gate = explicit precheck + `@Version` safety net.** The precheck
  yields the winning item for the 412 body on the common stale-base path; the
  JPA optimistic-lock failure at `saveAndFlush`/`flush` covers the
  read→write race, re-reading to produce the same 412. Both paths converge.
- **Item operations on the existing `ShoppingListService`**, not a new service —
  it already owns `ShoppingListItemRepository` and `toItemDto`, and the access
  check is shared. The `requireEditorPermission` helper de-duplicates the check
  currently inlined in `findById`/`updateById`.
- **`baseVersion` in the PUT body, as a DELETE query param.** Plain-JSON,
  payload-carried precondition; no `If-Match`/ETag machinery introduced.
- **Update is a full-mutable-field PUT.** One endpoint replaces
  name/quantity/unit/checked/position together and gates them as a unit (req
  §3.6 "rejected together"), rather than per-field PATCH or per-action
  `/check` `/move` sub-paths.
- **Missing item → 404 uniformly** (whether absent up front or discovered via the
  race re-read), including delete of an already-gone item — the client drops it on
  the next pull regardless.

## Assumptions to verify

- **Assumption:** Hibernate initialises the `@Version Long` to `0` on first
  persist, so a create returns `version: 0`.
  **If wrong:** the create response/version contract that T2/T3 build on shifts;
  confirm the initial value and document it.
- **Assumption:** Hibernate issues a versioned `DELETE … WHERE id=? AND version=?`
  for a `@Version` entity and raises `ObjectOptimisticLockingFailureException`
  when 0 rows match.
  **If wrong:** the delete race net (concurrent edit during delete) wouldn't
  trigger; the explicit precheck still covers the common case, but the narrow
  read→delete race would let a stale delete through.
- **Assumption:** a `@ControllerAdvice` handler can return a **raw DTO body**
  (`application/json`) at 412 while sibling handlers return `ProblemDetail`
  (`application/problem+json`) without content-negotiation conflicts.
  **If wrong:** fall back to a thin JSON wrapper or revisit the 412 shape
  decision.
- **Assumption:** nothing else depends on the position unique constraint — only
  the entity `@Table` declares it and only `findById`'s ordering reads position.
  **If wrong:** dropping it could change an ordering assumption elsewhere; the
  `(position, id)` tie-break is the mitigation for the read path.
- **Assumption:** `@PositiveOrZero` on a nullable `BigDecimal quantity` is the
  intended validation (null allowed, negatives rejected); the existing
  `ShoppingListItemDto` carries stricter response annotations that are
  inconsequential on the response path.
  **If wrong:** adjust the request validation to match product rules for
  quantity.

## Required reading for implementation planning

- `backend/.../shoppinglists/ShoppingListService.java` — the `findById` access
  check to extract into `requireEditorPermission`, and `toItemDto` to reuse.
- `backend/.../shoppinglists/ShoppingListController.java` — handler style
  (JWT email, `@Slf4j`, `ResponseEntity` status codes) to mirror.
- `backend/.../shoppinglists/ShoppingListItem.java` — the entity (constructor,
  `setPosition` scale normalisation, `@Version`, the `@UniqueConstraint` to
  remove).
- `backend/.../shoppinglists/ShoppingListsExceptionHandler.java` — existing
  `ProblemDetail` mapping pattern to extend for `ItemNotFoundException`.
- `backend/src/main/resources/db/migration/V5__rename_list_id_and_update_position.sql`
  — declares the constraint V14 drops; confirm the exact constraint name.
- `backend/src/test/.../ShoppingListIntegrationTest.java` and
  `docs/backend/standards/integration-tests.md` — Testcontainers + `RestClient`
  + `TestSecurityConfiguration` pattern for the verification tests in `tasks.md`
  (412-on-stale, different-item reorders both 200, delete-vs-edit).
- `docs/backend/standards/{java-patterns,module-structure}.md` — record DTOs,
  package-private visibility, RESTful naming.
- `docs/ADRs/0003-shopping-list-full-refresh-over-delta.md` — why there is no
  delta endpoint / list counter and why per-item `version` is the only
  coordination state.
- `../hld.md` §1 — the endpoint responsibilities and the conflict-gate framing
  (note the §1.2 "server assigns position" wording this design deviates from).
