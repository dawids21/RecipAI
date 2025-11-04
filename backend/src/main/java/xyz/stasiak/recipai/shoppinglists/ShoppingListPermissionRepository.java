package xyz.stasiak.recipai.shoppinglists;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

interface ShoppingListPermissionRepository extends JpaRepository<ShoppingListPermission, ShoppingListPermissionId> {
    @Query("SELECT slp.role FROM ShoppingListPermission slp WHERE slp.id.email = ?1 AND slp.id.shoppingListId = ?2")
    Optional<UserRole> getUserRole(String email, UUID shoppingListId);
}
