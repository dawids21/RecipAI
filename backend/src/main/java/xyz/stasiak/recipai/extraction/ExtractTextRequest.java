package xyz.stasiak.recipai.extraction;

import jakarta.validation.constraints.NotBlank;

public record ExtractTextRequest(@NotBlank String text) {
}