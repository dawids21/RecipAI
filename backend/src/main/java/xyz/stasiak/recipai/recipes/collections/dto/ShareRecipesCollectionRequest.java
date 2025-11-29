package xyz.stasiak.recipai.recipes.collections.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ShareRecipesCollectionRequest(@NotBlank @Email String email) {
}
