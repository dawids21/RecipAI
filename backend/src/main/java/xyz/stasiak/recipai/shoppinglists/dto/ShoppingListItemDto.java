package xyz.stasiak.recipai.shoppinglists.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record ShoppingListItemDto(
        @NotNull UUID id,
        @NotBlank @Size(max = 255) String name,
        BigDecimal quantity,
        @Size(max = 64) String unit,
        @NotNull Boolean checked,
        @NotNull Integer position,
        @NotNull Long version
) {
}