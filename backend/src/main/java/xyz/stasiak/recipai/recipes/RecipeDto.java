package xyz.stasiak.recipai.recipes;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record RecipeDto(UUID id, String name, JsonNode data) {
}