# Permissions API

The module's only direct endpoints are the invitee's surface below. Each resource module exposes its
own `share` / `unshare` / `permissions` endpoints on its own paths — see, for example,
`docs/backend/modules/shopping-lists/api.md`.

### GET /invites
- Description: List every pending invite for the caller, across all resource types, newest first
- Authenticated: true
- Example response:
  ```json
  [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "resourceType": "SHOPPING_LIST",
      "label": "Weekly Groceries",
      "invitedBy": "alice@example.com",
      "role": "EDITOR",
      "createdAt": "2026-08-26T10:00:00Z"
    }
  ]
  ```
- Success: 200 OK
- Note: `label` is a snapshot taken when the invite was created and is not refreshed; see
  `docs/ADRs/0008-invite-label-snapshot.md`. There is no `resourceId` — the resource is unreadable
  while the invite is pending, and accept/decline address the invite by its own `id`.

### POST /invites/{id}/accept
- Description: Accept a pending invite — grants the invite's role and destroys the invite
- Authenticated: true
- Success: 204 No Content
- Errors: 404 Not Found (the invite does not exist, or belongs to a different caller — a 403 would
  confirm someone else's invite exists, so both cases are a 404)

### POST /invites/{id}/decline
- Description: Decline a pending invite — destroys it without granting anything
- Authenticated: true
- Success: 204 No Content
- Errors: 404 Not Found (same rule as accept)

## Shared response and request shapes

Every resource module backed by this module uses these types in its `share` / `unshare` /
`permissions` endpoints:

- **`ShareRequest`**: `{"email": "user@example.com", "role": "EDITOR"}` — `role` must be `EDITOR`;
  `OWNER` is rejected with 400 (see **Shared error shape**), since the module has no ownership-transfer
  operation. Mobile clients always send `EDITOR`.
- **`UnshareRequest`**: `{"email": "user@example.com"}`.
- **`PermissionDto`**: `{"email": "user@example.com", "role": "EDITOR", "pending": false}` — a
  granted user or a pending invite, distinguished by `pending`.

## Shared error shape

`ResourceAccessDeniedException` → 403 `Resource Access Denied`; `InviteNotFoundException` → 404
`Invite Not Found`; `InviteRefusedException` → 409 `Invite Refused`, carrying a `reason` property of
`ALREADY_INVITED` or `ALREADY_HAS_ACCESS`; `InvalidInviteRoleException` → 400 `Invalid Invite Role`
(a `ShareRequest.role` the module cannot grant via an invite, i.e. `OWNER`).

```json
{
  "status": 409,
  "title": "Invite Refused",
  "detail": "Invite for SHOPPING_LIST to bob@example.com refused: ALREADY_INVITED",
  "reason": "ALREADY_INVITED"
}
```
