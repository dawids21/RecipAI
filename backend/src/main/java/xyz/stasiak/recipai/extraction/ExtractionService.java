package xyz.stasiak.recipai.extraction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;
import xyz.stasiak.recipai.recipes.CreateRecipeRequest;
import xyz.stasiak.recipai.recipes.RecipeDto;
import xyz.stasiak.recipai.recipes.RecipeService;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
class ExtractionService {

    private final ChatClient chatClient;
    private final RecipeService recipeService;
    private final ObjectMapper objectMapper;

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
                convertToJsonNode(extractedRecipe)
        );
        
        return recipeService.save(request);
    }

    private JsonNode convertToJsonNode(ExtractedRecipe extractedRecipe) {
        return objectMapper.valueToTree(extractedRecipe);
    }
}