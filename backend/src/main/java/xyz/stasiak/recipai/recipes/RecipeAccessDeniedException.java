package xyz.stasiak.recipai.recipes;

import java.util.UUID;

class RecipeAccessDeniedException extends RuntimeException {

    RecipeAccessDeniedException(UUID id) {
        super("Access denied to recipe with id: " + id);
    }
}