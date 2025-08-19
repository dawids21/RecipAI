package xyz.stasiak.recipai.extraction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
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

        log.debug("Extracted recipe with name: {}", extractedRecipe.name());

        return extractedRecipe;
    }
}