package xyz.stasiak.recipai.recipes;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecipeService {

    private final RecipeRepository recipeRepository;

    public List<RecipeListDto> findAll() {
        return recipeRepository.findAll().stream()
                .map(this::toRecipeListDto)
                .toList();
    }

    public Optional<RecipeDto> findById(UUID id) {
        return recipeRepository.findById(id)
                .map(this::toDto);
    }

    public RecipeDto save(CreateRecipeRequest request) {
        Recipe recipe = new Recipe();
        recipe.setName(request.name());
        recipe.setData(request.data());
        
        Recipe savedRecipe = recipeRepository.save(recipe);
        return toDto(savedRecipe);
    }

    private RecipeDto toDto(Recipe recipe) {
        return new RecipeDto(recipe.getId(), recipe.getName(), recipe.getData());
    }

    private RecipeListDto toRecipeListDto(Recipe recipe) {
        return new RecipeListDto(recipe.getId(), recipe.getName());
    }
}