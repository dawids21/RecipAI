# Share invites — Tasks

**Date:** 2026-08-26

## Summary

- **T1:** `permissions` module, shopping lists migrated, and the full invite handshake over the API
- **T2:** Recipes migrated onto the module, with collection-derived access composed by `recipes`
- **T3:** Collections and meal plans migrated — migration complete, old tables gone
- **T4:** Invitee-facing mobile surface — indicator, invites list, accept and decline
- **T5:** Sharer-facing mobile surface — pending invites in the sharing dialog, and cancel

T1–T3 are backend; their audience is an API consumer with curl and the `.http` suite. T4–T5 are
mobile and deliver the feature to the end user.

## Cross-task notes

- **One release.** The whole set ships together. From T1 onward the mobile app is degraded against
  the backend: sharing a shopping list stops adding anyone to the "Shared with" list, because the
  invite is pending and pending is not a permission. That gap closes in T5. Nothing between T1 and
  T5 is shippable to production, and the HLD's "all four ship in one release" applies to the whole
  sequence, not just the four modules.
- **The migration mechanism is decided once, in T1** (HLD open question: data migration vs.
  repeatable recompute). T2 and T3 follow whatever T1 establishes — this is the main reason to keep
  them sequential rather than parallel.
- **`limits`' usage recompute moves in three steps.** T1, T2 and T3 each repoint one slice of the
  ownership count at the new store, so the recompute reads from a mix of old and new tables until
  T3 finishes it. Every one of those intermediate states must leave `limit_usage` unchanged for
  existing data — that is the cheapest signal that a migration step was faithful.
- **Two HLD open questions are settled in T1 because they set a precedent T2/T3 copy blindly:** the
  four duplicated `UserRole` / `SharedUserDto` / `Share*Request` types collapse into the module's
  public types, and the `/shared_users` vs `/users` path inconsistency is unified on
  `GET /<resource>/{id}/permissions`. Both change what the mobile client sends and receives; the
  matching mobile repository updates ride in T4 and T5 — do not open a separate task for them.
- **Docs ride with the task that changes the behaviour:** `module.md` / `api.md` / `db.md` for the
  backend modules in T1–T3 (including a new `docs/backend/modules/permissions/` set and the
  `docs/INDEX.md` entry), and the mobile `ui.md` / `codebase_structure.md` files in T4–T5.
- **T4 and T5 can run in parallel** once T3 is merged. They touch disjoint mobile code — T4 adds a
  new feature directory and an app-shell indicator, T5 changes the shared `SharingDialog` and its
  four call sites.

---

## T1: `permissions` module, shopping lists migrated, and the invite handshake

**User-visible outcome**

An API consumer can share a shopping list with a second user and confirm the handshake end to end:
the list stays absent and unreadable for that user until they accept it, and they can find, accept,
decline, or have the invite cancelled out from under them — all over HTTP.

**Scope**

- The new shared `permissions` module in full: granted permissions, pending invites, the role
  predicates, and the facade the resource modules call. Its data model and API shape are the first
  thing this task decides — the HLD deliberately left them open.
- The invite lifecycle end to end — create with a role and a label, accept, decline, cancel — plus
  the cross-resource "what is waiting for this email" query behind the invitee's list and indicator.
  This is written once here and is not re-implemented in T2 or T3.
- The refusal rules from the module's feature area: no second pending invite for the same email and
  resource, no invite to an email that already holds a permission.
- Shopping-list permissions migrated into the module, `ShoppingListService`'s access checks and
  owner-only delete guard replaced by asking the module, and the shopping-list delete path reporting
  the deletion so permissions and pending invites are cleaned up.
- `POST /shopping-lists/{id}/share` creating an invite instead of a permission, accepting a role.
- The shopping-list slice of `limits`' usage recompute repointed at the new store.
- `SecurityConfig` allowlisting the module's new URL prefix — the filter chain ends in `denyAll()`.
- An architecture test holding the domain-free boundary ADR-0007 calls for.

**Out of scope**

- Recipes — covered in T2. Collections and meal plans — covered in T3. Their permission tables and
  access checks are untouched here.
- Dropping the four old permission tables — covered in T3, once nothing reads them.
- Anything on mobile — covered in T4 and T5.

**Depends on:** none

**HLD references**

- `HLD.md` > Feature areas > Shared `permissions` module (new) — the whole area
- `HLD.md` > Feature areas > Quota accounting
- `HLD.md` > Open questions — the migration mechanism, the unknown-resource-type answer, and the
  duplicated-types and path-naming questions all land in this task
- `docs/ADRs/0007-shared-permissions-module.md` — the boundary, and what the module owns
- `docs/ADRs/0008-invite-label-snapshot.md` — the label is supplied by the inviting module and
  stored opaquely

**How to verify**

Against a local `dev`-profile backend (`./recipai.sh start-backend`; `Bearer alice` is
`alice@local.test`):

1. `curl -sS -X POST -H "Authorization: Bearer alice" -H 'Content-Type: application/json' -d '{"email":"bob@local.test","role":"EDITOR"}' localhost:8080/shopping-lists/{id}/share` succeeds.
2. `curl -sS -H "Authorization: Bearer bob" localhost:8080/shopping-lists | jq` does **not** contain
   the list, and fetching it directly as `bob` is refused.
3. `bob` can list the pending invite, seeing the list's label and that `alice` sent it.
4. `bob` accepts; the list now appears in `bob`'s `/shopping-lists` and is readable and editable.
5. Repeat with a second list and have `bob` decline — the list stays invisible and the invite is gone
   from both sides. Repeat again and have `alice` cancel — same.
6. Sharing again to `bob` while an invite is pending is refused; so is inviting an email that already
   accepted.
7. Deleting a list with a pending invite removes the invite from `bob`'s list.
8. Run the repeatable recompute against pre-existing data and confirm `limit_usage` is byte-identical
   to before the migration.

**Risks / unknowns**

- This is the task that carries the migration decision, the module's data model, and the answer for
  an unseen resource type. It is the largest task in the set by a wide margin; if it grows past one
  session, the natural fault line is to land the module plus the shopping-list migration first with
  sharing behaviour unchanged, then add the invite handshake on top — but that first half has no
  user-visible outcome, so treat it as a split of last resort rather than a plan.
- Shopping lists' list query joins the permission table today. How a query in one module reaches
  ownership held by another is a task-design question with no precedent in the codebase — `limits`
  keeps its own tables and never joins across.

---

## T2: Recipes migrated, with collection-derived access composed by `recipes`

**User-visible outcome**

An API consumer sees the same handshake for recipes — sharing grants nothing until accepted — while
collection-derived access keeps working exactly as it does today.

**Scope**

- Recipe permissions migrated into the module, following the mechanism T1 established.
- `validateRecipeAccess` and the recipe access checks replaced by asking the module twice — once for
  the recipe, once for its collection — and composing the two answers in `recipes`, per ADR-0007.
- `POST /recipes/{uuid}/share` creating an invite with a label, accepting a role; the recipe delete
  path reporting the deletion.
- `findAllByUserEmail`, which joins both `recipe_permission` and
  `recipes_collection_permission` today, reworked so the recipe list still shows both directly-shared
  and collection-derived recipes.
- The recipe slice of the usage recompute repointed.

**Out of scope**

- Collections' own permissions and share endpoint — covered in T3. This task consumes collection
  access as it exists; it does not migrate it.
- Materialising collection-derived access as real permission rows — HLD > Out of scope, its own task.
- Any change to who may share or to unshare behaviour.

**Depends on:** T1

**HLD references**

- `HLD.md` > Feature areas > Permission migration and the four resource modules
- `docs/ADRs/0007-shared-permissions-module.md` — specifically the composition boundary and why the
  module must not learn that recipes belong to collections

**How to verify**

1. The T1 verification steps, run against `/recipes/{uuid}/share` instead — invite pending, recipe
   absent from `bob`'s `/recipes` and unreadable, accept makes it appear.
2. `alice` shares a *collection* with `bob` the old way (still a direct permission at this point);
   `bob` can read and edit the recipes in it, exactly as before.
3. `alice` invites `bob` to a recipe he can already reach through that collection: the invite is
   still created, and accepting it leaves his access working.
4. The recompute leaves `limit_usage` unchanged.

**Risks / unknowns**

- This is the module the HLD names as the hard one. Composing two role answers where there used to be
  one fallback lookup is easy to get subtly wrong in the direction of granting less than before —
  worth explicit coverage of a recipe in a shared collection with no direct permission.
- T2 sits in a half-migrated world: recipes read collection access from a store that has not moved
  yet. Whatever T2 uses to ask about a collection has to survive T3 moving it.

---

## T3: Collections and meal plans migrated — migration complete

**User-visible outcome**

An API consumer sees the same handshake for the last two resource types, and every one of the four
now answers "may this caller do this" from a single system of record.

**Scope**

- Collection and meal-plan permissions migrated; both modules' access checks and owner-only delete
  guards replaced by asking the module.
- Both share endpoints creating invites with labels and a role; both delete paths reporting the
  deletion.
- The `RecipesCollectionUnshared` event path preserved — unsharing a collection still detaches that
  user's owned recipes from it.
- The usage recompute finished: ownership read entirely from the new store, no permission table
  counted anywhere.
- The four old `*_permission` tables dropped, and the duplicated `UserRole` / `SharedUserDto` /
  `Share*Request` types removed per the decision T1 made.

**Out of scope**

- Anything on mobile — covered in T4 and T5.
- New roles or new resource types — HLD > Out of scope.

**Depends on:** T2

**HLD references**

- `HLD.md` > Feature areas > Permission migration and the four resource modules
- `HLD.md` > Feature areas > Quota accounting — this is where it lands for good
- `docs/ADRs/0007-shared-permissions-module.md`

**How to verify**

1. The T1 verification steps run against `/collections/{id}/share` and `/meal-plans/{id}/share`.
2. `bob` lists his pending invites and sees invites of all four types in one response, each with its
   label and sender.
3. Unshare a collection from a user who owns recipes in it; those recipes are detached, as before.
4. The recompute produces the same `limit_usage` as before any of T1–T3 ran, now with every old
   permission table gone.
5. `backend/http/` runs top to bottom for all four modules.

**Risks / unknowns**

- Two modules in one task. They are both the simple shape T1 already solved, but if the T1 pattern
  did not generalise as cleanly as expected, this is where it shows — splitting meal plans out is a
  reasonable fallback.
- Dropping the old tables is the irreversible step. It should be the last thing in the task, after
  the recompute has been shown to agree.

---

## T4: Invitee-facing mobile surface

**User-visible outcome**

A user opening the app sees that invites are waiting, can see what each one is and who sent it, and
can accept or decline — with nothing arriving outside the app.

**Scope**

- A new invites feature: repository, service and models over the module's invitee-facing endpoints.
- The in-app indicator in the app shell, and where it lives relative to the three-item bottom
  navigation — an HLD open question with no existing badge pattern in the app to copy. It clears once
  no invites remain.
- The invites list: every pending invite across all four resource types, each showing what it is, its
  stored label, and who sent it.
- Accept and decline per invite; on accept the resource appears in its own list and behaves as a
  shared resource does today.
- Whether the list refreshes on its own schedule or the indicator's — the second HLD open question
  this task settles.
- Any repository path or response-shape catch-up from decisions T1 made, for the endpoints this task
  touches.

**Out of scope**

- The sharing dialog and cancel — covered in T5.
- Any role picker; there are no roles to choose between here.
- Push or email notification — HLD > Out of scope.

**Depends on:** T3

**HLD references**

- `HLD.md` > Feature areas > Invitee-facing surface (mobile)
- `HLD.md` > Open questions — indicator placement, and the list's refresh trigger
- `docs/ADRs/0008-invite-label-snapshot.md` — the list renders a stored label; it does not fetch the
  resource, and the label may be stale

**How to verify**

With a shopping list, a recipe, a collection and a meal plan invited to the signed-in user's address:
opening the app shows the indicator, opening it lists all four with their names and senders, accepting
one makes that resource appear in its own tab and behave normally, declining another removes it, and
answering the last two clears the indicator.

**Risks / unknowns**

- The indicator is a new UI concept in an app shell that has no room reserved for one. Worth
  agreeing on placement before building.

---

## T5: Sharer-facing mobile surface

**User-visible outcome**

A user who shares a recipe, collection, shopping list or meal plan sees the invite sitting pending in
the shared-users list, visibly distinct from people who already have access, and can cancel it.

**Scope**

- The shared `SharingDialog` rendering pending invites alongside granted users and marking them
  distinctly — its `SharedUser` model carries no such distinction today.
- Cancel on a pending entry, after which it disappears for the invitee too.
- All four call sites reaching this through the one shared dialog they already funnel through: the
  three wrapper dialogs and the inline one built in the collections list screen.
- The four services and repositories fetching pending invites alongside granted users.
- The client continues to always send `EDITOR`; no role picker.
- **The shared-users URL in all four mobile repositories, updated for the rename T1 made:**
  `GET /<resource>/{id}/users` — and recipes' `GET /recipes/{uuid}/shared_users` — became
  `GET /<resource>/{id}/permissions`. Every one of the four is a 404 against the migrated backend
  until this lands, so it is not optional polish.
- Any further repository path or response-shape catch-up from decisions T1 made, for the endpoints
  this task touches.

**Out of scope**

- The invitee's list and indicator — covered in T4.
- Gating who may share; unchanged from today, where editors may share onward.
- Telling the sharer that an invite was declined — HLD > Out of scope.

**Depends on:** T3

**HLD references**

- `HLD.md` > Feature areas > Sharer-facing surface (mobile)

**How to verify**

For each of the four resource types: share with a second address, reopen the sharing dialog and see
the entry present and marked pending; cancel it and watch it disappear from the dialog and from the
other account's invites list. With T4 in place, accept from the other account and see the entry in the
dialog change from pending to a granted user.
