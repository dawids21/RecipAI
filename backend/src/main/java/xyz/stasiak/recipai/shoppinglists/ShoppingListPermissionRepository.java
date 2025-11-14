package xyz.stasiak.recipai.shoppinglists;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

interface ShoppingListPermissionRepository extends JpaRepository<ShoppingListPermission, ShoppingListPermissionId> {
    @Modifying
    @Query("DELETE FROM ShoppingListPermission slp WHERE slp.id.shoppingListId = ?1")
    void deleteAllByShoppingListId(UUID shoppingListId);
}