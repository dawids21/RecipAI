# Shopping Lists Module — Codebase Structure

```
backend/src/main/java/xyz/stasiak/recipai/
└── shoppinglists/
    ├── ShoppingList.java                            # Shopping list entity
    ├── ShoppingListItem.java                        # Shopping list item entity
    ├── ShoppingListPermission.java                  # Shopping list permission association entity
    ├── ShoppingListPermissionId.java                # Composite key for shopping list permissions
    ├── UserRole.java                                # Enum for OWNER/EDITOR roles
    ├── ShoppingListRepository.java                  # Shopping list data access
    ├── ShoppingListItemRepository.java              # Shopping list item data access
    ├── ShoppingListPermissionRepository.java        # Permission queries repository
    ├── ShoppingListService.java                     # Shopping list business logic with items and permissions
    ├── ShoppingListController.java                  # Shopping list REST endpoints with JWT authentication
    ├── ShoppingListsExceptionHandler.java           # Exception handling with ProblemDetail
    ├── dto/
    │   ├── ShoppingListListDto.java                 # Shopping list list response DTO
    │   ├── ShoppingListDto.java                     # Shopping list detail response DTO with items
    │   ├── ShoppingListItemDto.java                 # Shopping list item response DTO (also the 201/200/412 body)
    │   ├── CreateShoppingListRequest.java
    │   ├── UpdateShoppingListRequest.java
    │   ├── ShareShoppingListRequest.java
    │   ├── UnshareShoppingListRequest.java
    │   ├── SharedUserDto.java
    │   ├── CreateShoppingListItemRequest.java       # Create-item request DTO (no baseVersion — creates never conflict)
    │   └── UpdateShoppingListItemRequest.java       # Update-item request DTO (baseVersion + full mutable item state)
    └── exception/
        ├── ShoppingListNotFoundException.java
        ├── ShoppingListAccessDeniedException.java
        ├── ItemNotFoundException.java               # Item absent from its list -> 404
        └── ItemVersionConflictException.java        # Stale baseVersion -> 412 with the winning item
```
