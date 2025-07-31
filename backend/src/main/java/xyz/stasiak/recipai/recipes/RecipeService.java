package xyz.stasiak.recipai.recipes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final ObjectMapper objectMapper;

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
        recipe.setData(convertToJsonNode(request.data()));

        Recipe savedRecipe = recipeRepository.save(recipe);
        return toDto(savedRecipe);
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
            // Handle both ExtractedRecipe format (from AI extraction) and RecipeData format (from API)
            List<Ingredient> ingredients;
            List<Instruction> instructions;

            if (jsonNode.has("ingredients")) {
                ingredients = objectMapper.treeToValue(jsonNode.get("ingredients"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Ingredient.class));
            } else {
                ingredients = List.of();
            }

            if (jsonNode.has("instructions")) {
                // Direct mapping for RecipeData format
                instructions = objectMapper.treeToValue(jsonNode.get("instructions"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Instruction.class));
            } else if (jsonNode.has("steps")) {
                // Convert from ExtractedRecipe format (steps with "description" field to instructions with "step" field)
                JsonNode stepsNode = jsonNode.get("steps");
                instructions = new ArrayList<>();
                for (JsonNode stepNode : stepsNode) {
                    String stepDescription = stepNode.get("description").asText();
                    instructions.add(new Instruction(stepDescription));
                }
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