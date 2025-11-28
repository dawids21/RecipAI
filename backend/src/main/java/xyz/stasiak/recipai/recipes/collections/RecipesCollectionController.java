package xyz.stasiak.recipai.recipes.collections;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import xyz.stasiak.recipai.recipes.collections.dto.CreateRecipesCollectionRequest;
import xyz.stasiak.recipai.recipes.collections.dto.RecipesCollectionListDto;
import xyz.stasiak.recipai.recipes.collections.dto.UpdateRecipesCollectionRequest;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/collections")
@RequiredArgsConstructor
@Slf4j
class RecipesCollectionController {

    private final RecipesCollectionService recipesCollectionService;

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
}