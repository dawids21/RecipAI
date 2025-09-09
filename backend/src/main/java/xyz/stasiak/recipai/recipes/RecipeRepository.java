package xyz.stasiak.recipai.recipes;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface RecipeRepository extends JpaRepository<Recipe, UUID> {

    @Query("SELECT r FROM Recipe r INNER JOIN UserRecipe ur ON ur.id.recipeId = r.id WHERE ur.id.email = :email")
    List<Recipe> findAllByUserEmail(@Param("email") String email);
}