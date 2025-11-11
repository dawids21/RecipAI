package xyz.stasiak.recipai.shoppinglists;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

record RemoveShoppingListItemRequest(
        @NotNull UUID id
) {
}
