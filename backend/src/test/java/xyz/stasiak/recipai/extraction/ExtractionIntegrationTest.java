package xyz.stasiak.recipai.extraction;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.client.RestClient;
import xyz.stasiak.recipai.TestcontainersConfiguration;
import xyz.stasiak.recipai.recipes.RecipeDto;
import xyz.stasiak.recipai.recipes.RecipeListDto;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

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
    void shouldExtractRecipeFromTextAndMakeItAvailableInRecipes() throws Exception {
        ClassPathResource textResource = new ClassPathResource("recipe_sources/kwestia_smaku.txt");
        String content = loadResourceContent(textResource);
        
        ExtractTextRequest request = new ExtractTextRequest(content);

        // Extract recipe from text
        RecipeDto extractedRecipe = restClient()
                .post()
                .uri("/extract/text")
                .body(request)
                .retrieve()
                .body(RecipeDto.class);

        assertThat(extractedRecipe).isNotNull();
        assertThat(extractedRecipe.id()).isNotNull();
        assertThat(extractedRecipe.name()).isNotNull();
        assertThat(extractedRecipe.data()).isNotNull();
        assertThat(extractedRecipe.data().ingredients()).isNotEmpty();
        assertThat(extractedRecipe.data().instructions()).isNotEmpty();

        // Verify the extracted recipe is available in the recipes list (independent of existing count)
        List<RecipeListDto> recipes = restClient()
                .get()
                .uri("/recipes")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        assertThat(recipes).isNotEmpty();
        assertThat(recipes)
                .extracting(RecipeListDto::id)
                .contains(extractedRecipe.id());

        // Verify the extracted recipe can be retrieved by ID
        RecipeDto retrievedRecipe = restClient()
                .get()
                .uri("/recipes/" + extractedRecipe.id())
                .retrieve()
                .body(RecipeDto.class);

        assertThat(retrievedRecipe).isNotNull();
        assertThat(retrievedRecipe.id()).isEqualTo(extractedRecipe.id());
        assertThat(retrievedRecipe.name()).isEqualTo(extractedRecipe.name());
        assertThat(retrievedRecipe.data()).isEqualTo(extractedRecipe.data());
    }

    private String loadResourceContent(ClassPathResource resource) throws IOException {
        try (InputStream inputStream = resource.getInputStream()) {
            return FileCopyUtils.copyToString(new InputStreamReader(inputStream));
        }
    }
}