package xyz.stasiak.recipai.shoppinglists;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
interface ShoppingListItemRepository extends JpaRepository<ShoppingListItem, UUID> {

}
