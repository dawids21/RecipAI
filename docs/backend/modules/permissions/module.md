# Permissions Module

Owns access control for every shareable resource: granted permissions, pending invites, the role
predicates (`hasOwnerRights()` / `hasEditorRights()`), and the invite handshake — create, accept,
decline, cancel. Holds no domain knowledge — callers pass an opaque resource type key and a resource
UUID (see `docs/ADRs/0007-shared-permissions-module.md`). Granted permissions and pending invites live
in two separate tables, so a pending invite is structurally incapable of answering an access query.

## Codebase Structure

```
backend/src/main/java/xyz/stasiak/recipai/
└── permissions/
    ├── dto/
    │   ├── ResourceRole.java                    # Public enum OWNER, EDITOR
    │   ├── PermissionDto.java                   # Public record (email, role, pending)
    │   ├── PendingInviteDto.java                # Public record — the invitee's list entry; carries no resourceId
    │   ├── ShareRequest.java                    # Public record (email, role)
    │   └── UnshareRequest.java                  # Public record (email)
    ├── exception/
    │   ├── ResourceAccessDeniedException.java   # Public — 403
    │   ├── InviteNotFoundException.java         # Public — 404
    │   ├── InviteRefusedException.java          # Public — 409, carries a machine-readable Reason
    │   └── InvalidInviteRoleException.java      # Public — 400, a ShareRequest.role the module cannot grant via an invite (OWNER)
    ├── PermissionsFacade.java                   # Public facade — delegates single-service calls to PermissionService, coordinated calls to PermissionsApplicationService, logs
    ├── PermissionsApplicationService.java       # Package-private — owns the @Transactional boundary for invite, acceptInvite, revoke, getPermissions, resourceDeleted
    ├── PermissionService.java                   # Package-private — owns ResourcePermissionRepository: role lookups, accessible-resources, owner email, grant, revoke (self-unshare and owner-unshare guards), granted-permission listing, deletion
    ├── InviteService.java                       # Package-private — owns ResourceInviteRepository: invite create (both refusal rules), accept, decline, cancel, cross-resource pending list, deletion
    ├── InviteController.java                    # Package-private @RestController — /invites, the invitee's surface only (GET, accept, decline); pending list and decline talk to InviteService directly, accept goes through PermissionsApplicationService
    ├── PermissionsExceptionHandler.java         # Package-private @RestControllerAdvice for the four exceptions in exception/
    ├── ResourcePermission.java                  # Entity over resource_permission — composite key (email, resourceType, resourceId), owns hasOwnerRights()/hasEditorRights()
    ├── ResourcePermissionId.java                # Embeddable composite key (email, resourceType, resourceId)
    ├── ResourcePermissionRepository.java        # Role lookup, accessible-resources projection, per-resource listing (OWNER first), owner-role lookup, delete-all-for-resource
    ├── ResourceInvite.java                      # Entity over resource_invite — UUID primary key, target email, role to grant, sender, stored label
    ├── ResourceInviteRepository.java            # By-email (cross-resource), per-resource listing, existence checks behind the refusal rules, delete-all-for-resource
    └── AcceptedInvite.java                      # Package-private record (resourceType, resourceId, role) — returned by InviteService.accept
```

## Module Boundary

`permissions` holds no domain knowledge: callers pass an opaque `resourceType` string (owned by the
calling module, e.g. `ShoppingListService.SHOPPING_LIST_RESOURCE`) and a `resourceId` UUID.
`PermissionsModuleArchitectureTest` enforces this with ArchUnit — no class in `..permissions..` may
depend on any other `xyz.stasiak.recipai` package, and only `PermissionsFacade`, `ResourceRole`,
`PermissionDto`, `ShareRequest`, `UnshareRequest`, `PendingInviteDto`,
`ResourceAccessDeniedException`, `InviteNotFoundException`, `InviteRefusedException` (and its nested
`Reason` enum), and `InvalidInviteRoleException` may be public. See
`docs/ADRs/0007-shared-permissions-module.md`.

Recipes' collection-derived access is the one exception to "the module answers every access
question": that composition stays with `recipes`, which asks the facade twice (recipe, collection) and
combines the answers itself — the module never learns that recipes belong to collections.

## Behaviour

- **Two tables, not a state column** — `resource_permission` (granted) and `resource_invite`
  (pending) are separate, so a permission query structurally cannot return a pending row. Only an
  invite has an id, a sender and a label; see `db.md`.
- **Access questions** — `requireEditor` / `requireOwner` throw `ResourceAccessDeniedException`
  directly and return the role they validated, so a caller cannot check and forget to act. `roleOf`
  answers without throwing — an unseen resource type answers "no access", the same as any other miss;
  the module holds no registry to tell the two apart. `accessibleResources` returns every resource of
  a type an email may reach, as `Map<UUID, ResourceRole>`.
- **Invite lifecycle** — `invite` first rejects `role == OWNER` (`InvalidInviteRoleException`, 400 —
  the module has no ownership-transfer operation, so an invite can never be created at a role it
  cannot grant), then creates a pending row after the two refusal rules (`InviteRefusedException`, see
  **Refusal rules** below); `InviteController`'s accept/decline act on the invitee's own pending
  invites, address-checked so a wrong-owner invite id is a 404, never a 403 (a 403 would confirm the
  invite exists). Accepting is a local state change — insert the permission, delete the invite, one
  transaction, no event.
- **Refusal rules** — an invite is refused (not silently ignored) when the target already holds a
  permission (`ALREADY_HAS_ACCESS`, this is also how inviting the resource's own owner is refused) or
  already has a pending invite for the same resource (`ALREADY_INVITED`). The second rule is also
  backed by `uq_resource_invite_target` at the storage layer, so two concurrent shares to the same
  email cannot both create a row — the loser's constraint violation becomes the same refusal.
- **`revoke` — unshare and cancel are one operation** — removes a granted permission if one exists,
  otherwise cancels a pending invite, otherwise no-ops. Two guards apply to every resource type: a
  caller may not unshare an `OWNER`, and may not unshare themselves (checked before the lookup, so it
  holds whether the target is granted or pending).
- **Coordinated across two services** — invite, accept, revoke, listing and deletion each touch both
  `PermissionService` and `InviteService`; `PermissionsApplicationService` owns the `@Transactional`
  boundary for each of these five operations, so neither service depends on the other's repository —
  see `docs/backend/standards/module-structure.md` > Application Service for Multi-Service
  Coordination.
- **`getPermissions`** — granted users first (`OWNER` then `EDITOR`), then pending invites by age,
  each `PermissionDto.pending` set accordingly. One query result feeds a resource's whole sharing
  dialog.
- **`resourceDeleted`** — destroys every permission and every pending invite for a resource. Every
  module's delete path must call this; there is no foreign key enforcing it (see `db.md`).
- **Unseen resource types** — a resource type nobody has ever granted a permission for answers "no
  access" rather than throwing; the module has no registry of valid resource type keys to check
  against.
- **Labels are opaque snapshots** — an invite's `label` is supplied by the inviting module at creation
  time and never interpreted or refreshed; see `docs/ADRs/0008-invite-label-snapshot.md`.

## Consumers

- `shoppinglists` — holds every shopping-list permission and pending invite, and `share` creates an
  invite carrying the list's name as its label.
- `recipes` — holds every direct recipe permission and pending invite, and `share` creates an invite
  carrying the recipe's name as its label. Composes the facade's answer with collection-derived access
  itself; see `docs/backend/modules/recipes/module.md` > Access Composition and
  `docs/ADRs/0007-shared-permissions-module.md`.

Recipe collections and meal plans own their permission tables directly and do not call this module.
