package xyz.stasiak.recipai.extraction;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record ExtractedRecipe(
        @NotBlank String name,
        @NotNull @NotEmpty @Valid List<ExtractedIngredient> ingredients,
        @NotNull @NotEmpty @Valid List<ExtractedInstruction> instructions,
        @Positive @Max(100) Integer servingSize
) {
}