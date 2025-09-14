package xyz.stasiak.recipai.recipes;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

record UnshareRecipeRequest(@NotBlank @Email String email) {
}