package xyz.stasiak.recipai.planning.dto;

import xyz.stasiak.recipai.planning.UserRole;

public record SharedUserDto(String email, UserRole role) {
}
