package xyz.stasiak.recipai.planning.dto;

import java.util.List;

public record GeneratedShoppingListResponse(
        List<GeneratedShoppingListItemDto> items,
        List<String> warnings
) {
}
