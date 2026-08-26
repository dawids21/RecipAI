# Shopping Lists Module

Manages shopping lists (CRUD) and per-item `baseVersion` optimistic locking on item writes — a body
field on update, a query param on delete — with update covering edits, reorders, and check-state as
one version-gated write. Access control and sharing are owned by the `permissions` module
(`docs/backend/modules/permissions/`); this module asks `PermissionsFacade` for every access decision
and supplies the list's name as an invite's label.

## Codebase Structure

```
backend/src/main/java/xyz/stasiak/recipai/
└── shoppinglists/
    ├── ShoppingList.java                            # Shopping list entity
    ├── ShoppingListItem.java                        # Shopping list item entity
    ├── ShoppingListRepository.java                  # Shopping list data access — findByIdInOrderByCreatedAtAsc backs the accessible-list query
    ├── ShoppingListItemRepository.java              # Shopping list item data access
    ├── ShoppingListService.java                     # Shopping list business logic with items; asks PermissionsFacade for every access decision; owns the SHOPPING_LIST resource key, reserving one unit on create and releasing one on delete, and the SHOPPING_LIST_ITEM key, reserved and released per item against the list but configured from the list's owner (cleared outright when the list is deleted)
    ├── ShoppingListController.java                  # Shopping list REST endpoints with JWT authentication
    ├── ShoppingListsExceptionHandler.java           # Exception handling with ProblemDetail
    ├── dto/
    │   ├── ShoppingListListDto.java                 # Shopping list list response DTO
    │   ├── ShoppingListDto.java                     # Shopping list detail response DTO with items and the caller's ResourceRole
    │   ├── ShoppingListItemDto.java                 # Shopping list item response DTO (also the 201/200/412 body)
    │   ├── CreateShoppingListRequest.java
    │   ├── UpdateShoppingListRequest.java
    │   ├── CreateShoppingListItemRequest.java       # Create-item request DTO (no baseVersion — creates never conflict)
    │   └── UpdateShoppingListItemRequest.java       # Update-item request DTO (baseVersion + full mutable item state)
    └── exception/
        ├── ShoppingListNotFoundException.java
        ├── ItemNotFoundException.java               # Item absent from its list -> 404
        └── ItemVersionConflictException.java        # Stale baseVersion -> 412 with the winning item
```

Sharing types (`ResourceRole`, `PermissionDto`, `ShareRequest`, `UnshareRequest`) and the access-denied
exception live in `permissions` — see `docs/backend/modules/permissions/module.md`.

## Limits

Two independent stock quotas apply here, and a refusal of either does not resolve itself by waiting.

Creating a shopping list consumes one unit of the owner's `SHOPPING_LIST` budget, reserved before
anything is written and keyed by the `email` claim of the JWT; deleting one returns the unit. Only
creation is blocked — reading, editing and sharing keep working while the owner is over the quota,
and sharing never charges the recipient (including a recipient who has merely been invited, but not
yet accepted).

Creating an item consumes one unit of `SHOPPING_LIST_ITEM`, reserved after the permission check and
before the write; deleting an item returns it. Usage is counted against the list, so each list fills
up independently and an editor's add charges the list rather than their own records — but the quota
*value* is resolved from the list's owner, so raising one user's allowance covers every list they own,
present and future. Editing an item consumes nothing. This is also why the per-list quota can't be
read from `GET /limits`: on a shared list the override that matters belongs to someone else, and the
caller reading `GET /shopping-lists/{id}/limits` never learns who the owner is.

See `docs/backend/modules/limits/` for how either quota is configured and changed.
