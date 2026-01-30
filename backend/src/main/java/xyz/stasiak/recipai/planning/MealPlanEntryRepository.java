package xyz.stasiak.recipai.planning;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface MealPlanEntryRepository extends JpaRepository<MealPlanEntry, Long> {

    List<MealPlanEntry> findAllByRecipeId(UUID recipeId);
}
