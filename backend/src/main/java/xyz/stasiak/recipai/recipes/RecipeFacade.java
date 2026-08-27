package xyz.stasiak.recipai.recipes;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeFacade {

    private final RecipeRepository recipeRepository;
    private final RecipeService recipeService;
    private final ObjectMapper objectMapper;

    // Every recipe the caller reaches, directly or through a collection they have access to.
    public Set<UUID> getAccessibleRecipeIds(String userEmail) {
        log.debug("Getting accessible recipe ids for user {}", userEmail);
        return recipeService.accessibleRecipeIds(userEmail);
    }

    public RecipeInfoResult getRecipes(Collection<UUID> recipeIds, String userEmail) {
        log.debug("Getting recipes for {} recipe ids for user {}", recipeIds.size(), userEmail);

        Set<UUID> accessibleRecipeIds = recipeService.accessibleRecipeIds(userEmail);

        List<Recipe> recipes = recipeRepository.findAllById(recipeIds);

        List<RecipeInfo> recipeInfos = recipes.stream()
                .filter(r -> accessibleRecipeIds.contains(r.getId()))
                .map(r -> new RecipeInfo(
                        r.getId(),
                        r.getName(),
                        r.getRecipesCollectionId(),
                        r.getCreatedAt(),
                        extractIngredients(r.getData()),
                        extractInstructions(r.getData()),
                        extractSourceUrl(r.getData()),
                        extractServingSize(r.getData())
                ))
                .toList();

        List<String> inaccessibleRecipeNames = recipes.stream()
                .filter(r -> !accessibleRecipeIds.contains(r.getId()))
                .map(Recipe::getName)
                .toList();

        return new RecipeInfoResult(recipeInfos, inaccessibleRecipeNames);
    }

    private List<Ingredient> extractIngredients(JsonNode data) {
        try {
            if (!data.has("ingredients")) {
                return List.of();
            }
            return objectMapper.treeToValue(
                    data.get("ingredients"),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Ingredient.class)
            );
        } catch (Exception e) {
            log.error("Failed to extract ingredients from recipe data", e);
            return List.of();
        }
    }

    private List<Instruction> extractInstructions(JsonNode data) {
        try {
            if (!data.has("instructions")) {
                return List.of();
            }
            return objectMapper.treeToValue(
                    data.get("instructions"),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Instruction.class)
            );
        } catch (Exception e) {
            log.error("Failed to extract instructions from recipe data", e);
            return List.of();
        }
    }

    private String extractSourceUrl(JsonNode data) {
        JsonNode sourceUrl = data.path("sourceUrl");
        if (sourceUrl.isString()) {
            return sourceUrl.asString();
        }
        return null;
    }

    private int extractServingSize(JsonNode data) {
        JsonNode servingSize = data.path("servingSize");
        if (servingSize.isNumber() && servingSize.asInt() > 0) {
            return servingSize.asInt();
        }
        return 1;
    }
}
