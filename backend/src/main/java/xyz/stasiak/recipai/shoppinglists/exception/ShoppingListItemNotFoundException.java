package xyz.stasiak.recipai.shoppinglists.exception;

import java.util.UUID;

public class ShoppingListItemNotFoundException extends RuntimeException {

    public ShoppingListItemNotFoundException(UUID id) {
        super("Shopping list item not found with id: " + id);
    }
}
