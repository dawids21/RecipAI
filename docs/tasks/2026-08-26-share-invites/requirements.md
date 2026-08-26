# Share invites

**Date:** 2026-08-26
**Type:** feature

## Summary

Sharing a resource becomes a two-step handshake: entering an email creates a pending invite instead of
granting access immediately, and the invitee must accept before any permission is granted.

## Context

Today all four shareable resource types — recipes, collections, shopping lists and meal plans — share the
same mechanism: `POST /<resource>/{id}/share` takes an email and writes a permission row with
`role = 'EDITOR'` that takes effect instantly. The recipient is never asked and cannot refuse; the resource
simply appears in their lists.

This is being hardened pre-emptively, ahead of a public release of the app. Two problems it solves:

- **Consent** — nobody should be added to a resource they did not agree to join.
- **Clutter** — with a public user base, anyone who knows an email address can push unwanted resources into
  that person's recipe, shopping list and meal plan views.

## Requirements

- Sharing a resource creates a **pending invite** carrying the target email and a role. It grants no access:
  while the invite is pending, the resource does not appear anywhere in the invitee's app and is not
  readable by them.
- The invite carries a **role** field that the backend accepts and stores, so future roles can be invited at
  a different access level. For now the mobile app always sends `EDITOR`, and there is no role picker in the
  UI.
- The invitee can **accept** or **decline** a pending invite.
    - On accept, the permission row is created exactly as sharing creates it today, at the invite's role, and
      the resource behaves for them exactly as a shared resource behaves today.
    - On decline, no permission is created and the invite disappears from both sides.
- The invitee **discovers** their pending invites from inside the app, without any out-of-band message: an
  in-app indicator shows that pending invites are waiting, and leads to where they can be answered.
- The sharer sees pending invites in the resource's shared-users view, **visibly distinct** from users who
  already have access.
- The sharer can **cancel** a pending invite that has not been answered.
- Everything about already-granted access is unchanged: unshare on an accepted user, role semantics,
  collection-derived access, and who is allowed to share.

## Anti-requirements

- **No out-of-band notification.** Discovery is an in-app indicator and nothing else. Push notifications and
  email are a separate task if they turn out to be needed.
- **No expiry.** A pending invite waits indefinitely.
- **No role picker in the mobile app.** The backend accepts a role; the client always sends `EDITOR`.
- **No new roles.** `OWNER` and `EDITOR` remain the only roles.
- **No sender blocking** and **no decline reason** — the invitee cannot block a user from inviting them
  again, and cannot say why they declined.
- **No decline visibility.** The sharer is not told an invite was declined; it simply disappears.
- **No migration of existing permissions.** Permission rows that exist when this ships stay as granted
  access. Nobody is re-asked for consent retroactively.

## Constraints & assumptions

- Permissions are keyed on **email**, not on an account id, and sharing with an address that has never signed
  up already works today. An invite to such an address is therefore accepted and waits until that person
  signs up.
- The backend has **no email or push infrastructure** today. Nothing in this task may depend on adding some.
- Quotas count only `OWNER` permission rows (see `docs/backend/modules/limits/db.md`), so neither a pending
  invite nor an accepted share consumes anyone's `RECIPE`, `RECIPES_COLLECTION`, `SHOPPING_LIST` or
  `MEAL_PLAN` allowance. Invites are assumed to be outside the limits system entirely.
- All four shareable resource types are in scope and should behave identically.

## Acceptance criteria

- [ ] Sharing a recipe with a second user's email grants no access: the recipe is absent from that user's
      recipe list and unreadable by them while the invite is pending.
- [ ] The invited user sees an in-app indicator that invites are pending, and can open it to see and answer
      the invite — without receiving anything outside the app.
- [ ] Accepting the invite grants `EDITOR` access; the recipe then appears in their list and behaves as a
      shared recipe does today.
- [ ] Declining the invite grants nothing, and the invite disappears from both the invitee's and the
      sharer's view.
- [ ] While pending, the sharer sees the invite in the shared-users view, marked as pending and
      distinguishable from users who already have access.
- [ ] The sharer can cancel a pending invite, after which the invitee no longer sees it and the indicator
      clears once no invites remain.
- [ ] The same flow works for collections, shopping lists and meal plans.
- [ ] The share endpoint accepts a role on the invite and the accepted permission is created at that role.

## Edge cases

- **Re-invite after decline** — allowed. The owner may send a fresh invite to someone who declined.
- **Duplicate invite while pending** — refused. An invite cannot be sent to an email that already has a
  pending invite for that resource.
- **Invitee already has access via the collection** — the invite is still sent. Collection-derived access to
  a recipe does not suppress a direct invite to that recipe.
- **Resource deleted while an invite is pending** — the invite is cancelled along with the resource.
- **Invite to an email with no account** — persists until that person signs up, then surfaces to them.
- **Existing permissions at rollout** — untouched; those users keep their access with no invite involved.

## Integration points

Backend (`backend/src/main/java/xyz/stasiak/recipai/`):

- `recipes/` — `POST /recipes/{uuid}/share`, `/unshare`, `GET /recipes/{uuid}/shared_users`;
  `recipe_permission` table.
- `recipes/collections/` — the same three endpoints for collections; `recipes_collection_permission` table.
- `shoppinglists/` — `POST /shopping-lists/{id}/share`, `/unshare`, `GET .../shared_users`;
  `shopping_list_permission` table.
- `planning/` — `POST /meal-plans/{id}/share`, `/unshare`, `GET .../shared_users`; `meal_plan_permissions`
  table.
- Resource deletion paths in all four modules, which must cancel pending invites.

Mobile (`mobile/lib/features/`):

- The per-feature sharing dialogs (e.g. `recipe/.../recipe_sharing_dialog.dart`) and the collection,
  shopping list and meal plan equivalents — they must render pending invites distinctly and offer cancel.
- A new surface where the invitee answers their pending invites, plus the in-app indicator that leads to it
  (its placement in the app shell / bottom navigation is a design decision).

Docs: each module's `api.md` and `db.md`, and the mobile `ui.md` files for the affected features.

## Open questions

- **Sharer loses access while an invite is pending** — if the person who sent the invite is unshared from
  the resource before it is answered, does the invite stand or is it cancelled?
- **Invitee already holds direct permission** — inviting someone who already has an accepted permission row
  on that exact resource: refused, or silently ignored as duplicate shares are today?
