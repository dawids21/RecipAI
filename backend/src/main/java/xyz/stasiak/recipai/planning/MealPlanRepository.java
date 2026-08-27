package xyz.stasiak.recipai.planning;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface MealPlanRepository extends JpaRepository<MealPlan, UUID> {
    List<MealPlan> findByIdInOrderByCreatedAtAsc(Collection<UUID> ids);
}
