<conversation_summary>
<decisions>

1. API base path:
    - Use unversioned paths under /shopping-lists for this feature.

2. Operations endpoint and batch semantics:
    - Endpoint: POST /shopping-lists/{listId}/operations accepts an array of operations.
    - Apply operations sequentially in request order within a single transaction.
    - Version is required for all operations except ADD_ITEM.
    - When multiple operations in a batch target the same item, validate each operation against the provisional version
      resulting from earlier operations in that batch (group by item).
    - Response always includes an authoritative full items snapshot and lists acceptedOperations and
      rejectedOperations (without reasons).
    - No explicit server-side deduplication for operationId (UUID).

3. Operation types (finalized):
    - ADD_ITEM {operationId, item:{id(UUID), name, quantity(NUMERIC|null), unit|null}}
    - UPDATE_ITEM {operationId, item:{id(UUID), name, quantity(NUMERIC|null), unit|null, version}}
    - CHECK_ITEM {operationId, item:{id(UUID), version}}
    - UNCHECK_ITEM {operationId, item:{id(UUID), version}}
    - DELETE_ITEM {operationId, item:{id(UUID), version}}
    - Deferred: MOVE_ITEM (no server-side reordering in MVP; do not update PRD text regarding move).

4. Quantity parsing and storage:
    - Fields: name (TEXT), quantity (NUMERIC(12,3) | NULL), unit (TEXT | NULL).
    - If client parse succeeds: send quantity as numeric (scale 3, HALF_UP) and unit (or NULL); name is the item text.
    - If unit not found: quantity numeric, unit=NULL; name as entered (client may normalize).
    - If parse fails: send quantity=NULL, unit=NULL; name contains the full free-text line.

5. Merge behavior:
    - Client decides merge opportunities and uses UPDATE_ITEM to adjust existing items.
    - Server does not implicitly merge on ADD when name+unit collide.

6. Database schema:
    - shopping_lists(id UUID PK, name TEXT NOT NULL)
    - shopping_list_items(id UUID PK, list_id UUID FK, name TEXT NOT NULL, quantity NUMERIC(12,3) NULL, unit TEXT NULL,
      checked BOOLEAN NOT NULL DEFAULT FALSE, position INT NOT NULL, version BIGINT NOT NULL)
    - user_shopping_lists(list_id UUID, email TEXT NOT NULL, role ENUM('OWNER','EDITOR'), PRIMARY KEY(list_id, email))
    - Cascade delete items on list deletion at DB level.
    - Cascade delete memberships (user_shopping_lists) handled in application code (not a DB constraint).
    - No custom indexes for MVP.

7. Sharing and collaboration:
    - Share to any syntactically valid email (not restricted to registered users).
    - GET /shopping-lists/{id}/shared_users returns all shared emails, including not-yet-registered addresses.
    - When sharing to an email already present for the list, server silently rejects the duplicate (no error in
      response).
    - Authorization rules:
        - OWNER: can delete list; share/unshare anyone; cannot remove themselves.
        - EDITOR: can modify items; can unshare EDITORs; cannot remove OWNER; can leave (self-unshare).
    - Unsharing a non-member returns 400 (Problem Details).

8. Endpoints (feature-specific):
    - GET /shopping-lists → [{id, name}]
    - POST /shopping-lists {name} → {id, name}
    - GET /shopping-lists/{id} → {id, name, items:[{id, name, quantity, unit, checked, position, version}]}
    - DELETE /shopping-lists/{id} (OWNER only)
    - GET /shopping-lists/{id}/shared_users → [{email, role}]
    - POST /shopping-lists/{id}/share {email}
    - POST /shopping-lists/{id}/unshare {email}
    - POST /shopping-lists/{id}/operations → batch operations as defined.

9. Authentication/authorization and security:
    - Use existing OAuth2/JWT resource server; map email claim.
    - Do not include user role in standard GET responses.
    - 401 for unauthenticated requests.
    - Mask unauthorized lists as 404 where applicable.

10. HTTP semantics, caching, and status codes:

- Use 200 for mixed batch results (some accepted, some rejected).
- Errors: 400, 401, 404, 409 as applicable using RFC7807 Problem Details.
- No ETag/If-None-Match; no pagination on list views.
- No additional rate limiting in MVP.

11. Observability and failure recording:

- No additional monitoring/logging beyond minimal needs.
- Persist rejected operations and items snapshot:
    - rejected_operations(operationId UUID PK, operation JSONB NOT NULL, items_snapshot JSONB NOT NULL)
- Retention: forever.
- Log batch operations when any operation is rejected (implementation detail), but no general log retention beyond the
  persisted rejected_operations.
  </decisions>

<matched_recommendations>

1. Operation schema and idempotent state changes — Accepted/Modified:
    - Replaced TOGGLE_ITEM with CHECK_ITEM and UNCHECK_ITEM; per-item version required (except ADD_ITEM); intra-batch
      provisional versioning applied.

2. MOVE_ITEM — Deferred:
    - Reordering not implemented server-side in MVP; PRD left unchanged; clients should not rely on move semantics yet.

3. Sharing scope and visibility — Modified:
    - Allow any email (latent membership). GET /shared_users includes not-registered emails. Duplicate share is silently
      ignored; no global dedup store.

4. AuthZ policy — Accepted/Modified:
    - OWNER/EDITOR rules as specified; EDITOR can self-unshare; cannot remove OWNER; unsharing non-member is 400 (was
      previously idempotent in older plan).

5. Data model and precision — Accepted/Modified:
    - NUMERIC(12,3) for quantity with client-side parsing and HALF_UP rounding when numeric; NULL when unparsable; unit
      nullable.

6. Endpoint and error contracts — Accepted:
    - Unversioned /shopping-lists base; operations endpoint returns {acceptedOperations, rejectedOperations, items};
      Problem Details for errors; no ETag/pagination.

7. Observability and persistence — Modified:
    - No monitoring; persist only rejected operations with authoritative snapshot; retain indefinitely.

8. Indexing and cascade strategy — Modified:
    - No custom indexes initially; DB-level cascade for items; app-level cascade for memberships.

9. Rate limiting and payload guardrails — Rejected:
    - No limits introduced for MVP.
      </matched_recommendations>

<api_planning_summary>
a. Main API requirements:

- CRUD for shopping lists; share/unshare management; batch operation-based sync for list items.
- Optimistic concurrency via per-item version checking (first-write-wins); intra-batch provisional versioning when
  multiple operations target the same item.
- Offline-first client queues operations and reconciles against the authoritative snapshot returned from the operations
  endpoint.
- Client handles parsing/normalization and merge decisions; server persists what is sent (no implicit merge on ADD).

b. Key resources and endpoints:

- Resources:
    - ShoppingList {id, name}
    - ShoppingListItem {id, list_id, name, quantity(NUMERIC|NULL), unit(NULLABLE), checked, position, version}
    - UserShoppingList {list_id, email, role in {OWNER, EDITOR}} (includes latent emails)
    - RejectedOperation {operationId, operation(JSONB), items_snapshot(JSONB)}
- Endpoints:
    - GET /shopping-lists → [{id, name}]
    - POST /shopping-lists {name} → {id, name}
    - GET /shopping-lists/{id} → {id, name, items:[...]}
    - DELETE /shopping-lists/{id} (OWNER)
    - GET /shopping-lists/{id}/shared_users → [{email, role}]
    - POST /shopping-lists/{id}/share {email}
    - POST /shopping-lists/{id}/unshare {email}
    - POST /shopping-lists/{id}/operations → array of {operationId, type
      in [ADD_ITEM|UPDATE_ITEM|CHECK_ITEM|UNCHECK_ITEM|DELETE_ITEM], item:{...}}

c. Data models and relationships:

- shopping_lists 1..N shopping_list_items via list_id.
- user_shopping_lists associates emails to lists with OWNER/EDITOR roles; supports latent (not-registered) emails.
- rejected_operations stores failed ops and authoritative items snapshot at time of failure; accepted ops are not
  persisted.

d. Authentication, authorization, security:

- Spring Security OAuth2 Resource Server with JWT; email claim used as principal identifier.
- Authorization via user_shopping_lists: OWNER can delete list and manage sharing; EDITOR can modify items and unshare
  EDITORs; EDITOR can leave; cannot remove OWNER.
- Unauthenticated requests → 401. Unauthorized list access → 404 masking.
- Do not include user role in standard GET responses.

e. Integration points:

- Recipes API remains separate (e.g., GET /recipes/{uuid}); client composes operations to add or update items based on
  recipe ingredients.
- Mobile client handles queuing, debounced sync (~500ms after action), and periodic sync (~10s when list active) per
  ADRs.

f. Performance, scalability, caching:

- No ETag/If-None-Match and no pagination (expected small list sizes).
- Server processes each batch in a single transaction and returns full snapshot for reconciliation.
- No rate limiting or payload limits in MVP; clients expected to batch reasonably (e.g., fan-out UNCHECK_ITEM for "
  uncheck all").

g. Error handling and diagnostics:

- Use RFC7807 application/problem+json for 400 (validation, unshare non-member), 401, 404 (masking), 409 (stale
  version).
- Operations response omits rejection reasons; server persists rejected operations and snapshot for audit and debugging.
- No centralized monitoring or log retention; rejected_operations is the durable audit source for failures.

h. Tech stack alignment:

- Backend: Java 24, Spring Boot 3.5.x, Spring Data JPA, PostgreSQL 17.x, Flyway, Spring Security OAuth2 Resource Server.
- Database migrations to add shopping list tables and rejected_operations table; app-level cascade for memberships; DB
  cascade for items.
  </api_planning_summary>

<unresolved_issues>

1. Reordering semantics (MOVE_ITEM):
    - Server-side reordering deferred. Clarify how position is assigned on ADD (append vs. client-provided position) and
      whether future UPDATE_ITEM may include position changes.

2. Field validation constraints:
    - Confirm max lengths for name (e.g., 255) and unit (e.g., 64) and exact 400 Problem Details contract on overflow or
      invalid scale for quantity.

3. Unauthorized masking scope:
    - Confirm all list-specific endpoints (GET/DELETE/share/unshare/operations) consistently use 404 masking for
      unauthorized access.

4. Problem Details taxonomy:
    - Define stable "type" URIs and "code" fields for common errors (stale-version, not-member, invalid-email,
      validation-failed) to facilitate client handling.
      </unresolved_issues>
      </conversation_summary>
