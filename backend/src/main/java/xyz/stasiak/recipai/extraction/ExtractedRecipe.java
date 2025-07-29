package xyz.stasiak.recipai.extraction;

import java.util.List;

public record ExtractedRecipe(String name, String description, List<ExtractedIngredient> ingredients, List<ExtractedStep> steps) {
}