package xyz.stasiak.recipai.permissions.dto;

import java.time.Instant;
import java.util.UUID;

public record PendingInviteDto(UUID id, String resourceType, String label,
                               String invitedBy, ResourceRole role, Instant createdAt) {
}
