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
        return recipeService.findAll().stream()
                .map(this::toRecipeListDto)
                .toList();
    }

    @GetMapping("/{id}")
    public RecipeDto getRecipeById(@PathVariable UUID id) {
        log.debug("Getting recipe by id: {}", id);
        Recipe recipe = recipeService.findById(id)
                .orElseThrow(() -> new RecipeNotFoundException(id));
        return toRecipeDto(recipe);
    }

    @PostMapping
    public ResponseEntity<RecipeDto> createRecipe(@Valid @RequestBody CreateRecipeRequest request) {
        log.debug("Creating recipe with name: {}", request.name());
        Recipe recipe = new Recipe();
        recipe.setName(request.name());
        recipe.setData(request.data());
        
        Recipe savedRecipe = recipeService.save(recipe);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toRecipeDto(savedRecipe));
    }

    private RecipeListDto toRecipeListDto(Recipe recipe) {
        return new RecipeListDto(recipe.getId(), recipe.getName());
    }

    private RecipeDto toRecipeDto(Recipe recipe) {
        return new RecipeDto(recipe.getId(), recipe.getName(), recipe.getData());
    }
}