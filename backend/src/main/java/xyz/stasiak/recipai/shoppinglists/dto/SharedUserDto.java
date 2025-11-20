package xyz.stasiak.recipai.shoppinglists.dto;

import xyz.stasiak.recipai.shoppinglists.UserRole;

public record SharedUserDto(String email, UserRole role) {
}