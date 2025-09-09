package xyz.stasiak.recipai.users;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Slf4j
class UserController {

    private final UserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<Void> register(@AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        log.debug("Registering user with email: {}", email);

        // Check if user exists, if not create
        if (!userRepository.existsById(email)) {
            User user = new User();
            user.setEmail(email);
            userRepository.save(user);
            log.debug("Created new user: {}", email);
        } else {
            log.debug("User already exists: {}", email);
        }

        return ResponseEntity.ok().build();
    }
}