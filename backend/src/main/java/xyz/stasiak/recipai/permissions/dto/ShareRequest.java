package xyz.stasiak.recipai.permissions.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ShareRequest(@NotBlank @Email String email, @NotNull ResourceRole role) {
}
