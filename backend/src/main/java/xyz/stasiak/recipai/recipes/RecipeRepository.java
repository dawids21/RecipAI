package xyz.stasiak.recipai.recipes;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

interface RecipeRepository extends JpaRepository<Recipe, UUID> {

    @Query("""
            SELECT r FROM Recipe r
            WHERE r.id IN :recipeIds
               OR r.recipesCollectionId IN :collectionIds
            ORDER BY r.createdAt
            """)
    List<Recipe> findAllByUserEmail(@Param("recipeIds") Collection<UUID> recipeIds,
                                    @Param("collectionIds") Collection<UUID> collectionIds);

    List<Recipe> findAllByRecipesCollectionIdOrderByCreatedAt(UUID recipesCollectionId);

    @Query("""
            SELECT r.id FROM Recipe r
            WHERE r.id IN :recipeIds
               OR r.recipesCollectionId IN :collectionIds
            """)
    Set<UUID> findAccessibleIds(@Param("recipeIds") Collection<UUID> recipeIds,
                                @Param("collectionIds") Collection<UUID> collectionIds);

    @Query("""
            SELECT r FROM Recipe r
            WHERE r.id IN :recipeIds
            AND (r.recipesCollectionId IS NULL
                 OR r.recipesCollectionId NOT IN :collectionIds)
            ORDER BY r.createdAt
            """)
    List<Recipe> findAllUnassignedByUserEmail(@Param("recipeIds") Collection<UUID> recipeIds,
                                              @Param("collectionIds") Collection<UUID> collectionIds);
}
