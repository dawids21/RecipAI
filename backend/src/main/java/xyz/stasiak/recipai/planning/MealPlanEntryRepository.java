package xyz.stasiak.recipai.planning;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

interface MealPlanEntryRepository extends JpaRepository<MealPlanEntry, Long> {

    List<MealPlanEntry> findAllByRecipeId(UUID recipeId);

    @Query("""
            SELECT
                e.id AS id,
                e.planId AS planId,
                mp.color AS planColor,
                e.date AS date,
                e.recipeId AS recipeId,
                r.name AS recipeName,
                e.placeholderText AS placeholderText,
                e.servingSize AS servingSize,
                CASE
                    WHEN e.recipeId IS NULL THEN true
                    WHEN EXISTS (
                        SELECT 1 FROM RecipePermission rp
                        WHERE rp.id.recipeId = e.recipeId
                        AND rp.id.email = :email
                    ) THEN true
                    WHEN r.recipesCollectionId IS NOT NULL AND EXISTS (
                        SELECT 1 FROM xyz.stasiak.recipai.recipes.collections.RecipesCollectionPermission rcp
                        WHERE rcp.id.recipesCollectionId = r.recipesCollectionId
                        AND rcp.id.email = :email
                    ) THEN true
                    ELSE false
                END AS hasRecipeAccess
            FROM MealPlanEntry e
            INNER JOIN MealPlan mp ON mp.id = e.planId
            INNER JOIN MealPlanPermission mpp ON mpp.id.planId = e.planId
            LEFT JOIN Recipe r ON r.id = e.recipeId
            WHERE mpp.id.email = :email
            AND e.date BETWEEN :startDate AND :endDate
            AND e.planId IN :planIds
            ORDER BY e.date, e.createdAt
            """)
    List<MealPlanCalendarEntryProjection> findCalendarEntries(
            @Param("email") String email,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("planIds") List<UUID> planIds
    );
}
