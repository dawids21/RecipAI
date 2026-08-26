package xyz.stasiak.recipai.permissions;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import xyz.stasiak.recipai.permissions.dto.PendingInviteDto;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/invites")
@RequiredArgsConstructor
@Slf4j
class InviteController {

    private final InviteService inviteService;
    private final PermissionsApplicationService applicationService;

    @GetMapping
    List<PendingInviteDto> getPendingInvites(@AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Getting pending invites for user: {}", userEmail);
        return inviteService.findPendingFor(userEmail);
    }

    @PostMapping("/{id}/accept")
    ResponseEntity<Void> acceptInvite(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Accepting invite: {} for user: {}", id, userEmail);
        applicationService.acceptInvite(id, userEmail);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/decline")
    ResponseEntity<Void> declineInvite(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Declining invite: {} for user: {}", id, userEmail);
        inviteService.decline(id, userEmail);
        return ResponseEntity.noContent().build();
    }
}
