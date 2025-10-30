package xyz.stasiak.recipai.shoppinglists;

import jakarta.validation.constraints.NotBlank;

public record CreateShoppingListRequest(@NotBlank String name) {
}
