package xyz.stasiak.recipai.recipes;

import jakarta.validation.constraints.NotBlank;

public record Ingredient(@NotBlank String name, String quantity, String unit) {
}