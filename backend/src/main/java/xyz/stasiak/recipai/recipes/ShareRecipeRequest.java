package xyz.stasiak.recipai.recipes;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

record ShareRecipeRequest(@NotBlank @Email String email) {
}