package xyz.stasiak.recipai.shoppinglists.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateShoppingListItemRequest(
        @NotBlank @Size(max = 255) String name,
        BigDecimal quantity,
        @Size(max = 64) String unit,
        @Min(0) Integer index
) {
}