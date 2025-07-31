package xyz.stasiak.recipai.extraction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;
import xyz.stasiak.recipai.recipes.*;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
class ExtractionService {

    private final ChatClient chatClient;
    private final RecipeService recipeService;

    public RecipeDto extractFromText(String text) {
        log.debug("Extracting recipe from text with {} characters", text.length());

        PromptTemplate promptTemplate = new PromptTemplate("Extract recipe data from this page given as body text content\n<CONTENT>{content}</CONTENT>");
        Prompt prompt = promptTemplate.create(Map.of("content", text));

        ExtractedRecipe extractedRecipe = chatClient.prompt(prompt)
                .call()
                .entity(ExtractedRecipe.class);

        log.debug("Extracted recipe with name: {}", extractedRecipe.name());

        CreateRecipeRequest request = new CreateRecipeRequest(
                extractedRecipe.name(),
                convertToRecipeData(extractedRecipe)
        );

        return recipeService.save(request);
    }

    private RecipeData convertToRecipeData(ExtractedRecipe extractedRecipe) {
        List<Ingredient> ingredients = extractedRecipe.ingredients().stream()
                .map(extracted -> new Ingredient(extracted.name(), extracted.quantity(), extracted.unit()))
                .toList();

        List<Instruction> instructions = extractedRecipe.steps().stream()
                .map(extracted -> new Instruction(extracted.description()))
                .toList();

        return new RecipeData(ingredients, instructions);
    }
}