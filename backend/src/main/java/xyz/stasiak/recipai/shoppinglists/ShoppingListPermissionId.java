package xyz.stasiak.recipai.shoppinglists;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
record ShoppingListPermissionId(String email, UUID shoppingListId) implements Serializable {
}
