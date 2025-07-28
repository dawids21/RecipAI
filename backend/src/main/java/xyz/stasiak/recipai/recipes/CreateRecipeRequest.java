package xyz.stasiak.recipai.recipes;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateRecipeRequest(@NotBlank String name, @NotNull JsonNode data) {
}