package xyz.stasiak.recipai.permissions;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xyz.stasiak.recipai.permissions.dto.PermissionDto;
import xyz.stasiak.recipai.permissions.dto.ResourceRole;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionsFacade {

    private final PermissionService permissionService;
    private final PermissionsApplicationService applicationService;

    public ResourceRole requireEditor(String resourceType, UUID resourceId, String email) {
        log.debug("Requiring editor rights on {} {} for {}", resourceType, resourceId, email);
        return permissionService.requireEditor(resourceType, resourceId, email);
    }

    public ResourceRole requireOwner(String resourceType, UUID resourceId, String email) {
        log.debug("Requiring owner rights on {} {} for {}", resourceType, resourceId, email);
        return permissionService.requireOwner(resourceType, resourceId, email);
    }

    public Optional<ResourceRole> roleOf(String resourceType, UUID resourceId, String email) {
        log.debug("Getting role on {} {} for {}", resourceType, resourceId, email);
        return permissionService.roleOf(resourceType, resourceId, email);
    }

    public Map<UUID, ResourceRole> accessibleResources(String resourceType, String email) {
        log.debug("Getting accessible {} resources for {}", resourceType, email);
        return permissionService.accessibleResources(resourceType, email);
    }

    public Optional<String> ownerEmail(String resourceType, UUID resourceId) {
        log.debug("Getting owner email for {} {}", resourceType, resourceId);
        return permissionService.ownerEmail(resourceType, resourceId);
    }

    public void grantOwner(String resourceType, UUID resourceId, String email) {
        log.debug("Granting owner on {} {} to {}", resourceType, resourceId, email);
        permissionService.grantOwner(resourceType, resourceId, email);
    }

    public UUID invite(String resourceType, UUID resourceId, String targetEmail, ResourceRole role, String label, String invitedByEmail) {
        log.debug("Inviting {} to {} {} with role {} from {}", targetEmail, resourceType, resourceId, role, invitedByEmail);
        return applicationService.invite(resourceType, resourceId, targetEmail, role, label, invitedByEmail);
    }

    public void revoke(String resourceType, UUID resourceId, String targetEmail, String requesterEmail) {
        log.debug("Revoking access to {} {} from {} by {}", resourceType, resourceId, targetEmail, requesterEmail);
        applicationService.revoke(resourceType, resourceId, targetEmail, requesterEmail);
    }

    public List<PermissionDto> getPermissions(String resourceType, UUID resourceId) {
        log.debug("Getting permissions for {} {}", resourceType, resourceId);
        return applicationService.getPermissions(resourceType, resourceId);
    }

    public void resourceDeleted(String resourceType, UUID resourceId) {
        log.debug("Reporting deletion of {} {}", resourceType, resourceId);
        applicationService.resourceDeleted(resourceType, resourceId);
    }
}
