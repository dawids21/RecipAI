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
            ORDER BY r.createdAt
            """)
    List<Recipe> findAllByUserEmail(@Param("email") String email);

    List<Recipe> findAllByRecipesCollectionIdOrderByCreatedAt(UUID recipesCollectionId);

    @Query("""
            SELECT r FROM Recipe r
            INNER JOIN RecipePermission rp ON rp.id.recipeId = r.id
            WHERE rp.id.email = :email
            AND (r.recipesCollectionId IS NULL
                 OR NOT EXISTS (SELECT 1 FROM RecipesCollectionPermission rcp
                               WHERE rcp.id.recipesCollectionId = r.recipesCollectionId
                               AND rcp.id.email = :email))
            ORDER BY r.createdAt
            """)
    List<Recipe> findAllUnassignedByUserEmail(@Param("email") String email);
}