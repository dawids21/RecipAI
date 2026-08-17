package xyz.stasiak.recipai.extraction;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record ExtractedRecipe(
        @NotBlank String name,
        @NotNull @NotEmpty List<@Valid ExtractedIngredient> ingredients,
        @NotNull @NotEmpty List<@Valid ExtractedInstruction> instructions,
        @Positive @Max(100) Integer servingSize
) {
}