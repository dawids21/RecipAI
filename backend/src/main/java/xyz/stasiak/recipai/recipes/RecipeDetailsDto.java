package xyz.stasiak.recipai.recipes;

import xyz.stasiak.recipai.recipes.images.dto.RecipeImageDto;

import java.util.List;
import java.util.UUID;

public record RecipeDetailsDto(
        UUID id,
        String name,
        RecipeData data,
        UserRole role,
        UUID collectionId,
        String collectionName,
        List<RecipeImageDto> images
) {
}
