package xyz.stasiak.recipai.shoppinglists.exception;

import java.util.UUID;

public class ShoppingListNotFoundException extends RuntimeException {

    public ShoppingListNotFoundException(UUID id) {
        super("Shopping list not found with id: " + id);
    }
}
