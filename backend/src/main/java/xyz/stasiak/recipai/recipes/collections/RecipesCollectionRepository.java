package xyz.stasiak.recipai.recipes.collections;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

interface RecipesCollectionRepository extends JpaRepository<RecipesCollection, UUID> {
    @Query("SELECT c FROM RecipesCollection c INNER JOIN RecipesCollectionPermission cp ON cp.id.recipesCollectionId = c.id WHERE cp.id.email = :email ORDER BY c.createdAt")
    List<RecipesCollection> findAllByUserEmail(String email);
}