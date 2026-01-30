package xyz.stasiak.recipai.planning.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateMealPlanEntryRequest(
        @NotNull LocalDate date,
        UUID recipeId,
        @Size(max = 255) String placeholderText,
        @Positive Integer servingSize
) {
}
