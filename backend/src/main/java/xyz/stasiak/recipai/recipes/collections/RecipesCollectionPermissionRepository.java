package xyz.stasiak.recipai.recipes.collections;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

interface RecipesCollectionPermissionRepository extends JpaRepository<RecipesCollectionPermission, RecipesCollectionPermissionId> {
    @Modifying
    @Query("DELETE FROM RecipesCollectionPermission cp WHERE cp.id.recipesCollectionId = ?1")
    void deleteAllByRecipesCollectionId(UUID recipesCollectionId);
}