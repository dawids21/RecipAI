package xyz.stasiak.recipai.planning.dto;

import xyz.stasiak.recipai.planning.UserRole;

import java.time.Instant;
import java.util.UUID;

public record MealPlanDto(
        UUID id,
        String name,
        String color,
        UserRole role,
        Instant createdAt
) {
}
