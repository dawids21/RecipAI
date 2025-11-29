package xyz.stasiak.recipai.recipes.collections.dto;

import xyz.stasiak.recipai.recipes.collections.UserRole;

public record SharedUserDto(String email, UserRole role) {
}
