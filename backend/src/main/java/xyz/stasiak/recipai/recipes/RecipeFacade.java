package xyz.stasiak.recipai.recipes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeFacade {

    private final RecipeRepository recipeRepository;
    private final ObjectMapper objectMapper;

    public List<Ingredient> getIngredients(Collection<UUID> recipeIds) {
        log.debug("Getting ingredients for {} recipes", recipeIds.size());
        return recipeRepository.findAllById(recipeIds).stream()
                .flatMap(recipe -> extractIngredients(recipe.getData()).stream())
                .toList();
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
}
