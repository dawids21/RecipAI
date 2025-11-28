package xyz.stasiak.recipai.recipes.collections.exception;

import java.util.UUID;

public class RecipesCollectionAccessDeniedException extends RuntimeException {
    public RecipesCollectionAccessDeniedException(UUID id) {
        super("Access denied to recipes collection with id: " + id);
    }
}