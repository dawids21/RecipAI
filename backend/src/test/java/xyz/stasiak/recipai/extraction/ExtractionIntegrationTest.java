package xyz.stasiak.recipai.extraction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import xyz.stasiak.recipai.TestAiConfiguration;
import xyz.stasiak.recipai.TestSecurityConfiguration;
import xyz.stasiak.recipai.TestcontainersConfiguration;
import xyz.stasiak.recipai.limits.LimitUsageDetails;
import xyz.stasiak.recipai.limits.LimitsFacade;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;

@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class, TestAiConfiguration.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.ai.google.genai.api-key=test-key", "recipai.limits.enabled=true"})
class ExtractionIntegrationTest {

    private static final ExtractedRecipe FIXTURE_RECIPE = new ExtractedRecipe(
            "Test Recipe",
            List.of(new ExtractedIngredient("Flour", new BigDecimal("2"), "cups", null)),
            List.of(new ExtractedInstruction("Mix and bake.")),
            4
    );

    @LocalServerPort
    private int port;

    @Autowired
    private TestAiConfiguration testAiConfiguration;

    @Autowired
    private LimitsFacade limitsFacade;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void setUp() {
        Mockito.reset(testAiConfiguration.getChatClient());
        Mockito.when(testAiConfiguration.getChatClient().prompt(any(Prompt.class)).call().entity(ExtractedRecipe.class))
                .thenReturn(FIXTURE_RECIPE);
    }

    @AfterEach
    void tearDown() {
        jdbcClient.sql("DELETE FROM recipai.limit_usage WHERE resource = 'EXTRACTION'").update();
        jdbcClient.sql("UPDATE recipai.limit_config SET max_value = 2 WHERE resource = 'EXTRACTION' AND subject IS NULL")
                .update();
    }

    private RestClient restClient() {
        return restClient(TestSecurityConfiguration.AUTH_TOKEN);
    }

    private RestClient restClient(String authToken) {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader("Authorization", "Bearer " + authToken)
                .build();
    }

    private ExtractedRecipe extractText(RestClient client, String text) {
        return client.post()
                .uri("/extract/text")
                .body(new ExtractTextRequest(text))
                .retrieve()
                .body(ExtractedRecipe.class);
    }

    private int usedFor(String subject) {
        return limitsFacade.currentUsage(subject, ExtractionService.EXTRACTION_RESOURCE)
                .map(LimitUsageDetails::used)
                .orElse(0);
    }

    @Test
    void shouldReturn200AndMockedRecipeWhileBudgetRemains() {
        RestClient client = restClient();

        ExtractedRecipe recipe = extractText(client, "some recipe text");

        assertThat(recipe).isEqualTo(FIXTURE_RECIPE);
    }

    @Test
    void shouldReturn429WithProblemDetailsOnThirdCallAtSeededLimit() {
        RestClient client = restClient();

        extractText(client, "recipe 1");
        extractText(client, "recipe 2");

        try {
            extractText(client, "recipe 3");
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
            assertThat(ex.getResponseHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

            Map<String, Object> body = ex.getResponseBodyAs(new ParameterizedTypeReference<Map<String, Object>>() {
            });
            assertThat(body).isNotNull();
            assertThat(body.get("resource")).isEqualTo("EXTRACTION");
            assertThat(body.get("kind")).isEqualTo("FLOW");
            assertThat(body.get("limit")).isEqualTo(2);
            assertThat(body.get("used")).isEqualTo(2);
        }
    }

    @Test
    void shouldReturn429WithNoRetryAfterHeaderOrBodyKey() {
        RestClient client = restClient();

        extractText(client, "recipe 1");
        extractText(client, "recipe 2");

        try {
            extractText(client, "recipe 3");
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getResponseHeaders().get("Retry-After")).isNull();

            Map<String, Object> body = ex.getResponseBodyAs(new ParameterizedTypeReference<Map<String, Object>>() {
            });
            assertThat(body).isNotNull();
            assertThat(body).doesNotContainKey("retryAfterSeconds");
        }
    }

    @Test
    void shouldAdmitNextCallWithNoRestartAfterRaisingMaxValueBySql() {
        RestClient client = restClient();

        extractText(client, "recipe 1");
        extractText(client, "recipe 2");
        try {
            extractText(client, "recipe 3");
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        }

        jdbcClient.sql("UPDATE recipai.limit_config SET max_value = 5 WHERE resource = 'EXTRACTION' AND subject IS NULL")
                .update();

        ExtractedRecipe recipe = extractText(client, "recipe 4");

        assertThat(recipe).isEqualTo(FIXTURE_RECIPE);
        assertThat(usedFor("user@example.com")).isEqualTo(3);
    }

    @Test
    void shouldConsumeBudgetWhenChatClientThrows() {
        RestClient client = restClient();

        Mockito.when(testAiConfiguration.getChatClient().prompt(any(Prompt.class)).call().entity(ExtractedRecipe.class))
                .thenThrow(new RuntimeException("AI provider failure"));

        try {
            extractText(client, "recipe 1");
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }

        assertThat(usedFor("user@example.com")).isEqualTo(1);

        Mockito.reset(testAiConfiguration.getChatClient());
        Mockito.when(testAiConfiguration.getChatClient().prompt(any(Prompt.class)).call().entity(ExtractedRecipe.class))
                .thenReturn(FIXTURE_RECIPE);

        extractText(client, "recipe 2");

        try {
            extractText(client, "recipe 3");
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        }
    }

    @Test
    void shouldReturn500ExtractionFailedWhenChatClientReturnsNullAndStillConsumeTheUnit() {
        RestClient client = restClient();

        Mockito.reset(testAiConfiguration.getChatClient());

        try {
            extractText(client, "recipe 1");
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());

            Map<String, Object> body = ex.getResponseBodyAs(new ParameterizedTypeReference<Map<String, Object>>() {
            });
            assertThat(body).isNotNull();
            assertThat(body.get("title")).isEqualTo("Extraction Failed");
        }

        assertThat(usedFor("user@example.com")).isEqualTo(1);
    }

    @Test
    void shouldReturn400UnsupportedImageTypeForTextPlainAndLeaveUsedUnchanged() {
        RestClient client = restClient();

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", "not an image".getBytes())
                .filename("note.txt")
                .contentType(MediaType.TEXT_PLAIN);

        try {
            client.post()
                    .uri("/extract/image")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(builder.build())
                    .retrieve()
                    .body(ExtractedRecipe.class);
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value());

            Map<String, Object> body = ex.getResponseBodyAs(new ParameterizedTypeReference<Map<String, Object>>() {
            });
            assertThat(body).isNotNull();
            assertThat(body.get("title")).isEqualTo("Unsupported Image Type");
        }

        assertThat(usedFor("user@example.com")).isZero();
    }

    @Test
    void shouldConsumeOneUnitAndReturn200ForJpegImage() {
        RestClient client = restClient();

        ClassPathResource imageResource = new ClassPathResource("recipe_sources/kwestia_smaku.jpg");
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", imageResource).contentType(MediaType.IMAGE_JPEG);

        ExtractedRecipe recipe = client.post()
                .uri("/extract/image")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(builder.build())
                .retrieve()
                .body(ExtractedRecipe.class);

        assertThat(recipe).isEqualTo(FIXTURE_RECIPE);
        assertThat(usedFor("user@example.com")).isEqualTo(1);
    }

    @Test
    void shouldGiveUser1AndUser2IndependentBudgets() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        extractText(user1Client, "recipe 1");
        extractText(user1Client, "recipe 2");
        try {
            extractText(user1Client, "recipe 3");
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        }

        ExtractedRecipe recipe = extractText(user2Client, "recipe 1");
        assertThat(recipe).isEqualTo(FIXTURE_RECIPE);

        assertThat(usedFor("user1@example.com")).isEqualTo(2);
        assertThat(usedFor("user2@example.com")).isEqualTo(1);
    }

    @Test
    void shouldRejectRequestWithNoAuthorizationHeader() {
        RestClient client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();

        try {
            client.post()
                    .uri("/extract/text")
                    .body(new ExtractTextRequest("recipe " + UUID.randomUUID()))
                    .retrieve()
                    .body(ExtractedRecipe.class);
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().is4xxClientError()).isTrue();
            assertThat(ex.getStatusCode().value()).isNotEqualTo(HttpStatus.OK.value());
        }
    }

    @Test
    @Disabled("Calls the real AI provider - the mocked ChatClient must be removed to run it")
    void shouldExtractRecipeFromText() throws Exception {
        ClassPathResource textResource = new ClassPathResource("recipe_sources/kwestia_smaku.txt");
        String content = loadResourceContent(textResource);

        ExtractTextRequest request = new ExtractTextRequest(content);

        ExtractedRecipe extractedRecipe = restClient()
                .post()
                .uri("/extract/text")
                .body(request)
                .retrieve()
                .body(ExtractedRecipe.class);

        assertThat(extractedRecipe).isNotNull();
        assertThat(extractedRecipe.name()).isNotNull();
        assertThat(extractedRecipe.ingredients()).isNotEmpty();
        assertThat(extractedRecipe.instructions()).isNotEmpty();
        assertThat(extractedRecipe.servingSize())
                .isNotNull()
                .isPositive()
                .isLessThanOrEqualTo(100);
    }

    @Test
    @Disabled("Calls the real AI provider - the mocked ChatClient must be removed to run it")
    void shouldExtractRecipeFromImage() {
        ClassPathResource imageResource = new ClassPathResource("recipe_sources/kwestia_smaku.jpg");

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", imageResource);

        ExtractedRecipe extractedRecipe = restClient()
                .post()
                .uri("/extract/image")
                .body(parts)
                .retrieve()
                .body(ExtractedRecipe.class);

        assertThat(extractedRecipe).isNotNull();
        assertThat(extractedRecipe.name()).isNotNull();
        assertThat(extractedRecipe.ingredients()).isNotEmpty();
        assertThat(extractedRecipe.instructions()).isNotEmpty();
        assertThat(extractedRecipe.servingSize())
                .isNotNull()
                .isPositive()
                .isLessThanOrEqualTo(100);
    }

    private String loadResourceContent(ClassPathResource resource) throws IOException {
        try (InputStream inputStream = resource.getInputStream()) {
            return FileCopyUtils.copyToString(new InputStreamReader(inputStream));
        }
    }
}
