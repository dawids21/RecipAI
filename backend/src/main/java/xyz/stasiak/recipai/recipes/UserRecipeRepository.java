package xyz.stasiak.recipai.recipes;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface UserRecipeRepository extends JpaRepository<UserRecipe, UserRecipeId> {
    @Query("SELECT ur.role FROM UserRecipe ur WHERE ur.id.email = ?1 AND ur.id.recipeId = ?2")
    Optional<UserRole> getUserRole(String email, UUID recipeId);

    @Query("SELECT ur FROM UserRecipe ur WHERE ur.id.recipeId = ?1 ORDER BY ur.role DESC")
    List<UserRecipe> findAllByRecipeId(UUID recipeId);

    @Modifying
    @Transactional
    @Query("DELETE FROM UserRecipe ur WHERE ur.id.recipeId = ?1")
    void deleteAllByRecipeId(UUID recipeId);
}