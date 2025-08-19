package xyz.stasiak.recipai.recipes;

import java.util.UUID;

class RecipeNotFoundException extends RuntimeException {
   
    RecipeNotFoundException(UUID id) {
        super("Recipe not found with id: " + id);
    }
}