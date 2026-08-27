package xyz.stasiak.recipai.recipes.collections;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface RecipesCollectionRepository extends JpaRepository<RecipesCollection, UUID> {
    List<RecipesCollection> findByIdInOrderByCreatedAtAsc(Collection<UUID> ids);
}
