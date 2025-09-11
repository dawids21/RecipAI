package xyz.stasiak.recipai.recipes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class RecipeService {

    private final RecipeRepository recipeRepository;
    private final UserRecipeRepository userRecipeRepository;
    private final ObjectMapper objectMapper;

    public List<RecipeListDto> findAll(String userEmail) {
        return recipeRepository.findAllByUserEmail(userEmail).stream()
                .map(this::toRecipeListDto)
                .toList();
    }

    public RecipeDto findById(UUID id, String userEmail) {
        RecipeDto recipeDto = recipeRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new RecipeNotFoundException(id));

        // Check if user has access to this recipe
        if (userRecipeRepository.doesNotExistByIdEmailAndIdRecipeId(userEmail, id)) {
            throw new RecipeAccessDeniedException(id);
        }

        return recipeDto;
    }

    public RecipeDto save(CreateRecipeRequest request, String userEmail) {
        Recipe recipe = new Recipe();
        recipe.setName(request.name());
        recipe.setData(convertToJsonNode(request.data()));

        Recipe savedRecipe = recipeRepository.save(recipe);

        // Create UserRecipe association
        UserRecipe userRecipe = new UserRecipe();
        UserRecipeId userRecipeId = new UserRecipeId(userEmail, savedRecipe.getId());
        userRecipe.setId(userRecipeId);
        userRecipeRepository.save(userRecipe);
        
        return toDto(savedRecipe);
    }

    public RecipeDto updateById(UUID id, UpdateRecipeRequest request, String userEmail) {
        log.debug("Updating recipe with id: {} for user: {}", id, userEmail);

        Recipe existingRecipe = recipeRepository.findById(id)
                .orElseThrow(() -> new RecipeNotFoundException(id));

        // Check if user has access to this recipe
        if (userRecipeRepository.doesNotExistByIdEmailAndIdRecipeId(userEmail, id)) {
            throw new RecipeAccessDeniedException(id);
        }

        existingRecipe.setName(request.name());
        existingRecipe.setData(convertToJsonNode(request.data()));

        Recipe savedRecipe = recipeRepository.save(existingRecipe);
        return toDto(savedRecipe);
    }

    public void deleteById(UUID id, String userEmail) {
        log.debug("Deleting recipe with id: {} for user: {}", id, userEmail);

        if (!recipeRepository.existsById(id)) {
            throw new RecipeNotFoundException(id);
        }

        // Check if user has access to this recipe
        if (userRecipeRepository.doesNotExistByIdEmailAndIdRecipeId(userEmail, id)) {
            throw new RecipeAccessDeniedException(id);
        }

        // Delete UserRecipe association first
        UserRecipeId userRecipeId = new UserRecipeId(userEmail, id);
        userRecipeRepository.deleteById(userRecipeId);

        // Then delete the recipe itself
        recipeRepository.deleteById(id);
    }

    private RecipeDto toDto(Recipe recipe) {
        RecipeData recipeData = convertToRecipeData(recipe.getData());
        return new RecipeDto(recipe.getId(), recipe.getName(), recipeData);
    }

    private RecipeListDto toRecipeListDto(Recipe recipe) {
        return new RecipeListDto(recipe.getId(), recipe.getName());
    }

    private RecipeData convertToRecipeData(JsonNode jsonNode) {
        try {
            List<Ingredient> ingredients;
            List<Instruction> instructions;

            if (jsonNode.has("ingredients")) {
                ingredients = objectMapper.treeToValue(jsonNode.get("ingredients"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Ingredient.class));
            } else {
                ingredients = List.of();
            }

            if (jsonNode.has("instructions")) {
                instructions = objectMapper.treeToValue(jsonNode.get("instructions"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Instruction.class)
                );
            } else {
                instructions = List.of();
            }

            return new RecipeData(ingredients, instructions);
        } catch (Exception e) {
            log.error("Failed to convert JsonNode to RecipeData", e);
            throw new RuntimeException("Invalid recipe data format", e);
        }
    }

    private JsonNode convertToJsonNode(RecipeData recipeData) {
        return objectMapper.valueToTree(recipeData);
    }
}