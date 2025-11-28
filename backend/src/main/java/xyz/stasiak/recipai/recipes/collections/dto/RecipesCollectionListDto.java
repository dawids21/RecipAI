package xyz.stasiak.recipai.recipes.collections.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RecipesCollectionListDto(@NotNull UUID id, @NotBlank String name) {
}