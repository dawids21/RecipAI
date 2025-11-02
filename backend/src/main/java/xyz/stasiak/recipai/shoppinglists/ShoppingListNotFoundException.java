package xyz.stasiak.recipai.shoppinglists;

import java.util.UUID;

class ShoppingListNotFoundException extends RuntimeException {

    ShoppingListNotFoundException(UUID id) {
        super("Shopping list not found with id: " + id);
    }
}
