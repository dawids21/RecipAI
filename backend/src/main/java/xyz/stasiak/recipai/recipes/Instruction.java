package xyz.stasiak.recipai.recipes;

import jakarta.validation.constraints.NotBlank;

public record Instruction(@NotBlank String step) {
}