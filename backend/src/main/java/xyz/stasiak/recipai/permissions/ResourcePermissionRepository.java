package xyz.stasiak.recipai.permissions;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.stasiak.recipai.permissions.dto.ResourceRole;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ResourcePermissionRepository extends JpaRepository<ResourcePermission, ResourcePermissionId> {

    List<ResourcePermission> findByIdEmailAndIdResourceType(String email, String resourceType);

    // EDITOR sorts before OWNER alphabetically on the @Enumerated(STRING) column, so DESC puts
    // OWNER first.
    List<ResourcePermission> findByIdResourceTypeAndIdResourceIdOrderByRoleDesc(String resourceType, UUID resourceId);

    Optional<ResourcePermission> findByIdResourceTypeAndIdResourceIdAndRole(String resourceType, UUID resourceId, ResourceRole role);

    void deleteByIdResourceTypeAndIdResourceId(String resourceType, UUID resourceId);
}
