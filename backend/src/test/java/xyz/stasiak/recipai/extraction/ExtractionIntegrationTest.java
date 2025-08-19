package xyz.stasiak.recipai.extraction;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.client.RestClient;
import xyz.stasiak.recipai.TestcontainersConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExtractionIntegrationTest {

    @LocalServerPort
    private int port;

    private RestClient restClient() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void shouldExtractRecipeFromText() throws Exception {
        ClassPathResource textResource = new ClassPathResource("recipe_sources/kwestia_smaku.txt");
        String content = loadResourceContent(textResource);
        
        ExtractTextRequest request = new ExtractTextRequest(content);

        // Extract recipe from text
        ExtractedRecipe extractedRecipe = restClient()
                .post()
                .uri("/extract/text")
                .body(request)
                .retrieve()
                .body(ExtractedRecipe.class);

        assertThat(extractedRecipe).isNotNull();
        assertThat(extractedRecipe.name()).isNotNull();
        assertThat(extractedRecipe.description()).isNotNull();
        assertThat(extractedRecipe.ingredients()).isNotEmpty();
        assertThat(extractedRecipe.instructions()).isNotEmpty();
    }

    private String loadResourceContent(ClassPathResource resource) throws IOException {
        try (InputStream inputStream = resource.getInputStream()) {
            return FileCopyUtils.copyToString(new InputStreamReader(inputStream));
        }
    }
}