package xyz.stasiak.recipai.recipes;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public List<RecipeListDto> getAllRecipes() {
        log.debug("Getting all recipes");
        return recipeService.findAll();
    }

    @GetMapping("/{id}")
    public RecipeDto getRecipeById(@PathVariable UUID id) {
        log.debug("Getting recipe by id: {}", id);
        return recipeService.findById(id)
                .orElseThrow(() -> new RecipeNotFoundException(id));
    }

    @PostMapping
    public ResponseEntity<RecipeDto> createRecipe(@Valid @RequestBody CreateRecipeRequest request) {
        log.debug("Creating recipe with name: {}", request.name());
        RecipeDto savedRecipe = recipeService.save(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(savedRecipe);
    }

}