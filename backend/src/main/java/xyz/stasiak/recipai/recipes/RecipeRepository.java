package xyz.stasiak.recipai.recipes;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface RecipeRepository extends JpaRepository<Recipe, UUID> {

    @Query("""
            SELECT DISTINCT r FROM Recipe r
            LEFT JOIN RecipePermission rp ON rp.id.recipeId = r.id
            LEFT JOIN xyz.stasiak.recipai.recipes.collections.RecipesCollectionPermission cp ON cp.id.recipesCollectionId = r.recipesCollectionId
            WHERE rp.id.email = :email
            OR cp.id.email = :email
            """)
    List<Recipe> findAllByUserEmail(@Param("email") String email);

    List<Recipe> findAllByRecipesCollectionId(UUID recipesCollectionId);

    @Query("""
            SELECT r FROM Recipe r
            INNER JOIN RecipePermission rp ON rp.id.recipeId = r.id
            WHERE rp.id.email = :email
            AND r.recipesCollectionId IS NULL
            """)
    List<Recipe> findAllUnassignedByUserEmail(@Param("email") String email);
}