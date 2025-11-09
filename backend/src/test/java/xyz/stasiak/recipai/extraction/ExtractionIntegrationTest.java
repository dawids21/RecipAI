package xyz.stasiak.recipai.extraction;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import xyz.stasiak.recipai.TestSecurityConfiguration;
import xyz.stasiak.recipai.TestcontainersConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static org.assertj.core.api.Assertions.assertThat;

@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExtractionIntegrationTest {

    @LocalServerPort
    private int port;

    private RestClient restClient() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader("Authorization", "Bearer " + TestSecurityConfiguration.AUTH_TOKEN)
                .build();
    }

    @Test
    void shouldExtractRecipeFromText() throws Exception {
        RestClient client = restClient();
        ClassPathResource textResource = new ClassPathResource("recipe_sources/kwestia_smaku.txt");
        String content = loadResourceContent(textResource);

        ExtractTextRequest request = new ExtractTextRequest(content);

        // Extract recipe from text
        ExtractedRecipe extractedRecipe = client
                .post()
                .uri("/extract/text")
                .body(request)
                .retrieve()
                .body(ExtractedRecipe.class);

        assertThat(extractedRecipe).isNotNull();
        assertThat(extractedRecipe.name()).isNotNull();
        assertThat(extractedRecipe.ingredients()).isNotEmpty();
        assertThat(extractedRecipe.instructions()).isNotEmpty();
    }

    @Test
    void shouldExtractRecipeFromImage() {
        RestClient client = restClient();
        ClassPathResource imageResource = new ClassPathResource("recipe_sources/kwestia_smaku.jpg");

        // Prepare multipart request
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", imageResource);

        // Extract recipe from image
        ExtractedRecipe extractedRecipe = client
                .post()
                .uri("/extract/image")
                .body(parts)
                .retrieve()
                .body(ExtractedRecipe.class);

        assertThat(extractedRecipe).isNotNull();
        assertThat(extractedRecipe.name()).isNotNull();
        assertThat(extractedRecipe.ingredients()).isNotEmpty();
        assertThat(extractedRecipe.instructions()).isNotEmpty();
    }

    private String loadResourceContent(ClassPathResource resource) throws IOException {
        try (InputStream inputStream = resource.getInputStream()) {
            return FileCopyUtils.copyToString(new InputStreamReader(inputStream));
        }
    }
}