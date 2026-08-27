package xyz.stasiak.recipai.planning.dto;

import xyz.stasiak.recipai.permissions.dto.ResourceRole;

import java.time.Instant;
import java.util.UUID;

public record MealPlanDto(
        UUID id,
        String name,
        String color,
        ResourceRole role,
        Instant createdAt
) {
}
