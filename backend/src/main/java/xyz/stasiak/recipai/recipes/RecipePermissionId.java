package xyz.stasiak.recipai.recipes;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
record RecipePermissionId(String email, UUID recipeId) implements Serializable {
}
