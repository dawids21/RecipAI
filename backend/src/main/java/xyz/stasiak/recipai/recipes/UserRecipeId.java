package xyz.stasiak.recipai.recipes;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
record UserRecipeId(String email, UUID recipeId) implements Serializable {
}