package xyz.stasiak.recipai.planning.dto;

import java.math.BigDecimal;

public record GeneratedShoppingListItemDto(
        String name,
        BigDecimal quantity,
        String unit
) {
}
