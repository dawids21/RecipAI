package xyz.stasiak.recipai.shoppinglists;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface ShoppingListRepository extends JpaRepository<ShoppingList, UUID> {
    List<ShoppingList> findByIdInOrderByCreatedAtAsc(Collection<UUID> ids);
}
