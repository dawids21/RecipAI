package xyz.stasiak.recipai.permissions.dto;

public record PermissionDto(String email, ResourceRole role, boolean pending) {
}
