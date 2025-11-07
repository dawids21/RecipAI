package xyz.stasiak.recipai.shoppinglists;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface ShoppingListItemCheckboxRepository extends JpaRepository<ShoppingListItemCheckbox, UUID> {
}
