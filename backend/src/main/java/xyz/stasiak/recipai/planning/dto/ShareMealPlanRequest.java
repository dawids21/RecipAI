package xyz.stasiak.recipai.planning.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ShareMealPlanRequest(@NotBlank @Email String email) {
}
