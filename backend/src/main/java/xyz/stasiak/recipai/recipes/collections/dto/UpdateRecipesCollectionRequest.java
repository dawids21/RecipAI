package xyz.stasiak.recipai.recipes.collections.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateRecipesCollectionRequest(@NotBlank String name) {
}