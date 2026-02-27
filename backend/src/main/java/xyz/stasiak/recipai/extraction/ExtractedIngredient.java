package xyz.stasiak.recipai.extraction;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record ExtractedIngredient(
        @NotBlank String name,
        BigDecimal quantity,
        String unit,
        String comment
) {
}