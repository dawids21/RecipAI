package xyz.stasiak.recipai.recipes.collections;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
record RecipesCollectionPermissionId(String email, UUID recipesCollectionId) implements Serializable {
}