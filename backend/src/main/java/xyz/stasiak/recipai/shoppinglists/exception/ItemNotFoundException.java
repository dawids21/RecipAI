package xyz.stasiak.recipai.shoppinglists.exception;

import java.util.UUID;

public class ItemNotFoundException extends RuntimeException {

    public ItemNotFoundException(UUID id) {
        super("Shopping list item not found with id: " + id);
    }
}
