package xyz.stasiak.recipai.shoppinglists;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ShoppingListRepository extends JpaRepository<ShoppingList, UUID> {
    @Query("SELECT sl FROM ShoppingList sl INNER JOIN ShoppingListPermission slp ON slp.id.shoppingListId = sl.id WHERE slp.id.email = :email")
    List<ShoppingList> findAllByUserEmail(String email);

    @Query("SELECT sl FROM ShoppingList sl LEFT JOIN FETCH sl.items WHERE sl.id = :id")
    Optional<ShoppingList> findByIdWithItems(UUID id);

    @Query(value = """
            SELECT
                sl.id as list_id,
                sl.name as list_name,
                sl.version as list_version,
                sli.id as item_id,
                sli.name as item_name,
                sli.quantity as item_quantity,
                sli.unit as item_unit,
                sli.position as item_position,
                COALESCE(slic.checked, false) as item_checked
            FROM recipai.shopping_lists sl
            LEFT JOIN recipai.shopping_list_items sli ON sli.list_id = sl.id
            LEFT JOIN recipai.shopping_list_item_checkbox slic ON slic.shopping_list_item_id = sli.id
            WHERE sl.id = :id
            ORDER BY sli.position ASC
            """, nativeQuery = true)
    List<ShoppingListView> findShoppingListViewWithItemsById(UUID id);
}
