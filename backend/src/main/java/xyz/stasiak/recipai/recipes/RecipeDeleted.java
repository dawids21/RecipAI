package xyz.stasiak.recipai.recipes;

import java.util.UUID;

public record RecipeDeleted(UUID recipeId, String recipeName) {
}
