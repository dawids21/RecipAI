package xyz.stasiak.recipai.planning;

import org.springframework.data.jpa.repository.JpaRepository;

interface MealPlanEntryRepository extends JpaRepository<MealPlanEntry, Long> {
}
