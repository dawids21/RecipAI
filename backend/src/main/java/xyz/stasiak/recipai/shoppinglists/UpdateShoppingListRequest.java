package xyz.stasiak.recipai.shoppinglists;

import jakarta.validation.constraints.NotBlank;

record UpdateShoppingListRequest(@NotBlank String name) {
}
