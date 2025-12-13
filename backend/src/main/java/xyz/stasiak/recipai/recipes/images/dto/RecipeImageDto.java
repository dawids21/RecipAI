package xyz.stasiak.recipai.recipes.images.dto;

import java.util.UUID;

public record RecipeImageDto(
        UUID id,
        String url,
        String thumbnailUrl
) {
}
