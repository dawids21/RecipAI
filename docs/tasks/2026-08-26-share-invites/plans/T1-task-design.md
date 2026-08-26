# T1: `permissions` module, shopping lists migrated, and the invite handshake — Task Design

**Date:** 2026-08-26

## Summary

A new `permissions` package becomes the system of record for who may do what with a shareable
resource, storing granted permissions and pending invites in **two separate tables** so a pending
invite is structurally incapable of answering an access query. Shopping lists are the first module
migrated: `shopping_list_permission` is copied into the new store by a one-off `V20__` migration,
`ShoppingListService` stops owning permission logic and asks `PermissionsFacade` instead, and
`POST /shopping-lists/{id}/share` creates an invite carrying a role and a label rather than granting
access. The invite lifecycle — create, accept, decline, cancel — plus the invitee's cross-resource
list are written once here and not re-implemented in T2 or T3.

## Components and responsibilities

### New — `backend/src/main/java/xyz/stasiak/recipai/permissions/`

- **`PermissionsFacade`** (CREATE, public) — the only cross-module surface. Refuses directly
  (`requireEditor` / `requireOwner` throw), answers role questions, grants ownership at resource
  creation, creates and revokes invites, lists a resource's permissions, and accepts a deletion
  report. Delegates to the two package-private services and logs, mirroring `LimitsFacade`.
- **`PermissionService`** (CREATE, package-private) — granted permissions: the role lookups, the
  owner resolution, `grantOwner`, `revoke` (with the never-unshare-an-OWNER and never-unshare-
  yourself guards), the permission listing, and the cascade on resource deletion.
- **`InviteService`** (CREATE, package-private) — the invite lifecycle: creation with its two refusal
  rules, accept (destroy invite, grant permission), decline, and the cross-resource "what is waiting
  for this email" query. Calls `PermissionService` on accept.
- **`InviteController`** (CREATE, package-private, `/invites`) — the invitee's surface only:
  `GET /invites`, `POST /invites/{id}/accept`, `POST /invites/{id}/decline`. Cancel is not here; it
  rides each resource's existing `/unshare`.
- **`ResourcePermission`** / **`ResourcePermissionId`** (CREATE, package-private) — entity over
  `resource_permission`, composite key `(email, resourceType, resourceId)`. Owns the role
  predicates `hasOwnerRights()` / `hasEditorRights()` that the four modules reimplement today.
- **`ResourcePermissionRepository`** (CREATE, package-private) — role lookup, accessible-resources
  projection, per-resource listing, owner-email lookup, delete-all-for-resource.
- **`ResourceInvite`** (CREATE, package-private) — entity over `resource_invite`, UUID primary key.
- **`ResourceInviteRepository`** (CREATE, package-private) — by-id, by-email (cross-resource),
  per-resource listing, existence checks behind the refusal rules, delete-all-for-resource.
- **`ResourceRole`** (CREATE, public enum) — `OWNER`, `EDITOR`. Replaces the four per-module
  `UserRole` enums; wire values unchanged.
- **`PermissionDto`** (CREATE, public record) — `(email, role, pending)`. Replaces the four copies.
- **`ShareRequest`** / **`UnshareRequest`** (CREATE, public records) — replace the four
  `Share*Request` / `Unshare*Request` pairs. `ShareRequest` gains the `role` field the requirements
  ask for.
- **`PendingInviteDto`** (CREATE, public record) — the invitee's list entry.
- **`ResourceAccessDeniedException`** (CREATE, public) — the shared 403, replacing the four
  `*AccessDeniedException` classes as each module migrates.
- **`InviteNotFoundException`** (CREATE, public) — 404.
- **`InviteRefusedException`** (CREATE, public) — 409, carrying a machine-readable reason.
- **`PermissionsExceptionHandler`** (CREATE, package-private `@RestControllerAdvice`) — maps the
  three exceptions, in the shape of `LimitsExceptionHandler`.

### New — migrations and tests

- **`V20__resource_permission_and_invite.sql`** (CREATE) — creates both tables and their indexes,
  then copies `shopping_list_permission` into `resource_permission` under the `SHOPPING_LIST` type
  key. The old table is left in place, unread and unwritten, until T3 drops it.
- **`PermissionsModuleArchitectureTest`** (CREATE, `src/test/java/.../permissions/`) — the two
  ArchUnit rules `LimitsModuleArchitectureTest` already runs, retargeted: no class in
  `..permissions..` may depend on any other `xyz.stasiak.recipai` package, and only the public types
  listed above may be public.
- **`InviteIntegrationTest`** (CREATE) — the handshake end to end over HTTP against a shopping list,
  plus both refusal rules and the delete cascade.

### Modified

- **`ShoppingListService`** (MODIFY, `shoppinglists/ShoppingListService.java`) — drops
  `ShoppingListPermissionRepository` for `PermissionsFacade`; `share` creates an invite and must now
  load the list to supply its name as the label.
- **`ShoppingListRepository`** (MODIFY) — `findAllByUserEmail`'s join is replaced by
  `findByIdInOrderByCreatedAtAsc(Collection<UUID>)`.
- **`ShoppingListDto`** (MODIFY) — its `role` field becomes `ResourceRole`.
- **`ShoppingListController`** (MODIFY) — `ShareRequest` / `UnshareRequest` in the signatures, and
  `getSharedUsers` renamed to `getPermissions`, returning the shared `PermissionDto`, and the path
  renamed from `GET /shopping-lists/{id}/users` to `GET /shopping-lists/{id}/permissions`.
  `ShoppingListService.getSharedUsers` is renamed to match, so the name is `getPermissions` at all
  three levels and the path says the same thing.
- **`ShoppingListsExceptionHandler`** (MODIFY) — loses its `ShoppingListAccessDeniedException`
  branch; `ShoppingListNotFoundException` and the item branches stay.
- **DELETE** — `ShoppingListPermission`, `ShoppingListPermissionId`,
  `ShoppingListPermissionRepository`, `shoppinglists/UserRole`, `shoppinglists/dto/SharedUserDto`,
  `ShareShoppingListRequest`, `UnshareShoppingListRequest`,
  `exception/ShoppingListAccessDeniedException`.
- **`R__recompute_limit_usage.sql`** (MODIFY) — the `SHOPPING_LIST` slice and the owner join inside
  the `SHOPPING_LIST_ITEM` slice read `resource_permission` filtered by
  `resource_type = 'SHOPPING_LIST'`. The other three slices are untouched until T2/T3.
- **`SecurityConfig`** (MODIFY) — `/invites/**` added to the authenticated matcher; the chain ends
  in `denyAll()`, so without this every invite endpoint 403s.
- **`ShoppingListIntegrationTest`** (MODIFY) — its sharing tests now assert the handshake.
- **`backend/http/shopping-lists.http`** (MODIFY) + a new `invites.http`.
- **Docs** — new `docs/backend/modules/permissions/{module.md,api.md,db.md}`, the
  `docs/INDEX.md` entry, and the shopping-lists and limits module docs.

## Interfaces and method signatures

### `PermissionsFacade` — the cross-module surface

```java
public class PermissionsFacade {

    // --- access questions -------------------------------------------------
    // Both throw ResourceAccessDeniedException; on success they return the role
    // they validated, so a caller that also needs it for a DTO makes one call.
    ResourceRole requireEditor(String resourceType, UUID resourceId, String email);
    ResourceRole requireOwner (String resourceType, UUID resourceId, String email);

    Optional<ResourceRole> roleOf(String resourceType, UUID resourceId, String email);

    // Every resource of this type the email may reach, with its role. Empty map = none.
    Map<UUID, ResourceRole> accessibleResources(String resourceType, String email);

    Optional<String> ownerEmail(String resourceType, UUID resourceId);

    // --- writes -----------------------------------------------------------
    void grantOwner(String resourceType, UUID resourceId, String email);

    // Throws InviteRefusedException on either refusal rule. Returns the new invite's id.
    UUID invite(String resourceType, UUID resourceId, String targetEmail,
                ResourceRole role, String label, String invitedByEmail);

    // Removes a granted permission OR a pending invite for this email, whichever exists.
    // Throws ResourceAccessDeniedException if the target holds OWNER, or if the requester is
    // removing themselves. No-op if neither a permission nor an invite exists.
    void revoke(String resourceType, UUID resourceId, String targetEmail, String requesterEmail);

    // Granted users first (OWNER, then EDITOR), then pending invites by age.
    List<PermissionDto> getPermissions(String resourceType, UUID resourceId);

    // Destroys every permission and every pending invite for the resource.
    void resourceDeleted(String resourceType, UUID resourceId);
}
```

### Public types

```java
public enum ResourceRole { OWNER, EDITOR }   // wire values unchanged from the four UserRole enums

public record PermissionDto(String email, ResourceRole role, boolean pending) {}

public record ShareRequest(@NotBlank @Email String email, @NotNull ResourceRole role) {}
public record UnshareRequest(@NotBlank @Email String email) {}

// The invitee's list entry. Deliberately carries no resourceId — the resource is
// unreadable while pending and nothing on the invitee's surface needs to address it.
public record PendingInviteDto(UUID id, String resourceType, String label,
                               String invitedBy, ResourceRole role, Instant createdAt) {}

public class ResourceAccessDeniedException extends RuntimeException {}   // -> 403
public class InviteNotFoundException       extends RuntimeException {}   // -> 404
public class InviteRefusedException        extends RuntimeException {    // -> 409
    public Reason reason();
    public enum Reason { ALREADY_INVITED, ALREADY_HAS_ACCESS }
}
```

### `InviteService` — package-private, driven by `InviteController`

```java
class InviteService {
    List<PendingInviteDto> findPendingFor(String email);
    void accept (UUID inviteId, String callerEmail);   // InviteNotFoundException if absent or not theirs
    void decline(UUID inviteId, String callerEmail);
    UUID create(String resourceType, UUID resourceId, String targetEmail,
                ResourceRole role, String label, String invitedByEmail);
}
```

### Repository queries that matter

```java
interface ResourcePermissionRepository
        extends JpaRepository<ResourcePermission, ResourcePermissionId> {

    // Backs accessibleResources — projection, not entities.
    @Query("SELECT p.id.resourceId, p.role FROM ResourcePermission p"
         + " WHERE p.id.email = :email AND p.id.resourceType = :resourceType")
    List<Object[]> findAccessible(String resourceType, String email);

    List<ResourcePermission> findByIdResourceTypeAndIdResourceIdOrderByRoleAsc(String t, UUID id);

    @Query("SELECT p.id.email FROM ResourcePermission p"
         + " WHERE p.id.resourceType = ?1 AND p.id.resourceId = ?2 AND p.role = 'OWNER'")
    Optional<String> findOwnerEmail(String resourceType, UUID resourceId);

    @Modifying
    void deleteByIdResourceTypeAndIdResourceId(String resourceType, UUID resourceId);
}
```

`ORDER BY role ASC` puts `OWNER` first — the enum declares `OWNER` at ordinal 0, so this preserves
today's `ORDER BY role DESC` on the string column without depending on alphabetical luck.

### HTTP surface

| Method | Path | Who | Body / result |
|---|---|---|---|
| `GET` | `/invites` | invitee | `List<PendingInviteDto>`, newest first |
| `POST` | `/invites/{id}/accept` | invitee | 204 |
| `POST` | `/invites/{id}/decline` | invitee | 204 |
| `POST` | `/shopping-lists/{id}/share` | sharer | `{"email":…, "role":"EDITOR"}` → 204, creates an invite |
| `POST` | `/shopping-lists/{id}/unshare` | sharer | `{"email":…}` → 204, revokes a permission **or** cancels a pending invite |
| `GET` | `/shopping-lists/{id}/permissions` | any holder | `[{email, role, pending}]` |

### Schema

```sql
CREATE TABLE resource_permission (
    email         VARCHAR(255) NOT NULL,
    resource_type VARCHAR(64)  NOT NULL,
    resource_id   UUID         NOT NULL,
    role          VARCHAR(16)  NOT NULL CHECK (role IN ('OWNER', 'EDITOR')),
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    PRIMARY KEY (email, resource_type, resource_id)
);
-- Serves getPermissions, ownerEmail and resourceDeleted, none of which know the email.
CREATE INDEX idx_resource_permission_resource ON resource_permission (resource_type, resource_id);
-- One OWNER per resource is an invariant the four old tables held by construction only.
CREATE UNIQUE INDEX uq_resource_permission_owner
    ON resource_permission (resource_type, resource_id) WHERE role = 'OWNER';

CREATE TABLE resource_invite (
    id            UUID PRIMARY KEY,
    resource_type VARCHAR(64)  NOT NULL,
    resource_id   UUID         NOT NULL,
    email         VARCHAR(255) NOT NULL,
    role          VARCHAR(16)  NOT NULL CHECK (role IN ('OWNER', 'EDITOR')),
    invited_by    VARCHAR(255) NOT NULL,
    label         VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    -- Backs the "no second pending invite" rule at the storage layer, not just in code.
    CONSTRAINT uq_resource_invite_target UNIQUE (resource_type, resource_id, email)
);
CREATE INDEX idx_resource_invite_email    ON resource_invite (email);
CREATE INDEX idx_resource_invite_resource ON resource_invite (resource_type, resource_id);

INSERT INTO resource_permission (email, resource_type, resource_id, role)
SELECT email, 'SHOPPING_LIST', shopping_list_id, role FROM shopping_list_permission;
```

Neither table carries a foreign key to a resource table — there are four possible targets and no
polymorphic FK in Postgres. Referential integrity moves from the database to
`PermissionsFacade.resourceDeleted`, which every delete path must call.

## Data flow

### Sharing (invite creation)

1. `ShoppingListController.shareShoppingList` reads the caller's email from the JWT and passes
   `ShareRequest(email, role)` down.
2. `ShoppingListService.shareShoppingList` loads the list — no longer just `existsById`, because it
   needs the name for the label — 404 if absent.
3. `permissionsFacade.requireEditor(SHOPPING_LIST, id, requesterEmail)` — 403 if the caller cannot
   reach the list. Editors may still share onward, unchanged.
4. `permissionsFacade.invite(SHOPPING_LIST, id, targetEmail, role, list.getName(), requesterEmail)`.
5. `InviteService.create` applies both refusal rules, then inserts one `resource_invite` row.
   **No permission row is written**, so nothing in step 6's query can see it.
6. `bob`'s `GET /shopping-lists` calls `accessibleResources(SHOPPING_LIST, "bob@…")`, which reads
   `resource_permission` only. The list is absent; fetching it directly hits `requireEditor` and 403s.

### Accepting

1. `POST /invites/{id}/accept`, caller from the JWT.
2. `InviteService.accept` loads the invite; absent, or belonging to a different email, is a 404 — not
   a 403, so the endpoint never confirms that someone else's invite exists.
3. In one transaction: insert `resource_permission(email, SHOPPING_LIST, resourceId, invite.role)`,
   delete the invite row.
4. The list now appears in `bob`'s `/shopping-lists` and is readable and editable, because it is an
   ordinary granted permission indistinguishable from one created before this feature.

### Listing shopping lists

1. `ShoppingListService.findAll` asks `accessibleResources(SHOPPING_LIST, email)` →
   `Map<UUID, ResourceRole>`.
2. Empty map short-circuits to `List.of()` — an empty `IN ()` is not valid SQL.
3. `shoppingListRepository.findByIdInOrderByCreatedAtAsc(access.keySet())` preserves today's
   creation-date ordering.

T1 does not consume the map's role — `ShoppingListListDto` carries no role — but the map is the shape
T2 needs when composing recipe and collection answers, so it is established here rather than widened
later.

### Deleting a list

1. `requireOwner(SHOPPING_LIST, id, userEmail)` — the owner-only guard, now one call.
2. `permissionsFacade.resourceDeleted(SHOPPING_LIST, id)` — deletes every permission **and** every
   pending invite for the list, so the invite vanishes from `bob`'s `/invites`.
3. `shoppingListRepository.deleteById(id)` (items cascade).
4. `limitsFacade.release(userEmail, SHOPPING_LIST)` and `limitsFacade.clear(id.toString(),
   SHOPPING_LIST_ITEM)` — unchanged, and still last, so a limits failure cannot block the delete.

## Pseudo-code

### `InviteService.create` — the two refusal rules

```
@Transactional
create(resourceType, resourceId, targetEmail, role, label, invitedBy):
    if permissionRepository.existsById(targetEmail, resourceType, resourceId):
        log.warn(...)
        throw InviteRefused(ALREADY_HAS_ACCESS)          # requirements: refused, not silently ignored

    if inviteRepository.existsByResourceAndEmail(resourceType, resourceId, targetEmail):
        log.warn(...)
        throw InviteRefused(ALREADY_INVITED)

    # uq_resource_invite_target still backs this: two concurrent shares to the same email
    # race past the check above, and the loser's constraint violation becomes the same refusal.
    try:
        return inviteRepository.save(new ResourceInvite(...)).id
    except DataIntegrityViolationException:
        throw InviteRefused(ALREADY_INVITED)
```

### `InviteService.accept`

```
@Transactional
accept(inviteId, callerEmail):
    invite = inviteRepository.findById(inviteId) orElseThrow InviteNotFound
    if invite.email != callerEmail:
        # Not a 403: answering "that invite exists but is not yours" leaks it.
        throw InviteNotFound

    # A permission can appear between invite and accept (shared, unshared, re-shared).
    # Accepting then still consumes the invite rather than failing on the primary key.
    if not permissionRepository.existsById(callerEmail, invite.resourceType, invite.resourceId):
        permissionRepository.save(permission(callerEmail, invite.resourceType,
                                             invite.resourceId, invite.role))
    inviteRepository.delete(invite)
    log.info("Invite {} accepted by {}", inviteId, callerEmail)
```

### `PermissionService.revoke` — unshare and cancel are one operation

```
@Transactional
revoke(resourceType, resourceId, targetEmail, requesterEmail):
    # Checked before the lookup: it holds whether the target is a granted user or a
    # pending invite, and it must not depend on what happens to exist.
    if targetEmail == requesterEmail:
        log.warn("{} cannot unshare themselves from {} {}", requesterEmail, resourceType, resourceId)
        throw ResourceAccessDenied

    permission = permissionRepository.findById(targetEmail, resourceType, resourceId)
    if permission present:
        if permission.hasOwnerRights():
            log.warn("Cannot unshare OWNER {} from {} {}", targetEmail, resourceType, resourceId)
            throw ResourceAccessDenied            # the guard all four modules hold today
        permissionRepository.delete(permission)
        return

    # No permission: this is a cancel of a pending invite, if one exists.
    inviteRepository.deleteByResourceAndEmail(resourceType, resourceId, targetEmail)
    # Absent from both is a no-op, matching today's deleteById-is-a-no-op behaviour.
```

Both guards live here, so all four modules hold both. The self-unshare guard is recipes' rule today;
extending it to the other three is a **behaviour change** — an `EDITOR` of a shopping list,
collection or meal plan can currently remove their own permission to leave the resource, and after
this they cannot. It is invisible to the app: `SharingDialog` already suppresses the remove button
for the current user, so no client ever issues the call. Nothing else replaces "leave a shared
resource" — if that is wanted it needs its own endpoint, since an unshare cannot distinguish leaving
from being removed.

## Decisions made

- **Two tables, not one with a state column** — a permission query structurally cannot return a
  pending row, so ADR-0007's "cannot leak by construction" property holds without anyone remembering
  a filter. Their columns barely overlap anyway: only an invite has an id, a sender and a label.
- **One versioned copy per task** — T1's `V20__` copies `shopping_list_permission` only. Data and
  code move together per module, so no module ever has rows in two places. T2 and T3 copy theirs.
- **Typed resource key: `resource_type VARCHAR(64)` + `resource_id UUID`** — all four shareable types
  are UUID-keyed, so nothing is given up, and `accessibleResources` hands back `UUID`s the resource
  module drops straight into a query.
- **Resource types stay opaque strings with no registry** — an unseen key answers "no access",
  indistinguishable from any other miss; the module has no registry to tell the two apart.
  `ShoppingListService.SHOPPING_LIST_RESOURCE` already exists as the constant `limits` uses; the same
  constant serves `permissions`, so the key is declared once per module, by the module that owns it.
- **The facade refuses directly** — `requireEditor` / `requireOwner` throw a shared
  `ResourceAccessDeniedException`, so a module cannot check and forget to act. The four
  `*AccessDeniedException` classes go away as each module migrates, changing all four 403 bodies —
  T4/T5 carry the mobile catch-up.
- **`requireEditor` returns the role it validated** rather than `void` — `ShoppingListDto` carries a
  `role`, and a `void` signature would force a second lookup for it. The decision is still made
  inside the facade; the caller receives the role as a fact, not as something to branch on.
- **`accessibleResources` returns `Map<UUID, ResourceRole>`, not `List<UUID>`** — same two queries,
  and T2 needs the role per resource when composing recipe and collection answers.
- **`/unshare` cancels a pending invite** — one `revoke` handles both, so cancel is written once and
  T5's dialog gets it from the button it already has. The sharer never needs an invite id.
- **Both unshare guards apply to all four modules** — a caller may not unshare an `OWNER`, and may
  not unshare themselves. The second is recipes' rule alone today; putting it in `revoke` makes the
  four behave identically, which is what the shared module exists for, and costs nothing visible
  because `SharingDialog` already hides the remove button for the current user. This does narrow
  what the API allows — see the note under the `revoke` pseudo-code.
- **`revoke` takes the requester's email** — the self-unshare guard needs it, and passing it keeps
  the decision inside the facade rather than asking four modules to compare emails themselves.
- **The listing type is `PermissionDto` and the method is `getPermissions`** at facade, service and
  controller — the module's vocabulary is permissions, and the list now carries pending invites as
  well as people, so "shared users" no longer describes it. **The path follows the vocabulary:
  `/{id}/users` becomes `/{id}/permissions`**, and recipes' `/{uuid}/shared_users` becomes
  `/{uuid}/permissions` in T2 — the two spellings the four modules use today collapse into one that
  matches what the endpoint returns, which is no longer a list of users.
- **`GET /<resource>/{id}/permissions` grows a `pending` boolean** rather than gaining a sibling
  endpoint — one request feeds the whole dialog. A boolean, not a status enum: decline and cancel
  destroy the row, so there is no third state to leave room for.
- **Shared public types** — `ResourceRole`, `PermissionDto`, `ShareRequest` and `UnshareRequest` live
  in `permissions`; the four copies of each are deleted as their module migrates. Role wire values
  are unchanged, so the path rename above is the breaking change: all four resource types move to
  `/{id}/permissions` as they migrate (shopping lists here, recipes in T2, collections and meal plans
  in T3), and every mobile repository that calls one needs its URL updated in T5.
- **`PendingInviteDto` carries no `resourceId`** — the resource is unreadable while pending, accept
  and decline address the invite by its own id, and after accepting the resource surfaces in its own
  list. Withholding it keeps the endpoint from handing out a reference to something the caller cannot
  read.
- **A wrong-owner invite id is a 404, not a 403** — a 403 confirms the invite exists.
- **A partial unique index enforces one OWNER per resource** — an invariant the four old tables held
  by construction and never stated.
- **`InviteController` talks to the package-private services, not through `PermissionsFacade`** —
  unlike `LimitsController`, which goes through its facade, nothing outside the module calls accept
  or decline, so the facade stays the cross-module surface only.

## Assumptions to verify

- **Assumption:** every existing `shopping_list_permission` row has exactly one `OWNER` per list.
  **If wrong:** `V20__`'s `uq_resource_permission_owner` index fails to build and the migration
  aborts. Check with a `GROUP BY shopping_list_id HAVING count(*) FILTER (WHERE role='OWNER') <> 1`
  against production before shipping; if the data is dirty, drop the index from the migration and
  raise it separately.
- **Assumption:** no path outside `ShoppingListService` reads `shopping_list_permission`.
  `ShoppingListRepository.findAllByUserEmail` and `R__recompute_limit_usage.sql` are the two found;
  a missed third would keep reading a table that stops being written after T1.
  **If wrong:** silent divergence — a stale read that looks correct until someone shares.
- **Assumption:** the `IN (:ids)` list in `findByIdInOrderByCreatedAtAsc` stays small. It is bounded
  by the user's `SHOPPING_LIST` quota, so this holds while quotas are enforced.
  **If wrong:** the query degrades for a user with the kill-switch off and thousands of lists; the
  fix is a join, which would mean reopening the boundary decision.
- **Assumption:** `limit_usage` is byte-identical before and after the recompute is repointed. This
  is the task's cheapest correctness signal and the one the `How to verify` step 8 rests on — worth
  an integration test using the existing `RecomputeMigration.run(dataSource)` helper rather than a
  manual check.
  **If wrong:** the migration lost or duplicated ownership rows, which is an access-control bug and a
  quota bug at once.
- **Assumption:** dropping `ShoppingListAccessDeniedException` breaks nothing but its own handler
  branch. **If wrong:** a mobile client keying off the 403 `title` misbehaves — which T5 must handle
  regardless, since the body changes.
- **Assumption:** an invite's label fits `VARCHAR(255)`. Shopping list names are `VARCHAR(255)`, so
  this holds for T1; T2/T3 must confirm it for recipe and collection names.
- **Assumption:** `ORDER BY role ASC` on the JPA enum ordinal produces OWNER-first. The entity maps
  `role` with `@Enumerated(EnumType.STRING)`, so the sort is on the *string* — verify it orders
  `EDITOR` before `OWNER` alphabetically and flip the direction, or sort in Java, rather than
  assuming.

## Required reading

- `docs/ADRs/0007-shared-permissions-module.md` — the boundary, what the module owns, and why
  composition stays with the composing module.
- `docs/ADRs/0008-invite-label-snapshot.md` — why the label is supplied by the inviting module and
  stored opaquely.
- `HLD.md` > Feature areas > Shared `permissions` module — the behaviours this task implements.
- `docs/backend/modules/limits/module.md` — the module this one is patterned on: facade shape,
  boundary, architecture test, recompute.
- `backend/src/main/java/xyz/stasiak/recipai/limits/LimitsFacade.java` and
  `src/test/java/xyz/stasiak/recipai/limits/LimitsModuleArchitectureTest.java` — the two patterns to
  mirror directly.
- `backend/src/main/java/xyz/stasiak/recipai/shoppinglists/ShoppingListService.java` — every call
  site this task rewrites, including the three-shape access checks.
- `backend/src/main/resources/db/migration/R__recompute_limit_usage.sql` — the two slices repointed
  here, and the FLOW-exclusion pattern to preserve when editing them.
- `docs/backend/standards/module-structure.md` and `java-patterns.md` — facade, exception-handler,
  visibility and record conventions the new module must follow.
- `docs/backend/standards/integration-tests.md` — the suite shape, and the rule about seeding and
  reading through the module's own business methods.
- `docs/tasks/2026-08-26-share-invites/research/sharing-and-permissions.md` — the full inventory of
  what exists today, for the T2/T3 call sites this task must not break.
