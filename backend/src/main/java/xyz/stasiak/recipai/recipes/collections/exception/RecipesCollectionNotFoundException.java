package xyz.stasiak.recipai.recipes.collections.exception;

import java.util.UUID;

public class RecipesCollectionNotFoundException extends RuntimeException {

    public RecipesCollectionNotFoundException(UUID id) {
        super("Recipes collection not found with id: " + id);
    }
}