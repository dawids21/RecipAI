package xyz.stasiak.recipai.shoppinglists;

import java.util.UUID;

class ShoppingListAccessDeniedException extends RuntimeException {
    ShoppingListAccessDeniedException(UUID id) {
        super("Access denied to shopping list with id: " + id);
    }
}
