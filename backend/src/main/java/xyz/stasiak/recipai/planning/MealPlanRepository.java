package xyz.stasiak.recipai.planning;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

interface MealPlanRepository extends JpaRepository<MealPlan, UUID> {
    @Query("SELECT mp FROM MealPlan mp INNER JOIN MealPlanPermission mpp ON mpp.id.planId = mp.id WHERE mpp.id.email = :email ORDER BY mp.createdAt")
    List<MealPlan> findAllByUserEmail(String email);
}
