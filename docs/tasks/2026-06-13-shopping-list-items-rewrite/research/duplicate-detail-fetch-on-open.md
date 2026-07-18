# Duplicate `GET /shopping-lists/{id}` on Open — Research

When the detail screen opens, the app fetches `GET /shopping-lists/{listId}`
**twice**. This document analyses both calls, what each is used for, why the
second is redundant, and how it can be removed. It is analysis, not an
implementation plan.

## What happens on open

`ShoppingListDetailScreen.initState` calls `loadSharedUsers(...)` (the `/users`
endpoint — unrelated) and `openShoppingList(listId)`. Inside
`openShoppingList` (`shopping_list_detail_service.dart`) two calls hit the same
endpoint, `GET /shopping-lists/{listId}`, which returns `{id, name, role,
items[]}`:

| # | Call chain | Awaited | Uses from response |
|---|-----------|---------|--------------------|
| 1 | `_syncService.startPolling` → immediate `_poll` → `_itemRepository.fetchServerItems` | no (fire-and-forget) | **`items[]` only** |
| 2 | `await loadShoppingListDetail` → `_shoppingListRepository.fetchShoppingListDetail` | yes | **`id`, `name`, `role`** |

Both parse the identical body; each discards the half the other consumes:

- The **poll** reconciles `items[]` into the local store, driving
  `service.items` (the rendered item list). It ignores `name`/`role`.
- The **detail call** builds `ShoppingListDetail` into `_shoppingListDetail`.
  Its `items` field is **never rendered** — the screen renders `service.items`,
  not `detail.items`. So the detail call's item payload is pure waste.

## What the second call (`loadShoppingListDetail`) is used for

Only list-level metadata plus UI gating:

1. **Screen gating** — `asyncValueDetail.when(loading/error/data)` wraps the
   entire scaffold. Until `_shoppingListDetail` resolves, the screen shows a
   spinner; on failure, a full-screen `ApiErrorWidget`.
2. **`detail.name`** — the header title.
3. **`detail.role`** — gates the owner-only "Delete List" menu item. This is the
   **only** value not otherwise available locally: the `ShoppingList` model from
   the list endpoint carries `id` + `name` but **no role**.

`detail.id` is also passed to rename/delete/share handlers, but it always equals
`widget.shoppingListId`, so it is not a real dependency.

## Side effect: the second call breaks offline-first

Because the screen gates on `_shoppingListDetail`, a cold start **while
offline** makes `fetchShoppingListDetail` throw → the whole screen shows an
error, even though the items are in the local DB and the poll path is explicitly
built to tolerate offline (`fetchServerItems` throws → `_poll` sets
`SyncStatus.offline` and leaves the store untouched). Removing this call and
gating on locally-available data fixes that inconsistency, not just the extra
request.

## Removal approach

Fold the metadata into the single pull so one `GET` populates both notifiers:

- Change the pull path (`fetchServerItems`) to return the full parsed detail
  (name + role + items), or add a sibling that does.
- In the poll's success path, also update `_shoppingListDetail` from that same
  response.
- Drop `loadShoppingListDetail(listId)` from `openShoppingList` and re-gate the
  screen on `service.items` (with name/role from the store) so it stays usable
  offline.

### Open decision: where do `name`/`role` come from offline?

On a cold offline start there is no live response to source metadata from:

- **(A) Live pull only** — simple, but on a cold offline start the name/role are
  unknown until the device has been online once (role especially, which gates
  the delete action).
- **(B) Persist `name`/`role` locally** — e.g. alongside the items table — so
  they survive offline restarts the same way items do. Fully offline-capable at
  the cost of a small schema/store addition.
