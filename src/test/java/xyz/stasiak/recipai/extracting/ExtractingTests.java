package xyz.stasiak.recipai.extracting;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
class ExtractingTests {

    @Autowired
    ChatClient chatClient;

    @Test
    void extractRecipeDataFromImage() {
        ClassPathResource imageResource = new ClassPathResource("recipe_sources/kwestia_smaku.jpg");
        Media imageMedia = new Media(MimeTypeUtils.IMAGE_JPEG, imageResource);
        UserMessage userMessage = UserMessage.builder()
                .media(imageMedia)
                .text("Extract recipe data from this image")
                .build();


        Recipe recipe = chatClient.prompt(new Prompt(userMessage))
                .call()
                .entity(Recipe.class);

        assertThat(recipe).isNotNull();
        assertThat(recipe.name).containsIgnoringCase("wegańskie chili");
    }

    @Test
    void extractRecipeDataFromLink() {
        RestClient restClient = RestClient.create();

        String linkContent = restClient.get()
                .uri("https://www.kwestiasmaku.com/przepis/weganskie-chili-z-soczewica-i-fasola")
                .retrieve()
                .body(String.class);
        assert linkContent != null;

        PromptTemplate promptTemplate = new PromptTemplate("Extract recipe data from this page given as HTML content\n<CONTENT>{content}</CONTENT>");
        Prompt prompt = promptTemplate.create(Map.of("content", linkContent));
        Recipe recipe = chatClient.prompt(prompt)
                .call()
                .entity(Recipe.class);

        assertThat(recipe).isNotNull();
        assertThat(recipe.name).containsIgnoringCase("wegańskie chili");
    }

    record Recipe(String name, String description, List<Ingredient> ingredients, List<Step> steps) {
    }

    record Ingredient(String name, String quantity, String unit) {
    }

    record Step(String description) {
    }

    @TestConfiguration
    static class AiConfig {

        @Bean
        @Primary
        ChatClient chatClient(ChatClient.Builder builder) {
            return builder
                    .defaultAdvisors(new SimpleLoggerAdvisor())
                    .build();
        }
    }
}
