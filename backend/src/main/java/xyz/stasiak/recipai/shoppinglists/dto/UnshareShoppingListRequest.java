package xyz.stasiak.recipai.shoppinglists.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UnshareShoppingListRequest(@NotBlank @Email String email) {
}