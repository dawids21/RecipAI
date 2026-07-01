package xyz.stasiak.recipai.shoppinglists.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateShoppingListItemRequest(
        @NotNull Long baseVersion,
        @NotBlank @Size(max = 255) String name,
        @PositiveOrZero BigDecimal quantity,
        @Size(max = 64) String unit,
        @NotNull Boolean checked,
        @NotNull BigDecimal position
) {
}
