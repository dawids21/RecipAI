package xyz.stasiak.recipai.permissions.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UnshareRequest(@NotBlank @Email String email) {
}
