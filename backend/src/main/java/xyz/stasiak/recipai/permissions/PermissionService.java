package xyz.stasiak.recipai.permissions;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xyz.stasiak.recipai.permissions.dto.PermissionDto;
import xyz.stasiak.recipai.permissions.dto.ResourceRole;
import xyz.stasiak.recipai.permissions.exception.ResourceAccessDeniedException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class PermissionService {

    private final ResourcePermissionRepository permissionRepository;

    Optional<ResourceRole> roleOf(String resourceType, UUID resourceId, String email) {
        return permissionRepository.findById(new ResourcePermissionId(email, resourceType, resourceId))
                .map(ResourcePermission::getRole);
    }

    ResourceRole requireEditor(String resourceType, UUID resourceId, String email) {
        // Any granted permission (OWNER or EDITOR) carries editor rights - only its absence refuses.
        return roleOf(resourceType, resourceId, email)
                .orElseThrow(() -> new ResourceAccessDeniedException(resourceType, resourceId));
    }

    ResourceRole requireOwner(String resourceType, UUID resourceId, String email) {
        ResourceRole role = roleOf(resourceType, resourceId, email)
                .orElseThrow(() -> new ResourceAccessDeniedException(resourceType, resourceId));
        if (role != ResourceRole.OWNER) {
            throw new ResourceAccessDeniedException(resourceType, resourceId);
        }
        return role;
    }

    Map<UUID, ResourceRole> accessibleResources(String resourceType, String email) {
        Map<UUID, ResourceRole> access = new HashMap<>();
        for (ResourcePermission permission : permissionRepository.findByIdEmailAndIdResourceType(email, resourceType)) {
            access.put(permission.getId().resourceId(), permission.getRole());
        }
        return access;
    }

    Optional<String> ownerEmail(String resourceType, UUID resourceId) {
        return permissionRepository.findByIdResourceTypeAndIdResourceIdAndRole(resourceType, resourceId, ResourceRole.OWNER)
                .map(permission -> permission.getId().email());
    }

    void grantOwner(String resourceType, UUID resourceId, String email) {
        grant(resourceType, resourceId, email, ResourceRole.OWNER);
    }

    void grant(String resourceType, UUID resourceId, String email, ResourceRole role) {
        permissionRepository.save(new ResourcePermission(new ResourcePermissionId(email, resourceType, resourceId), role));
        log.info("Granted {} on {} {} to {}", role, resourceType, resourceId, email);
    }

    boolean hasPermission(String resourceType, UUID resourceId, String email) {
        return permissionRepository.existsById(new ResourcePermissionId(email, resourceType, resourceId));
    }

    boolean revoke(String resourceType, UUID resourceId, String targetEmail, String requesterEmail) {
        if (targetEmail.equals(requesterEmail)) {
            log.warn("{} cannot unshare themselves from {} {}", requesterEmail, resourceType, resourceId);
            throw new ResourceAccessDeniedException(resourceType, resourceId);
        }

        Optional<ResourcePermission> permission = permissionRepository.findById(
                new ResourcePermissionId(targetEmail, resourceType, resourceId));
        if (permission.isEmpty()) {
            return false;
        }
        if (permission.get().hasOwnerRights()) {
            log.warn("Cannot unshare OWNER {} from {} {}", targetEmail, resourceType, resourceId);
            throw new ResourceAccessDeniedException(resourceType, resourceId);
        }
        permissionRepository.delete(permission.get());
        return true;
    }

    List<PermissionDto> listGranted(String resourceType, UUID resourceId) {
        return permissionRepository.findByIdResourceTypeAndIdResourceIdOrderByRoleDesc(resourceType, resourceId).stream()
                .map(p -> new PermissionDto(p.getId().email(), p.getRole(), false))
                .toList();
    }

    void clear(String resourceType, UUID resourceId) {
        permissionRepository.deleteByIdResourceTypeAndIdResourceId(resourceType, resourceId);
    }
}
