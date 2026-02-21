package xyz.stasiak.recipai.recipes;

import java.util.List;

public record RecipeIngredientsResult(
        List<Ingredient> ingredients,
        List<String> inaccessibleRecipeNames
) {
}
