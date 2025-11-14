package xyz.stasiak.recipai.shoppinglists.exception;

import java.util.UUID;

public class ShoppingListItemVersionMismatchException extends RuntimeException {
    public ShoppingListItemVersionMismatchException(UUID itemId, Long expectedVersion, Long actualVersion) {
        super(String.format("Version mismatch for shopping list item with id: %s. Expected version: %d, actual version: %d",
                itemId, expectedVersion, actualVersion));
    }
}