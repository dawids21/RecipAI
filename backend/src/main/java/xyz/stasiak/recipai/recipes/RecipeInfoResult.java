package xyz.stasiak.recipai.recipes;

import java.util.List;

public record RecipeInfoResult(
        List<RecipeInfo> recipes,
        List<String> inaccessibleRecipeNames
) {
}
