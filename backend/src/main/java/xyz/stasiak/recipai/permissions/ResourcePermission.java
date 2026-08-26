package xyz.stasiak.recipai.permissions;

import jakarta.persistence.*;
import lombok.*;
import xyz.stasiak.recipai.permissions.dto.ResourceRole;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "resource_permission")
@Getter
@Setter
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ResourcePermission {

    @EmbeddedId
    private ResourcePermissionId id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceRole role;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    ResourcePermission(ResourcePermissionId id, ResourceRole role) {
        this.id = id;
        this.role = role;
    }

    boolean hasOwnerRights() {
        return role == ResourceRole.OWNER;
    }

    boolean hasEditorRights() {
        return hasOwnerRights() || role == ResourceRole.EDITOR;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ResourcePermission that = (ResourcePermission) o;
        return Objects.equals(id, that.id) && role == that.role;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, role);
    }
}
