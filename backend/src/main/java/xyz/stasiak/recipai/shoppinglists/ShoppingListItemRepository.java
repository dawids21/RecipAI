package xyz.stasiak.recipai.shoppinglists;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ShoppingListItemRepository extends JpaRepository<ShoppingListItem, UUID> {
    List<ShoppingListItem> findByShoppingListIdOrderByPositionAscIdAsc(UUID shoppingListId);

    Optional<ShoppingListItem> findByIdAndShoppingListId(UUID id, UUID shoppingListId);
}
