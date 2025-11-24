package xyz.stasiak.recipai.recipes;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface RecipePermissionRepository extends JpaRepository<RecipePermission, RecipePermissionId> {
    @Query("SELECT rp.role FROM RecipePermission rp WHERE rp.id.email = ?1 AND rp.id.recipeId = ?2")
    Optional<UserRole> getUserRole(String email, UUID recipeId);

    @Query("SELECT rp FROM RecipePermission rp WHERE rp.id.recipeId = ?1 ORDER BY rp.role DESC")
    List<RecipePermission> findAllByRecipeId(UUID recipeId);

    @Modifying
    @Transactional
    @Query("DELETE FROM RecipePermission rp WHERE rp.id.recipeId = ?1")
    void deleteAllByRecipeId(UUID recipeId);
}
