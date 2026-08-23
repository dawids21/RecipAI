package xyz.stasiak.recipai.recipes.collections;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import xyz.stasiak.recipai.limits.LimitStanding;
import xyz.stasiak.recipai.recipes.collections.dto.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/collections")
@RequiredArgsConstructor
@Slf4j
class RecipesCollectionController {

    private final RecipesCollectionService recipesCollectionService;

    @GetMapping("/usage")
    LimitStanding getUsage(@AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Getting recipes collection usage for user: {}", userEmail);
        return recipesCollectionService.usage(userEmail);
    }

    @GetMapping
    List<RecipesCollectionListDto> getAllRecipesCollections(@AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Getting recipes collections for user: {}", userEmail);
        return recipesCollectionService.findAll(userEmail);
    }

    @PostMapping
    ResponseEntity<RecipesCollectionListDto> createRecipesCollection(@Valid @RequestBody CreateRecipesCollectionRequest request, @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Creating recipes collection for user: {}", userEmail);
        RecipesCollectionListDto dto = recipesCollectionService.create(request, userEmail);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PutMapping("/{id}")
    ResponseEntity<RecipesCollectionListDto> updateRecipesCollection(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRecipesCollectionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Updating recipes collection with id: {} for user: {}", id, userEmail);
        RecipesCollectionListDto updatedCollection = recipesCollectionService.updateById(id, request, userEmail);
        return ResponseEntity.ok(updatedCollection);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> deleteRecipesCollection(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Deleting recipes collection with id: {} for user: {}", id, userEmail);
        recipesCollectionService.deleteById(id, userEmail);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/users")
    List<SharedUserDto> getSharedUsers(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Getting shared users for recipes collection: {} by user: {}", id, userEmail);
        return recipesCollectionService.getSharedUsers(id, userEmail);
    }

    @PostMapping("/{id}/share")
    ResponseEntity<Void> shareRecipesCollection(@PathVariable UUID id, @Valid @RequestBody ShareRecipesCollectionRequest request, @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Sharing recipes collection {} from {} to {}", id, userEmail, request.email());
        recipesCollectionService.shareRecipesCollection(request.email(), id, userEmail);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/unshare")
    ResponseEntity<Void> unshareRecipesCollection(@PathVariable UUID id, @Valid @RequestBody UnshareRecipesCollectionRequest request, @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Unsharing recipes collection {} from {} for {}", id, userEmail, request.email());
        recipesCollectionService.unshareRecipesCollection(request.email(), id, userEmail);
        return ResponseEntity.noContent().build();
    }
}