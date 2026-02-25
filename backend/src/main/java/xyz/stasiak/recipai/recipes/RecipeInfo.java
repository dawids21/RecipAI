package xyz.stasiak.recipai.recipes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RecipeInfo(
        UUID id,
        String name,
        UUID recipesCollectionId,
        Instant createdAt,
        List<Ingredient> ingredients,
        List<Instruction> instructions,
        String sourceUrl,
        int servingSize
) {
}
