package xyz.stasiak.recipai.settings;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
class AdminController {

    private final AccountDeletionService accountDeletionService;

    @DeleteMapping("/accounts")
    ResponseEntity<Void> deleteAccount(
            @RequestParam @NotBlank @Email String email,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String operator = jwt.getClaimAsString("email");
        log.info("Admin {} deleting account {}", operator, email);
        accountDeletionService.deleteAccount(email);
        return ResponseEntity.noContent().build();
    }
}
