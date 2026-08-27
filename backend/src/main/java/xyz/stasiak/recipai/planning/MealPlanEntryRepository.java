package xyz.stasiak.recipai.planning;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface MealPlanEntryRepository extends JpaRepository<MealPlanEntry, Long> {

    List<MealPlanEntry> findAllByRecipeId(UUID recipeId);

    @Query("""
            SELECT e FROM MealPlanEntry e
            WHERE e.planId IN :planIds
            AND e.date IN :dates
            AND e.recipeId IS NOT NULL
            """)
    List<MealPlanEntry> findEntriesWithRecipes(
            @Param("planIds") List<UUID> planIds,
            @Param("dates") List<LocalDate> dates
    );

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
                    WHEN e.recipeId IN :recipeIds THEN true
                    ELSE false
                END AS hasRecipeAccess
            FROM MealPlanEntry e
            INNER JOIN MealPlan mp ON mp.id = e.planId
            LEFT JOIN Recipe r ON r.id = e.recipeId
            WHERE e.date BETWEEN :startDate AND :endDate
            AND e.planId IN :planIds
            ORDER BY e.date, e.createdAt
            """)
    List<MealPlanCalendarEntryProjection> findCalendarEntries(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("planIds") Collection<UUID> planIds,
            @Param("recipeIds") Collection<UUID> recipeIds
    );
}
