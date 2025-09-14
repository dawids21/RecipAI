package xyz.stasiak.recipai.recipes;

import java.util.UUID;

public record RecipeDto(UUID id, String name, RecipeData data, UserRole role) {
}