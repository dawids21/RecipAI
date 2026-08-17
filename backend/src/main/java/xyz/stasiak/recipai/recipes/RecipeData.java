package xyz.stasiak.recipai.recipes;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.URL;

import java.util.List;

public record RecipeData(
        @NotNull @NotEmpty List<@Valid Ingredient> ingredients,
        @NotNull @NotEmpty List<@Valid Instruction> instructions,
        @URL String sourceUrl,
        @Positive @Max(100) Integer servingSize
) {
}