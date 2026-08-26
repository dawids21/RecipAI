package xyz.stasiak.recipai.permissions;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.stasiak.recipai.permissions.dto.PendingInviteDto;
import xyz.stasiak.recipai.permissions.dto.PermissionDto;
import xyz.stasiak.recipai.permissions.dto.ResourceRole;
import xyz.stasiak.recipai.permissions.exception.InvalidInviteRoleException;
import xyz.stasiak.recipai.permissions.exception.InviteNotFoundException;
import xyz.stasiak.recipai.permissions.exception.InviteRefusedException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class InviteService {

    private final ResourceInviteRepository inviteRepository;

    List<PendingInviteDto> findPendingFor(String email) {
        return inviteRepository.findByEmailOrderByCreatedAtDesc(email).stream()
                .map(i -> new PendingInviteDto(i.getId(), i.getResourceType(), i.getLabel(),
                        i.getInvitedBy(), i.getRole(), i.getCreatedAt()))
                .toList();
    }

    UUID create(String resourceType, UUID resourceId, String targetEmail, ResourceRole role, String label, String invitedByEmail) {
        if (role == ResourceRole.OWNER) {
            throw new InvalidInviteRoleException(role);
        }

        if (inviteRepository.existsByResourceTypeAndResourceIdAndEmail(resourceType, resourceId, targetEmail)) {
            log.warn("{} already has a pending invite to {} {} - refusing invite", targetEmail, resourceType, resourceId);
            throw new InviteRefusedException(resourceType, targetEmail, InviteRefusedException.Reason.ALREADY_INVITED);
        }

        try {
            ResourceInvite invite = new ResourceInvite(resourceType, resourceId, targetEmail, role, invitedByEmail, label);
            ResourceInvite saved = inviteRepository.saveAndFlush(invite);
            log.info("Invite {} created for {} to {} {} by {}", saved.getId(), targetEmail, resourceType, resourceId, invitedByEmail);
            return saved.getId();
        } catch (DataIntegrityViolationException ex) {
            log.warn("Concurrent invite race lost for {} to {} {} - refusing invite", targetEmail, resourceType, resourceId);
            throw new InviteRefusedException(resourceType, targetEmail, InviteRefusedException.Reason.ALREADY_INVITED);
        }
    }

    AcceptedInvite accept(UUID inviteId, String callerEmail) {
        ResourceInvite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new InviteNotFoundException(inviteId));
        if (!invite.getEmail().equals(callerEmail)) {
            throw new InviteNotFoundException(inviteId);
        }

        inviteRepository.delete(invite);
        log.info("Invite {} accepted by {}", inviteId, callerEmail);
        return new AcceptedInvite(invite.getResourceType(), invite.getResourceId(), invite.getRole());
    }

    @Transactional
    void decline(UUID inviteId, String callerEmail) {
        ResourceInvite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new InviteNotFoundException(inviteId));
        if (!invite.getEmail().equals(callerEmail)) {
            throw new InviteNotFoundException(inviteId);
        }

        inviteRepository.delete(invite);
        log.info("Invite {} declined by {}", inviteId, callerEmail);
    }

    void cancel(String resourceType, UUID resourceId, String email) {
        inviteRepository.deleteByResourceTypeAndResourceIdAndEmail(resourceType, resourceId, email);
    }

    List<PermissionDto> listPending(String resourceType, UUID resourceId) {
        return inviteRepository.findByResourceTypeAndResourceIdOrderByCreatedAtAsc(resourceType, resourceId).stream()
                .map(i -> new PermissionDto(i.getEmail(), i.getRole(), true))
                .toList();
    }

    void clear(String resourceType, UUID resourceId) {
        inviteRepository.deleteByResourceTypeAndResourceId(resourceType, resourceId);
    }
}
