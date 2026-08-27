# Permissions — Database Schema

## Tables

### resource_permission

- email: VARCHAR(255) NOT NULL
- resource_type: VARCHAR(64) NOT NULL — an opaque key owned by the calling module, e.g. `SHOPPING_LIST`
- resource_id: UUID NOT NULL
- role: VARCHAR(16) NOT NULL CHECK (role IN ('OWNER', 'EDITOR'))
- created_at: TIMESTAMP NOT NULL DEFAULT now()
- PRIMARY KEY (email, resource_type, resource_id)

### resource_invite

- id: UUID PRIMARY KEY
- resource_type: VARCHAR(64) NOT NULL
- resource_id: UUID NOT NULL
- email: VARCHAR(255) NOT NULL — the invitee
- role: VARCHAR(16) NOT NULL CHECK (role IN ('OWNER', 'EDITOR')) — the role granted on accept
- invited_by: VARCHAR(255) NOT NULL
- label: VARCHAR(255) NOT NULL — an opaque snapshot the inviting module supplies; see
  `docs/ADRs/0008-invite-label-snapshot.md`
- created_at: TIMESTAMP NOT NULL DEFAULT now()
- CONSTRAINT uq_resource_invite_target UNIQUE (resource_type, resource_id, email) — backs the
  "no second pending invite" refusal rule at the storage layer, not just in code

## Relationships

Neither table carries a foreign key to a resource table — there are four possible targets and no
polymorphic FK in Postgres. Referential integrity is enforced in application code:
`PermissionsFacade.resourceDeleted(resourceType, resourceId)` deletes every permission and every
pending invite for a resource, and every resource module's delete path must call it. See
`docs/ADRs/0007-shared-permissions-module.md`.

- One resource has at most one `OWNER` row in `resource_permission` (`uq_resource_permission_owner`
  below) and any number of `EDITOR` rows.
- A resource may have both granted permissions and pending invites at once — they are different
  emails, since an email with a permission is refused a second invite (`ALREADY_HAS_ACCESS`).

## Indexes

- Composite primary key index on `resource_permission(email, resource_type, resource_id)`
- `idx_resource_permission_resource` on `resource_permission(resource_type, resource_id)` — serves
  `getPermissions`, `ownerEmail` and `resourceDeleted`, none of which know the email
- `uq_resource_permission_owner`, a partial unique index on `resource_permission(resource_type,
  resource_id) WHERE role = 'OWNER'` — enforces one `OWNER` per resource
- Primary key index on `resource_invite(id)`
- `idx_resource_invite_email` on `resource_invite(email)` — serves the cross-resource pending list
- `idx_resource_invite_resource` on `resource_invite(resource_type, resource_id)` — serves
  `getPermissions` and `resourceDeleted`
