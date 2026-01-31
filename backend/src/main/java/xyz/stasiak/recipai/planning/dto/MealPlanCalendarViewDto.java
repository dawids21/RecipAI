package xyz.stasiak.recipai.planning.dto;

import java.time.LocalDate;
import java.util.UUID;

public record MealPlanCalendarViewDto(
        Long id,
        UUID planId,
        String planColor,
        LocalDate date,
        UUID recipeId,
        String recipeName,
        String placeholderText,
        Integer servingSize,
        Boolean hasRecipeAccess
) {
}
