package xyz.stasiak.recipai.shoppinglists;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

record ShoppingListItemOperationResponse(
        @NotNull UUID listId,
        @NotBlank String listName,
        @NotNull Long listVersion,
        @NotNull UUID itemId
) {
}
