package xyz.stasiak.recipai.shoppinglists;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

interface ShoppingListRepository extends JpaRepository<ShoppingList, UUID> {
    @Query("SELECT sl FROM ShoppingList sl INNER JOIN ShoppingListPermission slp ON slp.id.shoppingListId = sl.id WHERE slp.id.email = :email ORDER BY sl.createdAt")
    List<ShoppingList> findAllByUserEmail(String email);
}
