package xyz.stasiak.recipai.recipes;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateRecipeRequest(@NotBlank String name, @NotNull @Valid RecipeData data) {
}