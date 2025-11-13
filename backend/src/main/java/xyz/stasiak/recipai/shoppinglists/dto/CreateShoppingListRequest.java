package xyz.stasiak.recipai.shoppinglists.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateShoppingListRequest(@NotBlank String name) {
}
