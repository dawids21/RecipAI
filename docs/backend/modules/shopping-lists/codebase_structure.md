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
    │   ├── ShoppingListItemDto.java                 # Shopping list item response DTO
    │   ├── CreateShoppingListRequest.java
    │   ├── UpdateShoppingListRequest.java
    │   ├── CreateShoppingListItemRequest.java
    │   ├── UpdateShoppingListItemRequest.java
    │   └── MoveShoppingListItemRequest.java
    └── exception/
        ├── ShoppingListNotFoundException.java
        ├── ShoppingListAccessDeniedException.java
        ├── ShoppingListItemNotFoundException.java
        └── ShoppingListItemVersionMismatchException.java
```
