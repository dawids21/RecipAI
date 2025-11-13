package xyz.stasiak.recipai.shoppinglists.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ShoppingListListDto(@NotNull UUID id, @NotBlank String name) {
}
