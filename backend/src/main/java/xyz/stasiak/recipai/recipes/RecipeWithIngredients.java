package xyz.stasiak.recipai.recipes;

import java.util.List;
import java.util.UUID;

public record RecipeWithIngredients(
        UUID recipeId,
        int servingSize,
        List<Ingredient> ingredients
) {
}
