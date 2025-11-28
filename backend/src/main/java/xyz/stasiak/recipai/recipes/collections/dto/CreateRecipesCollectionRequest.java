package xyz.stasiak.recipai.recipes.collections.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateRecipesCollectionRequest(@NotBlank String name) {
}