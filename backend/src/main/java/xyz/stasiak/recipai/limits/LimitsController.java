package xyz.stasiak.recipai.limits;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/limits")
@RequiredArgsConstructor
@Slf4j
class LimitsController {

    private final LimitsFacade limitsFacade;

    @GetMapping
    List<LimitCap> getLimits(@AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Getting limits for user: {}", userEmail);
        return limitsFacade.caps(userEmail);
    }
}
