# API Plan - Shopping Lists

## Overview

Collaborative shopping lists with offline-first, operation-based sync. Provides CRUD for lists, sharing (OWNER/EDITOR),
and a batch operations endpoint for items supporting optimistic concurrency and reconciliation via authoritative
snapshots.

## API Endpoints

### List Shopping Lists

* Path: [GET] /shopping-lists
* Purpose: Return lists the caller owns or has been shared on.
* Authentication: OAuth2/JWT required (401 if missing)
* Request Parameters:
    - Path: —
    - Query: —
    - Body: —
* Response:
    - Success (200): [{id, name}]
    - Error (4xx/5xx): Problem Details (application/problem+json)
* Notes: Only returns lists visible to the caller.

### Create Shopping List

* Path: [POST] /shopping-lists
* Purpose: Create a new list with the caller as OWNER.
* Authentication: OAuth2/JWT required (401 if missing)
* Request Parameters:
    - Path: —
    - Query: —
    - Body: {name}
* Response:
    - Success (201): {id, name} (Location: /shopping-lists/{id})
    - Error (400/401): Problem Details
* Notes: Name required; uniqueness not enforced globally.

### Get Shopping List (with items)

* Path: [GET] /shopping-lists/{id}
* Purpose: Retrieve list metadata and full items snapshot.
* Authentication: OAuth2/JWT required; 404 masking for unauthorized
* Request Parameters:
    - Path: {id: UUID}
    - Query: —
    - Body: —
* Response:
    - Success (200): {id, name, items:[{id, name, quantity, unit, checked, position, version}]}
    - Error (401/404): Problem Details
* Notes: Items ordered by position ASC.

### Delete Shopping List

* Path: [DELETE] /shopping-lists/{id}
* Purpose: Delete a list (OWNER only).
* Authentication: OAuth2/JWT required; 404 masking for unauthorized/non-member
* Request Parameters:
    - Path: {id: UUID}
    - Query: —
    - Body: —
* Response:
    - Success (204): No body
    - Error (401/404/403): Problem Details
* Notes: Cascades delete of items at DB level. Memberships removed in application logic.

### List Shared Users

* Path: [GET] /shopping-lists/{id}/shared_users
* Purpose: Return all shared emails and roles, including latent (not-yet-registered) users.
* Authentication: OAuth2/JWT required; 404 masking for unauthorized
* Request Parameters:
    - Path: {id: UUID}
    - Query: —
    - Body: —
* Response:
    - Success (200): [{email, role}]
    - Error (401/404): Problem Details
* Notes: Does not include caller’s permissions in standard GETs elsewhere.

### Share Shopping List

* Path: [POST] /shopping-lists/{id}/share
* Purpose: Share a list with an email (OWNER can share anyone; EDITOR can share EDITORs).
* Authentication: OAuth2/JWT required; 404 masking for unauthorized
* Request Parameters:
    - Path: {id: UUID}
    - Query: —
    - Body: {email}
* Response:
    - Success (204): No body (duplicate shares silently ignored)
    - Error (400/401/404): Problem Details
* Notes: Accepts any syntactically valid email; latent memberships allowed.

### Unshare Shopping List

* Path: [POST] /shopping-lists/{id}/unshare
* Purpose: Remove a collaborator. EDITOR can unshare EDITORs (including self); EDITOR cannot remove OWNER; OWNER cannot
  remove themselves.
* Authentication: OAuth2/JWT required; 404 masking for unauthorized
* Request Parameters:
    - Path: {id: UUID}
    - Query: —
    - Body: {email}
* Response:
    - Success (204): No body
    - Error (400/401/404): Problem Details
* Notes: Unsharing a non-member returns 400 (Problem Details).

### Apply Item Operations (Batch)

* Path: [POST] /shopping-lists/{id}/operations
* Purpose: Apply a batch of item operations atomically; return authoritative items snapshot.
* Authentication: OAuth2/JWT required; 404 masking for unauthorized
* Request Parameters:
    - Path: {id: UUID}
    - Query: —
    - Body: {
      operations: [
      {operationId: UUID, type: "ADD_ITEM", item:{id:UUID, name, quantity(NUMERIC|null), unit|null}}
      {operationId: UUID, type: "UPDATE_ITEM", item:{id:UUID, name, quantity(NUMERIC|null), unit|null, version:long}}
      {operationId: UUID, type: "CHECK_ITEM", item:{id:UUID, version:long}}
      {operationId: UUID, type: "UNCHECK_ITEM", item:{id:UUID, version:long}}
      {operationId: UUID, type: "DELETE_ITEM", item:{id:UUID, version:long}}
      ]
      }
* Response:
    - Success (200): {
      items:[{id, name, quantity, unit, checked, position, version}],
      acceptedOperations:[UUID],
      rejectedOperations:[UUID]
      }
    - Error (400/401/404): Problem Details
* Notes:
    - Operations executed sequentially in request order in a single transaction.
    - ADD_ITEM appends to end: server sets position = max(position)+1.
    - Version required for all except ADD_ITEM.
    - When multiple ops in a batch target the same item, validate against the provisional version from earlier ops in
      that batch.
    - No server-side de-duplication of operationId.
    - Server persists rejected operations with snapshot for audit; response does not include rejection reasons.
    - Use this endpoint for bulk actions (e.g., “uncheck all”, “delete checked”) via fan-out of
      UNCHECK_ITEM/DELETE_ITEM.
    - Reordering (MOVE_ITEM) deferred; no server-side reordering in MVP.

## Data Models

### ShoppingList

```json
{
  "id": "uuid | List identifier",
  "name": "string | List name (<=255)"
}
```

* Validation Rules: name required, length <=255.
* Relationships: 1..N with ShoppingListItem via list_id. N..M with users via UserShoppingList.

### ShoppingListItem

```json
{
  "id": "uuid | Item identifier",
  "name": "string | Required; <=255",
  "quantity": "number|null | NUMERIC(12,3); null if unparsable",
  "unit": "string|null | Optional; <=64",
  "checked": "boolean | Default false",
  "position": "integer | Ordering within list",
  "version": "integer | Monotonic item version for optimistic concurrency"
}
```

* Validation Rules:
    - name required (<=255)
    - unit optional (<=64)
    - quantity null or NUMERIC(12,3) (HALF_UP rounding on client)
* Relationships: Belongs to one ShoppingList.

### UserShoppingList

```json
{
  "list_id": "uuid | Target list",
  "email": "string | Collaborator email",
  "role": "string | 'OWNER' or 'EDITOR'"
}
```

* Validation Rules: email syntactically valid; role in {'OWNER','EDITOR'}.
* Relationships: Associates users (by email) to lists; supports latent users.

### Operation (request element)

```json
{
  "operationId": "uuid | Client-generated",
  "type": "string | ADD_ITEM | UPDATE_ITEM | CHECK_ITEM | UNCHECK_ITEM | DELETE_ITEM",
  "item": "object | See per-type requirements"
}
```

* Validation Rules:
    - ADD_ITEM: item{id, name, quantity|null, unit|null}; no version
    - UPDATE_ITEM: item{id, name, quantity|null, unit|null, version}
    - CHECK_ITEM/UNCHECK_ITEM/DELETE_ITEM: item{id, version}
* Relationships: Applies to a ShoppingListItem within a ShoppingList.

### OperationsResponse

```json
{
  "items": [
    {
      "id": "uuid",
      "name": "string",
      "quantity": "number|null",
      "unit": "string|null",
      "checked": "boolean",
      "position": "integer",
      "version": "integer"
    }
  ],
  "acceptedOperations": [
    "uuid"
  ],
  "rejectedOperations": [
    "uuid"
  ]
}
```

* Validation Rules: —
* Relationships: items belong to the requested list.

### ProblemDetails (RFC 7807)

```json
{
  "type": "string | URI identifying the error category",
  "title": "string",
  "status": "number",
  "detail": "string",
  "instance": "string",
  "code": "string | stable machine code e.g., 'stale-version', 'not-member', 'invalid-email', 'validation-failed', 'not-allowed'"
}
```

* Notes: Use for 400/401/404/409. Operations endpoint uses 200 for mixed accept/reject; Problem Details only for
  request-level errors.

## Integration Points

* Existing System/Service:
    - OAuth2/JWT Resource Server (Spring Security). Email claim used as principal.
    - Client uses Recipes API to fetch ingredients; client decides merges and issues UPDATE_ITEM accordingly (no server
      implicit merge on ADD).
* Database Tables:
    - shopping_lists(id UUID PK, name TEXT NOT NULL)
    - shopping_list_items(id UUID PK, list_id UUID FK, name TEXT NOT NULL, quantity NUMERIC(12,3) NULL, unit TEXT NULL,
      checked BOOLEAN NOT NULL DEFAULT FALSE, position INT NOT NULL, version BIGINT NOT NULL)
    - user_shopping_lists(list_id UUID, email TEXT NOT NULL, role ENUM('OWNER','EDITOR'), PRIMARY KEY(list_id, email))
    - rejected_operations(operation_id UUID PK, operation JSONB NOT NULL, items_snapshot JSONB NOT NULL)
* External Services: None in MVP.

## Cross-Cutting Notes

- Base path unversioned: /shopping-lists.
- AuthZ rules:
    - OWNER: delete list; share/unshare anyone; cannot remove self.
    - EDITOR: modify items; share/unshare EDITORs; cannot remove OWNER; can self-unshare.
- Unauthorized list access masked as 404 on list-specific endpoints.
- Caching/pagination: none in MVP.
- Reordering: MOVE_ITEM deferred; in MVP server appends new items (position = max+1). Client-side drag/drop not
  persisted server-side yet.
- Offline-first: Client queues operations; reconciliation via items snapshot from operations response.
