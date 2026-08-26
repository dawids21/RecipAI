package xyz.stasiak.recipai.permissions;

import xyz.stasiak.recipai.permissions.dto.ResourceRole;

import java.util.UUID;

record AcceptedInvite(String resourceType, UUID resourceId, ResourceRole role) {
}
