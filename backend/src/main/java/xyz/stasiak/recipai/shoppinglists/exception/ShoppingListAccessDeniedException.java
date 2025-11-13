package xyz.stasiak.recipai.shoppinglists.exception;

import java.util.UUID;

public class ShoppingListAccessDeniedException extends RuntimeException {
    public ShoppingListAccessDeniedException(UUID id) {
        super("Access denied to shopping list with id: " + id);
    }
}
