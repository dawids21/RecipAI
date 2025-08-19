package xyz.stasiak.recipai.extraction;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ExtractedRecipe(
        @NotBlank String name,
        String description,
        @NotNull @NotEmpty @Valid List<ExtractedIngredient> ingredients,
        @NotNull @NotEmpty @Valid List<ExtractedInstruction> instructions
) {
}