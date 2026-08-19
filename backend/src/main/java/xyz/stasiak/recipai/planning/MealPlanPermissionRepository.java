package xyz.stasiak.recipai.planning;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

interface MealPlanPermissionRepository extends JpaRepository<MealPlanPermission, MealPlanPermissionId> {
    @Modifying
    @Query("DELETE FROM MealPlanPermission mpp WHERE mpp.id.planId = ?1")
    void deleteAllByPlanId(UUID planId);

    @Query("SELECT mpp FROM MealPlanPermission mpp WHERE mpp.id.planId = ?1 ORDER BY mpp.role DESC")
    List<MealPlanPermission> findAllByPlanId(UUID planId);
}
