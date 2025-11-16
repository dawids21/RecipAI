package xyz.stasiak.recipai.shoppinglists;

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

    @Query(value = """
            SELECT *
            FROM recipai.shopping_list_items
            WHERE shopping_list_id = :shoppingListId
            ORDER BY position
            LIMIT 2
            OFFSET :offset
            """, nativeQuery = true)
    List<ShoppingListItem> findByShoppingListIdWithLimitOffset(
            @Param("shoppingListId") UUID shoppingListId,
            @Param("offset") int offset
    );

    @Query(value = """
            SELECT row_number - 1 as item_index
            FROM (
                SELECT ROW_NUMBER() OVER (ORDER BY position) as row_number, id
                FROM recipai.shopping_list_items
                WHERE shopping_list_id = :shoppingListId
                ) AS ranked
            WHERE id = :itemId
            """, nativeQuery = true)
    int findItemIndexInList(
            @Param("shoppingListId") UUID shoppingListId,
            @Param("itemId") UUID itemId
    );
}
