package xyz.stasiak.recipai.permissions;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
record ResourcePermissionId(String email, String resourceType, UUID resourceId) implements Serializable {
}
