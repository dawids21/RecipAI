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

        PromptTemplate promptTemplate = new PromptTemplate("Extract recipe data from this page given as body text content\n<CONTENT>{content}</CONTENT>");
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
                .text("Extract recipe data from this image. Include name, ingredients with quantities, and step-by-step instructions.")
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