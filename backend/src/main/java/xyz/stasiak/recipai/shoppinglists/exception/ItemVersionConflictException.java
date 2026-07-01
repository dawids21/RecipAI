package xyz.stasiak.recipai.shoppinglists.exception;

import xyz.stasiak.recipai.shoppinglists.dto.ShoppingListItemDto;

public class ItemVersionConflictException extends RuntimeException {

    private final ShoppingListItemDto winningItem;

    public ItemVersionConflictException(ShoppingListItemDto winningItem) {
        super("Item version conflict, current version is: " + winningItem.version());
        this.winningItem = winningItem;
    }

    public ShoppingListItemDto winningItem() {
        return winningItem;
    }
}
