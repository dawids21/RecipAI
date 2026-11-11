# Invites — UI

## Screen

### Invites Screen (`/invites`)

Full-screen list titled "Invites", reached from the Main Screen's overflow menu
(`docs/mobile/modules/core/ui.md`). Pull-to-refresh works in every state, including empty and error. When it loads
is in `module.md` → Services and State.

- **Loading** — a spinner.
- **Error** — the error display with a `Retry` button.
- **Empty** — "No pending invites", centred.
- **Data** — one invite row per pending invite.

### Invite row (`invite_list_item.dart`)

- *Content*: an icon for the resource type (the same one the app uses for that resource elsewhere), the invite's
  label as title, `"{type} · Shared by {invitedBy}"` as subtitle, and `Decline` / `Accept` buttons. The label is
  what the resource was called when the invite was sent (`docs/ADRs/0008-invite-label-snapshot.md`).
- *Answering*: declining asks first (title "Decline invite", naming the label and sender, `Cancel` / a destructive
  `Decline`); accepting does not. While the call is in flight the row dims and both buttons disable. The row
  disappears once the invite is answered or already gone, and stays on any other failure.
- *Outcome snackbar*:
  - Success: `Invite accepted` or `Invite declined`.
  - Already gone, on accept: `That invite is no longer available`. Decline says nothing, since the invite is gone
    either way (see `module.md` → Services and State).
  - Any other failure: `Failed: $error`, and the row stays answerable.

An accepted resource appears in its own tab immediately (see `module.md` → Cross-module Flows).

## Indicator (in Main Screen)

While any invite is pending, the overflow menu's icon carries a dot, and the menu's "Invites" row shows a mail icon
with the pending count. With none pending, both show no badge. Both count the same loaded list (see `module.md` →
Services and State).
