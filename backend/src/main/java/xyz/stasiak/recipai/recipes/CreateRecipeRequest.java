package xyz.stasiak.recipai.recipes;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateRecipeRequest(@NotBlank String name, @NotNull @Valid RecipeData data, UUID recipesCollectionId) {
}