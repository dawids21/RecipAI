package xyz.stasiak.recipai.extraction;

import jakarta.validation.constraints.NotBlank;

public record ExtractedIngredient(
        @NotBlank String name,
        String quantity,
        String unit
) {
}