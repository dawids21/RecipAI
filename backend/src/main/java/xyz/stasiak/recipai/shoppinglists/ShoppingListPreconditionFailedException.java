package xyz.stasiak.recipai.shoppinglists;

import java.util.UUID;

class ShoppingListPreconditionFailedException extends RuntimeException {

    ShoppingListPreconditionFailedException(UUID id) {
        super(String.format("Shopping list with id %s has been modified", id));
    }

    ShoppingListPreconditionFailedException(UUID id, Throwable cause) {
        super(String.format("Shopping list with id %s has been modified", id), cause);
    }
}
