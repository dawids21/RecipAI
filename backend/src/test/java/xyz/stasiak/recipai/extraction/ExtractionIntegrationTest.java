package xyz.stasiak.recipai.extraction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import xyz.stasiak.recipai.IntegrationTest;
import xyz.stasiak.recipai.LimitsEnabled;
import xyz.stasiak.recipai.TestAiConfiguration;
import xyz.stasiak.recipai.TestRestClients;
import xyz.stasiak.recipai.TestIdentities;
import xyz.stasiak.recipai.limits.LimitBalance;
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

@IntegrationTest
class ExtractionIntegrationTest {

    private static final ExtractedRecipe FIXTURE_RECIPE = new ExtractedRecipe(
            "Test Recipe",
            List.of(new ExtractedIngredient("Flour", new BigDecimal("2"), "cups", null)),
            List.of(new ExtractedInstruction("Mix and bake.")),
            4
    );

    private String owner;
    private String user1;
    private String user2;
    private String ownerSubject;
    private List<String> quotaSubjects;

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
        owner = TestIdentities.freshToken();
        user1 = TestIdentities.freshToken();
        user2 = TestIdentities.freshToken();
        ownerSubject = TestIdentities.emailOf(owner);
        quotaSubjects = List.of(ownerSubject, TestIdentities.emailOf(user1), TestIdentities.emailOf(user2));

        Mockito.reset(testAiConfiguration.getChatClient());
        Mockito.when(testAiConfiguration.getChatClient().prompt(any(Prompt.class)).call().entity(ExtractedRecipe.class))
                .thenReturn(FIXTURE_RECIPE);

        quotaSubjects.forEach(subject -> setLimitQuota(ExtractionService.EXTRACTION_RESOURCE, subject, 2));
    }

    /**
     * Upserts the quota: {@code limit_config} has no write API, so there is no business path to it.
     * Extraction's overrides are flow with no period, like the shipped default.
     */
    private void setLimitQuota(String resource, String subject, int maxValue) {
        jdbcClient.sql("""
                        INSERT INTO recipai.limit_config (id, resource, subject, kind, max_value, period)
                        VALUES (:id, :resource, :subject, 'FLOW', :maxValue, NULL)
                        ON CONFLICT (resource, subject) DO UPDATE SET max_value = EXCLUDED.max_value
                        """)
                .param("id", UUID.randomUUID())
                .param("resource", resource)
                .param("subject", subject)
                .param("maxValue", maxValue)
                .update();
    }

    private RestClient restClient() {
        return restClient(owner);
    }

    private RestClient restClient(String authToken) {
        return TestRestClients.forToken(port, authToken);
    }

    private ExtractedRecipe extractText(RestClient client, String text) {
        return client.post()
                .uri("/extract/text")
                .body(new ExtractTextRequest(text))
                .retrieve()
                .body(ExtractedRecipe.class);
    }

    private ExtractedRecipe extractImage(RestClient client, Resource resource, MediaType contentType) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", resource).contentType(contentType);

        return client.post()
                .uri("/extract/image")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(builder.build())
                .retrieve()
                .body(ExtractedRecipe.class);
    }

    private int usedFor(String subject) {
        return limitsFacade.getBalance(subject, ExtractionService.EXTRACTION_RESOURCE)
                .map(LimitBalance::used)
                .orElse(0);
    }

    private Map<String, Object> getBalance(RestClient client) {
        return client.get()
                .uri("/extract/balance")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    @Test
    void shouldReturn200AndMockedRecipeWhileBudgetRemains() {
        RestClient client = restClient();

        ExtractedRecipe recipe = extractText(client, "some recipe text");

        assertThat(recipe).isEqualTo(FIXTURE_RECIPE);
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

        assertThat(usedFor(TestIdentities.emailOf(owner))).isEqualTo(1);
    }

    @Test
    void shouldReturn400UnsupportedImageTypeForTextPlainAndLeaveUsedUnchanged() {
        RestClient client = restClient();

        Resource notAnImage = new ByteArrayResource("not an image".getBytes()) {
            @Override
            public String getFilename() {
                return "note.txt";
            }
        };

        try {
            extractImage(client, notAnImage, MediaType.TEXT_PLAIN);
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value());

            Map<String, Object> body = ex.getResponseBodyAs(new ParameterizedTypeReference<Map<String, Object>>() {
            });
            assertThat(body).isNotNull();
            assertThat(body.get("title")).isEqualTo("Unsupported Image Type");
        }

        assertThat(usedFor(TestIdentities.emailOf(owner))).isZero();
    }

    @Test
    void shouldConsumeOneUnitAndReturn200ForJpegImage() {
        RestClient client = restClient();

        ClassPathResource imageResource = new ClassPathResource("recipe_sources/kwestia_smaku.jpg");

        ExtractedRecipe recipe = extractImage(client, imageResource, MediaType.IMAGE_JPEG);

        assertThat(recipe).isEqualTo(FIXTURE_RECIPE);
        assertThat(usedFor(TestIdentities.emailOf(owner))).isEqualTo(1);
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

        ExtractedRecipe extractedRecipe = extractImage(restClient(), imageResource, MediaType.IMAGE_JPEG);

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
    void shouldReturnZeroUsageBeforeAnyExtractionAndOneAfter() {
        RestClient client = restClient();

        assertThat(getBalance(client).get("used")).isEqualTo(0);

        extractText(client, "recipe 1");

        assertThat(getBalance(client).get("used")).isEqualTo(1);
    }

    @Test
    void shouldReturnNullResetsInSecondsUnderSeededFlowWithNoPeriodDefault() {
        RestClient client = restClient();

        extractText(client, "recipe 1");

        // Jackson is configured with default-property-inclusion: non_null, so a null field is absent
        // rather than serialised as null, while periodStart rides along on every live balance.
        assertThat(getBalance(client)).doesNotContainKey("resetsInSeconds").containsKey("periodStart");
    }

    @Test
    void shouldReturnExhaustedBalanceAfterBudgetIsSpent() {
        RestClient client = restClient();

        extractText(client, "recipe 1");
        extractText(client, "recipe 2");

        assertThat(getBalance(client).get("used")).isEqualTo(2);
    }

    private String loadResourceContent(ClassPathResource resource) throws IOException {
        try (InputStream inputStream = resource.getInputStream()) {
            return FileCopyUtils.copyToString(new InputStreamReader(inputStream));
        }
    }

    @Nested
    @LimitsEnabled
    class LimitsEnforced {

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
        void shouldAdmitNextCallWithNoRestartAfterRaisingQuota() {
            RestClient client = restClient();

            extractText(client, "recipe 1");
            extractText(client, "recipe 2");
            try {
                extractText(client, "recipe 3");
                fail("Should have thrown exception");
            } catch (RestClientResponseException ex) {
                assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
            }

            setLimitQuota(ExtractionService.EXTRACTION_RESOURCE, ownerSubject, 5);

            ExtractedRecipe recipe = extractText(client, "recipe 4");

            assertThat(recipe).isEqualTo(FIXTURE_RECIPE);
            assertThat(usedFor(TestIdentities.emailOf(owner))).isEqualTo(3);
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

            assertThat(usedFor(TestIdentities.emailOf(owner))).isEqualTo(1);

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
        void shouldGiveUser1AndUser2IndependentBudgets() {
            RestClient user1Client = restClient(user1);
            RestClient user2Client = restClient(user2);

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

            assertThat(usedFor(TestIdentities.emailOf(user1))).isEqualTo(2);
            assertThat(usedFor(TestIdentities.emailOf(user2))).isEqualTo(1);
        }
    }
}
