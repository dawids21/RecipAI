package xyz.stasiak.recipai.shoppinglists;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ShoppingListDto(
        @NotNull UUID id,
        @NotBlank String name,
        @NotNull List<ShoppingListItemDto> items,
        @NotNull UserRole role
) {
}