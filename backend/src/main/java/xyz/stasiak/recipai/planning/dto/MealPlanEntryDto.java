package xyz.stasiak.recipai.planning.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MealPlanEntryDto(
        Long id,
        UUID planId,
        LocalDate date,
        UUID recipeId,
        String placeholderText,
        Integer servingSize,
        Instant createdAt
) {
}
