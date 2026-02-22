package xyz.stasiak.recipai.recipes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeFacade {

    private final RecipeRepository recipeRepository;
    private final RecipeService recipeService;
    private final ObjectMapper objectMapper;

    public RecipeIngredientsResult getIngredients(Collection<UUID> recipeIds, String userEmail) {
        log.debug("Getting ingredients for {} recipes for user {}", recipeIds.size(), userEmail);

        Set<UUID> accessibleRecipeIds = recipeService.findAll(userEmail).stream()
                .map(RecipeListDto::id)
                .collect(Collectors.toSet());

        List<Recipe> recipes = recipeRepository.findAllById(recipeIds);

        List<RecipeWithIngredients> recipeWithIngredients = recipes.stream()
                .filter(r -> accessibleRecipeIds.contains(r.getId()))
                .map(r -> new RecipeWithIngredients(
                        r.getId(),
                        extractServingSize(r.getData()),
                        extractIngredients(r.getData())
                ))
                .toList();

        List<String> inaccessibleRecipeNames = recipes.stream()
                .filter(r -> !accessibleRecipeIds.contains(r.getId()))
                .map(Recipe::getName)
                .toList();

        return new RecipeIngredientsResult(recipeWithIngredients, inaccessibleRecipeNames);
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

    private int extractServingSize(JsonNode data) {
        JsonNode servingSize = data.path("servingSize");
        if (servingSize.isNumber() && servingSize.asInt() > 0) {
            return servingSize.asInt();
        }
        return 1;
    }
}
