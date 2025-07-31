package xyz.stasiak.recipai.recipes;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RecipeData(
        @NotNull @NotEmpty @Valid List<Ingredient> ingredients,
        @NotNull @NotEmpty @Valid List<Instruction> instructions
) {
}