package xyz.stasiak.recipai.extraction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
class ExtractionService {

    private final ChatClient chatClient;

    public ExtractedRecipe extractFromText(String text) {
        log.debug("Extracting recipe from text with {} characters", text.length());

        PromptTemplate promptTemplate = new PromptTemplate("Extract recipe data from this page given as body text content.\nInclude the number of servings if available in the source.\nFor each ingredient: extract numeric quantities as a number into 'quantity'; extract non-numeric descriptors (e.g. \"to taste\", \"a pinch\", \"fresh\") into 'comment'; leave 'quantity' null when there is no numeric quantity.\n<CONTENT>{content}</CONTENT>");
        Prompt prompt = promptTemplate.create(Map.of("content", text));

        ExtractedRecipe extractedRecipe = chatClient.prompt(prompt)
                .call()
                .entity(ExtractedRecipe.class);

        if (extractedRecipe == null) {
            throw new IllegalStateException("Failed to extract recipe from text.");
        }

        log.debug("Extracted recipe from text with name: {}", extractedRecipe.name());

        return extractedRecipe;
    }

    public ExtractedRecipe extractFromImage(Media imageMedia) {
        log.debug("Extracting recipe from image media");

        UserMessage userMessage = UserMessage.builder()
                .text("Extract recipe data from this image. Include name, ingredients with quantities, step-by-step instructions, and the number of servings if visible. For each ingredient: extract numeric quantities as a number into 'quantity'; extract non-numeric descriptors (e.g. \"to taste\", \"a pinch\", \"fresh\") into 'comment'; leave 'quantity' null when there is no numeric quantity.")
                .media(imageMedia)
                .build();

        ExtractedRecipe extractedRecipe = chatClient.prompt(new Prompt(userMessage))
                .call()
                .entity(ExtractedRecipe.class);

        if (extractedRecipe == null) {
            throw new IllegalStateException("Failed to extract recipe from image.");
        }

        log.debug("Extracted recipe from image with name: {}", extractedRecipe.name());

        return extractedRecipe;
    }
}