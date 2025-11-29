package xyz.stasiak.recipai.recipes.collections.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UnshareRecipesCollectionRequest(@NotBlank @Email String email) {
}
