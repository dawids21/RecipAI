package xyz.stasiak.recipai.extracting;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.content.Media;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.client.RestClient;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
class ExtractingTests {

    @Autowired
    ChatClient chatClient;

    @ParameterizedTest
    @CsvSource({
            "recipe_sources/kwestia_smaku.jpg,wegańskie chili",
            "recipe_sources/ania_gotuje.jpg,pappardelle z kurczakiem",
            "recipe_sources/instagram.jpg,curry z tofu",
            "recipe_sources/tiktok.jpg,danie jednogarnkowe"
    })
    void extractRecipeDataFromImage(String image, String expectedName) {
        ClassPathResource imageResource = new ClassPathResource(image);
        Media imageMedia = new Media(MimeTypeUtils.IMAGE_JPEG, imageResource);
        UserMessage userMessage = UserMessage.builder()
                .media(imageMedia)
                .text("Extract recipe data from this image")
                .build();


        Recipe recipe = chatClient.prompt(new Prompt(userMessage))
                .call()
                .entity(Recipe.class);

        assertThat(recipe).isNotNull();
        assertThat(recipe.name).containsIgnoringCase(expectedName);
    }

    @ParameterizedTest
    @CsvSource({
            "https://www.kwestiasmaku.com/przepis/weganskie-chili-z-soczewica-i-fasola,wegańskie chili",
            "https://aniagotuje.pl/przepis/pappardelle-z-kurczakiem,pappardelle z kurczakiem",
//            "https://www.instagram.com/p/CslHY_bIjIF/,curry z tofu",
//            "https://www.tiktok.com/@jakjalubiejesc/video/7205547465674624261,danie jednogarnkowe"
    })
    void extractRecipeDataFromUrl(String url, String expectedName) {
        RestClient restClient = RestClient.create();

        String urlContent = restClient.get()
                .uri(url)
                .retrieve()
                .body(String.class);
        assert urlContent != null;

        PromptTemplate promptTemplate = new PromptTemplate("Extract recipe data from this page given as HTML content\n<CONTENT>{content}</CONTENT>");
        Prompt prompt = promptTemplate.create(Map.of("content", urlContent));
        Recipe recipe = chatClient.prompt(prompt)
                .call()
                .entity(Recipe.class);

        assertThat(recipe).isNotNull();
        assertThat(recipe.name).containsIgnoringCase(expectedName);
    }

    @ParameterizedTest
    @CsvSource({
//            "https://www.kwestiasmaku.com/przepis/weganskie-chili-z-soczewica-i-fasola,wegańskie chili",
            "https://aniagotuje.pl/przepis/pappardelle-z-kurczakiem,pappardelle z kurczakiem",
//            "https://www.instagram.com/p/CslHY_bIjIF/,curry z tofu",
//            "https://www.tiktok.com/@jakjalubiejesc/video/7205547465674624261,danie jednogarnkowe"
    })
    void extractRecipeDataFromUrlUsingPlaywright(String url, String expectedName) {
        String urlContent;
        try (Playwright playwright = Playwright.create()) {
            try (Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true).setSlowMo(50))) {
//            try (Browser browser = playwright.chromium().launch()) {
                Page page = browser.newContext().newPage();
                page.navigate(url);
                page.waitForLoadState(LoadState.NETWORKIDLE);
                page.screenshot(new Page.ScreenshotOptions()
                        .setPath(Paths.get("screenshot.png"))
                        .setFullPage(true));
//                List<ElementHandle> elements = page.querySelectorAll("body");
//                urlContent = elements.stream()
//                        .map(ElementHandle::textContent)
//                        .collect(Collectors.joining("\n\n"));
            }
        }

        PromptTemplate promptTemplate = new PromptTemplate("Extract recipe data from this page given as HTML content\n<CONTENT>{content}</CONTENT>");
//        Prompt prompt = promptTemplate.create(Map.of("content", urlContent));
//        Recipe recipe = chatClient.prompt(prompt)
//                .call()
//                .entity(Recipe.class);
//
//        assertThat(recipe).isNotNull();
//        assertThat(recipe.name).containsIgnoringCase(expectedName);
    }

    @ParameterizedTest
    @CsvSource({
            "recipe_sources/kwestia_smaku.pdf,wegańskie chili",
            "recipe_sources/ania_gotuje.pdf,pappardelle z kurczakiem",
    })
    void extractRecipeDataFromPdf(String fileName, String expectedName) {
        ClassPathResource pdfFileResource = new ClassPathResource(fileName);
        PagePdfDocumentReader reader = new PagePdfDocumentReader(pdfFileResource);
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> documents = splitter.split(reader.read());
        String pdfContent = documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        PromptTemplate promptTemplate = new PromptTemplate("Extract recipe data from this page given as HTML content\n<CONTENT>{content}</CONTENT>");
        Prompt prompt = promptTemplate.create(Map.of("content", pdfContent));
        Recipe recipe = chatClient.prompt(prompt)
                .call()
                .entity(Recipe.class);

        assertThat(recipe).isNotNull();
        assertThat(recipe.name).containsIgnoringCase(expectedName);
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
