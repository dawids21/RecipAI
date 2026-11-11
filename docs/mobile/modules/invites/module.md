# Invites Module

The invitee's side of sharing: lists the signed-in user's pending invites and accepts or declines them, feeding both
the `/invites` screen and the Main Screen's pending-invite indicator. The sharer's side is `sharing`.

## Boundary

- **Backend endpoints**: `GET /invites`, `POST /invites/{id}/accept`, `POST /invites/{id}/decline`
  (`docs/backend/modules/permissions/api.md`).
- **Uses**: `auth`; the list services of `recipe` (recipes, collections), `shopping_list` and `planning`, reloaded on
  accept (see Cross-module Flows).
- **Used by**: `core` (Main Screen indicator and menu row, `/invites` route).

## File Map

```
mobile/lib/features/invites/
├── invite_resource_type.dart   # InviteResourceType over the four resource keys, with display names
├── invite.dart                 # Invite model (id, resourceType, label, invitedBy)
├── invites_repository.dart     # /invites endpoints; a 404 on accept/decline is InviteGoneException
├── invites_service.dart        # The session's pending invites (see Services and State)
├── invites_screen.dart         # The /invites screen
├── invite_list_item.dart       # One invite row with Decline/Accept
└── invites_setup.dart
```

```
mobile/test/features/invites/
└── invites_screen_widget_test.dart
```

## Services and State

- **`InvitesService`** — session-scoped lazy singleton (see `docs/mobile/modules/core/module.md` → Services and
  State) holding one `AsyncValue` list of pending invites, which both the screen and the indicator read.
- **Load triggers.** The list loads when the Main Screen first builds, when the app returns to the foreground, when
  the `/invites` screen opens, and on pull-to-refresh. There is no polling, and the indicator's count is the loaded
  list's length.
- **Reloads keep the data.** A reload leaves the current rows in place instead of switching to loading, so the
  indicator does not blink off on every resume; only a failed load falls back to loading, so Retry shows a spinner.
  The indicator reads a loading or failed state as zero, so it never renders over unknown state.
- **Answering.** An answered invite is removed from the list locally, with no reload. A 404 means the invite is
  already gone: it is removed too, and decline treats it as success while accept reports it to the caller.

## Cross-module Flows

### Accept reloads the resource's list

Accepting reloads the resource's own list so it appears in its tab immediately:

| Resource type | Reloaded                                                            |
|---------------|---------------------------------------------------------------------|
| Recipe        | the recipe list                                                     |
| Collection    | the collection list and the recipe list                             |
| Shopping list | the shopping list list                                              |
| Meal plan     | the meal plan list (which drives the calendar via its own listener) |
