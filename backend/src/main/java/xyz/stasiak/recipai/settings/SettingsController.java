package xyz.stasiak.recipai.settings;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
@Slf4j
class SettingsController {

    private final AccountDeletionService accountDeletionService;

    @DeleteMapping("/account")
    ResponseEntity<Void> deleteOwnAccount(@AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Deleting own account for user: {}", userEmail);
        accountDeletionService.deleteAccount(userEmail);
        return ResponseEntity.noContent().build();
    }
}
