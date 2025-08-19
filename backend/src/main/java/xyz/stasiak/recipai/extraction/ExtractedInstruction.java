package xyz.stasiak.recipai.extraction;

import jakarta.validation.constraints.NotBlank;

public record ExtractedInstruction(@NotBlank String step) {
}