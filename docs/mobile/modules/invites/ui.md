# Invites — UI

## Screen

### Invites Screen (`/invites`, `AppRoute.invites`)

Full-screen list reached from the Main Screen's overflow menu (see `docs/mobile/modules/core/ui.md`). Its own
`AppBar` titled "Invites", painted `inversePrimary`. A `RefreshIndicator` wraps the body; the empty and
error branches are scrollable, so the pull gesture works even with nothing in the list.

- **Loading** — a bare `LoadingWidget`.
- **Error** — `ApiErrorWidget` with a `Retry` button.
- **Empty** — "No pending invites", centred.
- **Data** — one `InviteListItem` per pending invite.

Opening the screen always reloads, independent of the badge's last-known state.

### Invite row (`invite_list_item.dart`)

A `Card` containing a `ListTile` (per-type icon, the invite's `label` as title, `"{type} · Shared by
{invitedBy}"` as subtitle) and a right-aligned `Decline` / `Accept` button pair. The icon is keyed off
`InviteResourceType`: `restaurant_menu` (recipe), `folder` (collection), `shopping_cart` (shopping list),
`calendar_today` (meal plan) — the same icon the resource's own tab uses. `label` is rendered as-is and
never re-fetched (`docs/ADRs/0008-invite-label-snapshot.md`).

Declining shows a confirmation dialog (title "Decline invite", naming the label and sender, `Cancel` /
a destructive `Decline`); accepting has no confirmation. While either call is in flight the row dims and
both buttons disable. The row is removed once the invite is gone — answered, or already gone — and stays
in place on any other failure. A `SnackBar` reports the outcome:

- Success: `Invite accepted` or `Invite declined`.
- The invite was already gone (404): `That invite is no longer available` (accept only — decline treats
  a 404 as the outcome it asked for and says nothing).
- Any other failure: `Failed: $error`, and the row remains answerable.

Accepting reloads the resource's own list so it appears in its tab immediately (see `module.md` →
Cross-module Flows).

## Indicator (in Main Screen)

The overflow menu's icon carries a dot `Badge` whenever any invite is pending; inside the menu, a
permanent "Invites" row shows a mail icon carrying a corner `Badge.count` of the same number while any are
pending, and the bare mail icon otherwise. Both read one shared `InvitesService` notifier — see
`docs/mobile/modules/core/ui.md` for the menu itself.

## Load triggers

When the list loads, and why the indicator does not blink on a reload, are in `module.md` → Services and State.
