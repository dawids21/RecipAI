package xyz.stasiak.recipai.recipes;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record Ingredient(@NotBlank String name, BigDecimal quantity, String unit, String comment) {
}