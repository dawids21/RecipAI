package xyz.stasiak.recipai.shoppinglists;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

record AddShoppingListItemRequest(
        @NotBlank @Size(max = 255) String name,
        BigDecimal quantity,
        @Size(max = 64) String unit
) {
}