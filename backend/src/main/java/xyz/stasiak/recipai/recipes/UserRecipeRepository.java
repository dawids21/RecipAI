package xyz.stasiak.recipai.recipes;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface UserRecipeRepository extends JpaRepository<UserRecipe, UserRecipeId> {
    boolean existsByIdEmailAndIdRecipeId(String email, UUID recipeId);

    default boolean doesNotExistByIdEmailAndIdRecipeId(String email, UUID recipeId) {
        return !existsByIdEmailAndIdRecipeId(email, recipeId);
    }
}