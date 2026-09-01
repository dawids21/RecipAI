package xyz.stasiak.recipai.limits;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;
import xyz.stasiak.recipai.IntegrationTest;
import xyz.stasiak.recipai.LimitsEnabled;
import xyz.stasiak.recipai.TestRestClients;
import xyz.stasiak.recipai.TestIdentities;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class LimitsApiIntegrationTest {

    private String owner;

    @LocalServerPort
    private int port;

    @BeforeEach
    void freshUser() {
        owner = TestIdentities.freshToken();
    }

    private RestClient restClient() {
        return restClient(owner);
    }

    private RestClient restClient(String authToken) {
        return TestRestClients.forToken(port, authToken);
    }

    private List<Map<String, Object>> getLimits(RestClient client) {
        return client.get()
                .uri("/limits")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    @Test
    void shouldReturnEmptyArrayWhenLimitsAreDisabled() {
        List<Map<String, Object>> quotas = getLimits(restClient());

        assertThat(quotas).isEmpty();
    }

    @Nested
    @LimitsEnabled
    class LimitsEnforced {

        private String ownerSubject;

        @Autowired
        private JdbcClient jdbcClient;

        @BeforeEach
        void setSubject() {
            ownerSubject = TestIdentities.emailOf(owner);
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
            setLimitQuota("RECIPE", ownerSubject, 42);

            List<Map<String, Object>> quotas = getLimits(restClient());

            Map<String, Object> recipeQuota = quotas.stream()
                    .filter(quota -> "RECIPE".equals(quota.get("resource")))
                    .findFirst().orElseThrow();
            assertThat(recipeQuota.get("limit")).isEqualTo(42);
        }
    }
}
