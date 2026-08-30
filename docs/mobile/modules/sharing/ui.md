# Sharing — UI

## Sharing Dialog (`sharing_dialog.dart`)

The generic dialog every resource type (recipes, collections, shopping lists, meal plans) opens to
manage access. It takes a `ValueListenable<AsyncValue<List<ResourcePermission>>>`, the caller's
`currentUserEmail`, and `onShare` / `onUnshare` callbacks — it renders and reports, never holding a
service itself.

An email form (validated, `Share` button) sits above a "Shared with" list. The list renders in the
order the backend sends it — owner, then granted editors, then pending invites by age
(`docs/backend/modules/permissions/api.md`) — and is never sorted, partitioned, or grouped by the
dialog. Each row:

- **Granted row** — the email as title, the role's display name (`Owner` / `Editor`) as subtitle.
- **Pending row** — the email plus a compact "Pending" `Chip` on the title line (the email is
  `Flexible` with ellipsis so a long address does not push the chip off), and `Invited as {role}` as
  subtitle.
- **Remove/cancel icon** — shown on every row except the one whose email is `currentUserEmail`; same
  icon (`remove_circle_outline`) for both kinds, with the tooltip and the confirmation wording
  branching on `pending`:
  - Pending: tooltip "Cancel invitation", confirmation titled "Cancel Invitation" ("Cancel the
    invitation for {email}? They will not be able to accept it any more."), destructive action
    labelled "Confirm".
  - Granted: tooltip "Remove access", confirmation titled "Confirm Unshare" ("Remove access for
    {email}? They will no longer be able to view or edit this item."), destructive action labelled
    "Unshare".

  Both confirmations call the same `onUnshare(email)` — the backend's `unshare` endpoint revokes a
  permission or cancels an invite, whichever exists.

`Loading` shows a spinner; `Error` shows the error text; an empty list shows "Not shared with anyone
yet".

## Sharing and the 409 refusal

A successful share reloads the list, so the new invite appears as a pending row from the server
rather than being inserted locally; the caller shows `Invitation sent to {email}`. A 409 from `share`
is a `ShareRefusedException` (`share_refused_exception.dart`), parsed from the response body's
`reason`:

- `ALREADY_INVITED` → `{email} already has a pending invitation`
- `ALREADY_HAS_ACCESS` → `{email} already has access`

An unrecognised or missing reason falls through to the caller's generic share-failure message.

## Call sites

Four features open `SharingDialog`. `currentUserEmail` comes from `AuthService`, held by the screen
(or `meal_plan_drawer.dart`) that opens the dialog; `permissions` / `shareX` / `unshareX` come from
each feature's own resource service:

| Feature            | Wrapper / call site                                                                  |
|--------------------|--------------------------------------------------------------------------------------|
| Recipe             | `recipe_sharing_dialog.dart`, opened from the recipe detail screen's Share action    |
| Recipes collection | inline in `collection/recipes_collection_list_screen.dart`'s `_showSharingDialog`    |
| Shopping list      | `shopping_list_sharing_dialog.dart`, opened from the detail screen's popup menu      |
| Meal plan          | `meal_plan_sharing_dialog.dart`, opened from `meal_plan_drawer.dart`'s per-plan menu |

The screen that opens the dialog loads the permissions first — `initState` in
`recipe_detail_screen.dart` and `shopping_list_detail_screen.dart`, `_showSharingDialog` in the
collection list screen and in `meal_plan_drawer.dart`. A successful share or unshare reloads them,
from the services' own `share` / `unshare` methods.
