package xyz.stasiak.recipai.recipes;

import java.util.List;

public record RecipeIngredientsResult(
        List<RecipeWithIngredients> recipes,
        List<String> inaccessibleRecipeNames
) {
}
