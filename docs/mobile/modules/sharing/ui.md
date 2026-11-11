# Sharing — UI

## Sharing Dialog (`sharing_dialog.dart`)

The generic dialog every resource type (recipes, collections, shopping lists, meal plans) opens to manage access.
Where its state lives is in `module.md` → Services and State.

- *Share form*: an email field (validated) and a `Share` button, above a "Shared with" list.
- *Order*: the list keeps the backend's order — owner, then granted editors, then pending invites by age
  (`docs/backend/modules/permissions/api.md`); the dialog never sorts or groups it.
- *Granted row*: the email as title, the role (`Owner` / `Editor`) as subtitle.
- *Pending row*: the email with a "Pending" tag beside it (a long email is truncated so the tag stays visible), and
  `Invited as {role}` as subtitle.
- *Remove/cancel*: every row except the current user's own has the same remove icon; its tooltip and confirmation
  depend on the row kind:
  - Pending: tooltip "Cancel invitation", confirmation titled "Cancel Invitation" ("Cancel the invitation for
    {email}? They will not be able to accept it any more."), destructive action labelled "Confirm".
  - Granted: tooltip "Remove access", confirmation titled "Confirm Unshare" ("Remove access for {email}? They will
    no longer be able to view or edit this item."), destructive action labelled "Unshare".
- *States*: a spinner while loading, the error text on failure, and "Not shared with anyone yet" when empty.

## Sharing and the 409 refusal

After a successful share the new invite appears as a pending row and the caller shows `Invitation sent to {email}`.
A 409 from `share` is reported by its `reason`:

- `ALREADY_INVITED` → `{email} already has a pending invitation`
- `ALREADY_HAS_ACCESS` → `{email} already has access`

An unrecognised or missing reason falls back to the caller's generic share-failure message.

## Call sites

Four places open the dialog; each feature's own service supplies its permissions and share/unshare calls:

| Resource           | Opened from                                                   |
|--------------------|---------------------------------------------------------------|
| Recipe             | Recipe Detail Screen menu → "Share Recipe"                    |
| Recipes collection | Recipes Collection List Screen, a collection's menu → "Share" |
| Shopping list      | Shopping List Detail Screen menu → "Share List"               |
| Meal plan          | Meal Plan Drawer, a plan's menu → "Share"                     |

The permissions are loaded before the dialog is shown, and reloaded after each successful share or unshare.
