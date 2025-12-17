package xyz.stasiak.recipai.recipes;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/recipes")
@RequiredArgsConstructor
@Slf4j
class RecipeController {

    private final RecipeService recipeService;

    @GetMapping
    public List<RecipeListDto> getAllRecipes(
            @RequestParam(required = false) UUID collectionId,
            @RequestParam(required = false) Boolean unassigned,
            @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");

        // Validate mutual exclusivity
        if (collectionId != null && Boolean.TRUE.equals(unassigned)) {
            log.warn("User {} provided both collectionId and unassigned filters", userEmail);
            throw new IllegalArgumentException("Cannot specify both collectionId and unassigned filters");
        }

        // Route to appropriate service method
        if (collectionId != null) {
            log.debug("Getting recipes for collection {} for user {}", collectionId, userEmail);
            return recipeService.findAllByCollectionId(collectionId, userEmail);
        } else if (Boolean.TRUE.equals(unassigned)) {
            log.debug("Getting unassigned recipes for user {}", userEmail);
            return recipeService.findAllUnassigned(userEmail);
        } else {
            log.debug("Getting all accessible recipes for user {}", userEmail);
            return recipeService.findAll(userEmail);
        }
    }

    @GetMapping("/{id}")
    public RecipeDetailsDto getRecipeById(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Getting recipe by id: {} for user: {}", id, userEmail);
        return recipeService.findById(id, userEmail);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RecipeDetailsDto> createRecipe(@Valid @RequestBody CreateRecipeRequest request, @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Creating recipe with name: {} for user: {}", request.name(), userEmail);
        RecipeDetailsDto savedRecipe = recipeService.save(request, userEmail);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(savedRecipe);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RecipeDetailsDto> createRecipeWithImages(
            @RequestPart("data") @Valid CreateRecipeRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Creating recipe with name: {} and {} images for user: {}", request.name(), images != null ? images.size() : 0, userEmail);

        if (images == null) {
            images = List.of();
        }

        RecipeDetailsDto savedRecipe = recipeService.save(request, images, userEmail);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(savedRecipe);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RecipeDetailsDto> updateRecipe(@PathVariable UUID id, @Valid @RequestBody UpdateRecipeRequest request, @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Updating recipe with id: {} for user: {}", id, userEmail);
        RecipeDetailsDto updatedRecipe = recipeService.updateById(id, request, userEmail);
        return ResponseEntity.ok(updatedRecipe);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RecipeDetailsDto> updateRecipeWithImages(
            @PathVariable UUID id,
            @RequestPart("data") @Valid UpdateRecipeRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Updating recipe with id: {} and {} images for user: {}", id, images != null ? images.size() : 0, userEmail);

        if (images == null) {
            images = List.of();
        }
        RecipeDetailsDto updatedRecipe = recipeService.updateById(id, request, images, userEmail);
        return ResponseEntity.ok(updatedRecipe);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecipe(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Deleting recipe with id: {} for user: {}", id, userEmail);
        recipeService.deleteById(id, userEmail);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/share")
    public ResponseEntity<Void> shareRecipe(@PathVariable UUID id, @Valid @RequestBody ShareRecipeRequest request, @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Sharing recipe with id: {} from user: {} to user: {}", id, userEmail, request.email());
        recipeService.shareRecipe(request.email(), id, userEmail);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/unshare")
    public ResponseEntity<Void> unshareRecipe(@PathVariable UUID id, @Valid @RequestBody UnshareRecipeRequest request, @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Unsharing recipe with id: {} from user: {} for user: {}", id, userEmail, request.email());
        recipeService.unshareRecipe(request.email(), id, userEmail);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/shared_users")
    public List<SharedUserDto> getSharedUsers(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Getting shared users for recipe: {} by user: {}", id, userEmail);
        return recipeService.getSharedUsers(id, userEmail);
    }

}