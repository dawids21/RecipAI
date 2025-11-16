package xyz.stasiak.recipai.shoppinglists.dto;

import jakarta.validation.constraints.Min;

public record MoveShoppingListItemRequest(
        @Min(0) int index
) {
}
