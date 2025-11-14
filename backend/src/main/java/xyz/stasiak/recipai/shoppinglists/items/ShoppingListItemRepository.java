package xyz.stasiak.recipai.shoppinglists.items;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ShoppingListItemRepository extends JpaRepository<ShoppingListItem, UUID> {
    List<ShoppingListItem> findByShoppingListIdOrderByPositionAsc(UUID shoppingListId);

    @Query("SELECT MAX(i.position) FROM ShoppingListItem i WHERE i.shoppingListId = :shoppingListId")
    Optional<BigDecimal> findMaxPositionByShoppingListId(@Param("shoppingListId") UUID shoppingListId);
}
