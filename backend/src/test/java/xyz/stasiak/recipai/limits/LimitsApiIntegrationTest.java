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
import xyz.stasiak.recipai.TestSecurityConfiguration;
import xyz.stasiak.recipai.TestcontainersConfiguration;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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

    /**
     * Upserts the quota: {@code limit_config} has no write API, so there is no business path to it.
     */
    private void setLimitQuota(String resource, String subject, int maxValue) {
        jdbcClient.sql("""
                        INSERT INTO recipai.limit_config (id, resource, subject, kind, max_value, period)
                        VALUES (:id, :resource, :subject, 'STOCK', :maxValue, NULL)
                        ON CONFLICT (resource, subject) DO UPDATE SET max_value = EXCLUDED.max_value
                        """)
                .param("id", UUID.randomUUID())
                .param("resource", resource)
                .param("subject", subject)
                .param("maxValue", maxValue)
                .update();
    }

    @Test
    void shouldReturnQuotasResolvedForCallerEmail() {
        List<Map<String, Object>> quotas = getLimits(restClient());

        assertThat(quotas).extracting(quota -> quota.get("resource"))
                .contains("RECIPE", "RECIPES_COLLECTION", "SHOPPING_LIST", "MEAL_PLAN", "EXTRACTION", "SHOPPING_LIST_ITEM");

        Map<String, Object> recipeQuota = quotas.stream()
                .filter(quota -> "RECIPE".equals(quota.get("resource")))
                .findFirst().orElseThrow();
        assertThat(recipeQuota.get("kind")).isEqualTo("STOCK");
        assertThat(recipeQuota.get("limit")).isEqualTo(5);
    }

    @Test
    void shouldReflectSubjectOverrideRatherThanDefault() {
        setLimitQuota("RECIPE", SUBJECT, 42);

        List<Map<String, Object>> quotas = getLimits(restClient());

        Map<String, Object> recipeQuota = quotas.stream()
                .filter(quota -> "RECIPE".equals(quota.get("resource")))
                .findFirst().orElseThrow();
        assertThat(recipeQuota.get("limit")).isEqualTo(42);
    }

    @Nested
    @TestPropertySource(properties = "recipai.limits.enabled=false")
    class Disabled {

        @Test
        void shouldReturnEmptyArrayWhenLimitsAreDisabled() {
            List<Map<String, Object>> quotas = getLimits(restClient());

            assertThat(quotas).isEmpty();
        }
    }
}
