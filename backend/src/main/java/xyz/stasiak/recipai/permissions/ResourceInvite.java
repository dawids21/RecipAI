package xyz.stasiak.recipai.permissions;

import jakarta.persistence.*;
import lombok.*;
import xyz.stasiak.recipai.permissions.dto.ResourceRole;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "resource_invite")
@Getter
@Setter
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ResourceInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String resourceType;

    @Column(nullable = false)
    private UUID resourceId;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceRole role;

    @Column(nullable = false)
    private String invitedBy;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    ResourceInvite(String resourceType, UUID resourceId, String email, ResourceRole role, String invitedBy, String label) {
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.email = email;
        this.role = role;
        this.invitedBy = invitedBy;
        this.label = label;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ResourceInvite that = (ResourceInvite) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
