package xyz.stasiak.recipai.limits;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import xyz.stasiak.recipai.TestSecurityConfiguration;
import xyz.stasiak.recipai.TestcontainersConfiguration;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "recipai.limits.enabled=true")
class LimitsApiIntegrationTest {

    private static final String SUBJECT = "user@example.com";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcClient jdbcClient;

    @AfterEach
    void tearDown() {
        jdbcClient.sql("DELETE FROM recipai.limit_config WHERE resource = 'RECIPE' AND subject IS NOT NULL").update();
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

    private List<Map<String, Object>> getLimits(RestClient client) {
        return client.get()
                .uri("/limits")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private void seedOverride(String resource, String subject, int maxValue) {
        jdbcClient.sql("""
                        INSERT INTO recipai.limit_config (id, resource, subject, kind, max_value, period)
                        VALUES (:id, :resource, :subject, 'STOCK', :maxValue, NULL)
                        """)
                .param("id", UUID.randomUUID())
                .param("resource", resource)
                .param("subject", subject)
                .param("maxValue", maxValue)
                .update();
    }

    @Test
    void shouldReturnCapsResolvedForCallerEmail() {
        List<Map<String, Object>> caps = getLimits(restClient());

        assertThat(caps).extracting(cap -> cap.get("resource"))
                .contains("RECIPE", "RECIPES_COLLECTION", "SHOPPING_LIST", "MEAL_PLAN", "EXTRACTION", "SHOPPING_LIST_ITEM");

        Map<String, Object> recipeCap = caps.stream()
                .filter(cap -> "RECIPE".equals(cap.get("resource")))
                .findFirst().orElseThrow();
        assertThat(recipeCap.get("kind")).isEqualTo("STOCK");
        assertThat(recipeCap.get("limit")).isEqualTo(5);
    }

    @Test
    void shouldReflectSubjectOverrideRatherThanDefault() {
        seedOverride("RECIPE", SUBJECT, 42);

        List<Map<String, Object>> caps = getLimits(restClient());

        Map<String, Object> recipeCap = caps.stream()
                .filter(cap -> "RECIPE".equals(cap.get("resource")))
                .findFirst().orElseThrow();
        assertThat(recipeCap.get("limit")).isEqualTo(42);
    }

    @Test
    void shouldReturn401WithoutBearerToken() {
        RestClient client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();

        try {
            client.get().uri("/limits").retrieve().body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
            });
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(401);
        }
    }

    @Nested
    @TestPropertySource(properties = "recipai.limits.enabled=false")
    class Disabled {

        @Test
        void shouldReturnEmptyArrayWhenLimitsAreDisabled() {
            List<Map<String, Object>> caps = getLimits(restClient());

            assertThat(caps).isEmpty();
        }
    }
}
