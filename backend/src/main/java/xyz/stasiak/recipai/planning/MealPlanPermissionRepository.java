package xyz.stasiak.recipai.planning;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

interface MealPlanPermissionRepository extends JpaRepository<MealPlanPermission, MealPlanPermissionId> {
    @Modifying
    @Query("DELETE FROM MealPlanPermission mpp WHERE mpp.id.planId = ?1")
    void deleteAllByPlanId(UUID planId);

    @Query("SELECT COUNT(mpp) FROM MealPlanPermission mpp WHERE mpp.id.email = ?1 AND mpp.role = 'OWNER'")
    long countOwnedByEmail(String email);
}
