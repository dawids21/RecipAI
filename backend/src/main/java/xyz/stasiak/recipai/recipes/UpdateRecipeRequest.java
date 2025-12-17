package xyz.stasiak.recipai.recipes;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record UpdateRecipeRequest(
        @NotBlank String name,
        @NotNull @Valid RecipeData data,
        UUID recipesCollectionId,
        @Size(max = 2, message = "Maximum 2 images allowed") List<UUID> images
) {
}