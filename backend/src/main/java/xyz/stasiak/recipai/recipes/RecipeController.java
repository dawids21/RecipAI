package xyz.stasiak.recipai.recipes;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/recipes")
@RequiredArgsConstructor
@Slf4j
class RecipeController {

    private final RecipeService recipeService;

    @GetMapping
    public List<RecipeListDto> getAllRecipes(@AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Getting all recipes for user: {}", userEmail);
        return recipeService.findAll(userEmail);
    }

    @GetMapping("/{id}")
    public RecipeDto getRecipeById(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Getting recipe by id: {} for user: {}", id, userEmail);
        return recipeService.findById(id, userEmail);
    }

    @PostMapping
    public ResponseEntity<RecipeDto> createRecipe(@Valid @RequestBody CreateRecipeRequest request, @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Creating recipe with name: {} for user: {}", request.name(), userEmail);
        RecipeDto savedRecipe = recipeService.save(request, userEmail);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(savedRecipe);
    }

    @PutMapping("/{id}")
    public ResponseEntity<RecipeDto> updateRecipe(@PathVariable UUID id, @Valid @RequestBody UpdateRecipeRequest request, @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Updating recipe with id: {} for user: {}", id, userEmail);
        RecipeDto updatedRecipe = recipeService.updateById(id, request, userEmail);
        return ResponseEntity.ok(updatedRecipe);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecipe(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Deleting recipe with id: {} for user: {}", id, userEmail);
        recipeService.deleteById(id, userEmail);
        return ResponseEntity.noContent().build();
    }

}