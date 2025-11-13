package xyz.stasiak.recipai.shoppinglists.permissions;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

interface ShoppingListPermissionRepository extends JpaRepository<ShoppingListPermission, ShoppingListPermissionId> {
    @Query("SELECT slp.role FROM ShoppingListPermission slp WHERE slp.id.email = ?1 AND slp.id.shoppingListId = ?2")
    Optional<UserRole> getUserRole(String email, UUID shoppingListId);

    @Modifying
    @Query("DELETE FROM ShoppingListPermission slp WHERE slp.id.shoppingListId = ?1")
    void deleteAllByShoppingListId(UUID shoppingListId);
}