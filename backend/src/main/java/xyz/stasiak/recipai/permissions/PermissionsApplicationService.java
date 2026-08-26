package xyz.stasiak.recipai.permissions;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.stasiak.recipai.permissions.dto.PermissionDto;
import xyz.stasiak.recipai.permissions.dto.ResourceRole;
import xyz.stasiak.recipai.permissions.exception.InviteRefusedException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
class PermissionsApplicationService {

    private final PermissionService permissionService;
    private final InviteService inviteService;

    @Transactional
    UUID invite(String resourceType, UUID resourceId, String targetEmail, ResourceRole role, String label, String invitedByEmail) {
        if (permissionService.hasPermission(resourceType, resourceId, targetEmail)) {
            log.warn("{} already has access to {} {} - refusing invite", targetEmail, resourceType, resourceId);
            throw new InviteRefusedException(resourceType, targetEmail, InviteRefusedException.Reason.ALREADY_HAS_ACCESS);
        }
        return inviteService.create(resourceType, resourceId, targetEmail, role, label, invitedByEmail);
    }

    @Transactional
    void acceptInvite(UUID inviteId, String callerEmail) {
        AcceptedInvite accepted = inviteService.accept(inviteId, callerEmail);
        // A permission can appear between invite and accept (shared, unshared, re-shared); accepting
        // then still consumes the invite rather than failing on the primary key.
        if (!permissionService.hasPermission(accepted.resourceType(), accepted.resourceId(), callerEmail)) {
            permissionService.grant(accepted.resourceType(), accepted.resourceId(), callerEmail, accepted.role());
        }
    }

    @Transactional
    void revoke(String resourceType, UUID resourceId, String targetEmail, String requesterEmail) {
        boolean removed = permissionService.revoke(resourceType, resourceId, targetEmail, requesterEmail);
        if (!removed) {
            inviteService.cancel(resourceType, resourceId, targetEmail);
        }
    }

    @Transactional(readOnly = true)
    List<PermissionDto> getPermissions(String resourceType, UUID resourceId) {
        return Stream.concat(
                permissionService.listGranted(resourceType, resourceId).stream(),
                inviteService.listPending(resourceType, resourceId).stream()
        ).toList();
    }

    @Transactional
    void resourceDeleted(String resourceType, UUID resourceId) {
        permissionService.clear(resourceType, resourceId);
        inviteService.clear(resourceType, resourceId);
        log.info("Cleared permissions and pending invites for {} {}", resourceType, resourceId);
    }
}
