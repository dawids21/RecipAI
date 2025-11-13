package xyz.stasiak.recipai.shoppinglists.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateShoppingListRequest(@NotBlank String name) {
}
