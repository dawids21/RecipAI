package xyz.stasiak.recipai.planning;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import xyz.stasiak.recipai.TestSecurityConfiguration;
import xyz.stasiak.recipai.TestcontainersConfiguration;
import xyz.stasiak.recipai.planning.dto.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@SuppressWarnings("ResultOfMethodCallIgnored")
@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MealPlanIntegrationTest {

    @LocalServerPort
    private int port;

    private RestClient restClient() {
        return restClient(TestSecurityConfiguration.AUTH_TOKEN);
    }

    private RestClient restClient(String authToken) {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader("Authorization", "Bearer " + authToken)
                .build();
    }

    @AfterEach
    void cleanup() {
        for (String token : List.of(
                TestSecurityConfiguration.AUTH_TOKEN,
                TestSecurityConfiguration.AUTH_TOKEN_USER_1,
                TestSecurityConfiguration.AUTH_TOKEN_USER_2)) {
            RestClient client = restClient(token);
            List<MealPlanDto> plans = getAllMealPlans(client);
            for (MealPlanDto plan : plans) {
                try {
                    deleteMealPlan(client, plan.id());
                } catch (RestClientResponseException ignored) {
                    // EDITOR cannot delete, ignore
                }
            }
        }
    }

    private MealPlanDto createMealPlan(RestClient client, String name, String color) {
        CreateMealPlanRequest request = new CreateMealPlanRequest(name, color);
        return client
                .post()
                .uri("/meal-plans")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(MealPlanDto.class);
    }

    private List<MealPlanDto> getAllMealPlans(RestClient client) {
        return client
                .get()
                .uri("/meal-plans")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private MealPlanDto updateMealPlan(RestClient client, UUID id, String name, String color) {
        UpdateMealPlanRequest request = new UpdateMealPlanRequest(name, color);
        return client
                .put()
                .uri("/meal-plans/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(MealPlanDto.class);
    }

    private void deleteMealPlan(RestClient client, UUID id) {
        client
                .delete()
                .uri("/meal-plans/" + id)
                .retrieve()
                .toBodilessEntity();
    }

    private MealPlanEntryDto createEntry(RestClient client, UUID planId, CreateMealPlanEntryRequest request) {
        return client
                .post()
                .uri("/meal-plans/" + planId + "/entries")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(MealPlanEntryDto.class);
    }

    private MealPlanEntryDto updateEntry(RestClient client, UUID planId, Long entryId, UpdateMealPlanEntryRequest request) {
        return client
                .put()
                .uri("/meal-plans/" + planId + "/entries/" + entryId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(MealPlanEntryDto.class);
    }

    private void deleteEntry(RestClient client, UUID planId, Long entryId) {
        client
                .delete()
                .uri("/meal-plans/" + planId + "/entries/" + entryId)
                .retrieve()
                .toBodilessEntity();
    }

    @Test
    void shouldCreateAndListMealPlans() {
        RestClient client = restClient();

        MealPlanDto created = createMealPlan(client, "Weekly Plan", "#FF5733");

        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("Weekly Plan");
        assertThat(created.color()).isEqualTo("#FF5733");
        assertThat(created.role()).isEqualTo(UserRole.OWNER);
        assertThat(created.createdAt()).isNotNull();

        List<MealPlanDto> plans = getAllMealPlans(client);
        assertThat(plans).extracting(MealPlanDto::id).contains(created.id());
    }

    @Test
    void shouldRejectCreateWithBlankName() {
        RestClient client = restClient();

        try {
            createMealPlan(client, "", "#FF5733");
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        }
    }

    @Test
    void shouldRejectCreateWithInvalidColor() {
        RestClient client = restClient();

        try {
            createMealPlan(client, "Plan", "invalid");
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        }
    }

    @Test
    void shouldUpdateMealPlan() {
        RestClient client = restClient();

        MealPlanDto created = createMealPlan(client, "Original", "#FF5733");
        MealPlanDto updated = updateMealPlan(client, created.id(), "Updated", "#00FF00");

        assertThat(updated.name()).isEqualTo("Updated");
        assertThat(updated.color()).isEqualTo("#00FF00");
    }

    @Test
    void shouldDeleteMealPlan() {
        RestClient client = restClient();

        MealPlanDto created = createMealPlan(client, "To Delete", "#FF5733");
        deleteMealPlan(client, created.id());

        List<MealPlanDto> plans = getAllMealPlans(client);
        assertThat(plans).extracting(MealPlanDto::id).doesNotContain(created.id());
    }

    @Test
    void shouldIsolateUserPlans() {
        RestClient client1 = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient client2 = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        MealPlanDto plan1 = createMealPlan(client1, "User1 Plan", "#FF0000");
        MealPlanDto plan2 = createMealPlan(client2, "User2 Plan", "#0000FF");

        List<MealPlanDto> user1Plans = getAllMealPlans(client1);
        List<MealPlanDto> user2Plans = getAllMealPlans(client2);

        assertThat(user1Plans).extracting(MealPlanDto::id).contains(plan1.id()).doesNotContain(plan2.id());
        assertThat(user2Plans).extracting(MealPlanDto::id).contains(plan2.id()).doesNotContain(plan1.id());
    }

    @Test
    void shouldPreventCrossUserAccess() {
        RestClient client1 = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient client2 = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        MealPlanDto plan = createMealPlan(client1, "Private Plan", "#FF0000");

        try {
            updateMealPlan(client2, plan.id(), "Hacked", "#000000");
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.FORBIDDEN.value());
        }

        try {
            deleteMealPlan(client2, plan.id());
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.FORBIDDEN.value());
        }
    }

    @Test
    void shouldReturn404ForNonexistentPlan() {
        RestClient client = restClient();
        UUID nonexistent = UUID.randomUUID();

        try {
            updateMealPlan(client, nonexistent, "Name", "#FF5733");
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
        }

        try {
            deleteMealPlan(client, nonexistent);
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
        }
    }

    @Test
    void shouldEnforce10PlanLimit() {
        RestClient client = restClient();

        for (int i = 0; i < 10; i++) {
            createMealPlan(client, "Plan " + i, "#FF5733");
        }

        try {
            createMealPlan(client, "Plan 11", "#FF5733");
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.CONFLICT.value());
        }
    }

    @Test
    void shouldCreateEntryWithRecipeId() {
        RestClient client = restClient();
        MealPlanDto plan = createMealPlan(client, "Entry Test Plan", "#FF5733");
        UUID recipeId = UUID.randomUUID();

        CreateMealPlanEntryRequest request = new CreateMealPlanEntryRequest(
                LocalDate.of(2026, 1, 29), recipeId, null, 4
        );

        MealPlanEntryDto entry = createEntry(client, plan.id(), request);

        assertThat(entry.id()).isNotNull();
        assertThat(entry.planId()).isEqualTo(plan.id());
        assertThat(entry.date()).isEqualTo(LocalDate.of(2026, 1, 29));
        assertThat(entry.recipeId()).isEqualTo(recipeId);
        assertThat(entry.placeholderText()).isNull();
        assertThat(entry.servingSize()).isEqualTo(4);
        assertThat(entry.createdAt()).isNotNull();
    }

    @Test
    void shouldCreateEntryWithPlaceholderText() {
        RestClient client = restClient();
        MealPlanDto plan = createMealPlan(client, "Placeholder Test", "#FF5733");

        CreateMealPlanEntryRequest request = new CreateMealPlanEntryRequest(
                LocalDate.of(2026, 1, 30), null, "Leftovers", null
        );

        MealPlanEntryDto entry = createEntry(client, plan.id(), request);

        assertThat(entry.recipeId()).isNull();
        assertThat(entry.placeholderText()).isEqualTo("Leftovers");
        assertThat(entry.servingSize()).isNull();
    }

    @Test
    void shouldUpdateEntry() {
        RestClient client = restClient();
        MealPlanDto plan = createMealPlan(client, "Update Entry Test", "#FF5733");

        CreateMealPlanEntryRequest createReq = new CreateMealPlanEntryRequest(
                LocalDate.of(2026, 1, 29), null, "Original", null
        );
        MealPlanEntryDto entry = createEntry(client, plan.id(), createReq);

        UpdateMealPlanEntryRequest updateReq = new UpdateMealPlanEntryRequest(
                LocalDate.of(2026, 2, 1), null, "Updated", null
        );
        MealPlanEntryDto updated = updateEntry(client, plan.id(), entry.id(), updateReq);

        assertThat(updated.date()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(updated.placeholderText()).isEqualTo("Updated");
    }

    @Test
    void shouldDeleteEntry() {
        RestClient client = restClient();
        MealPlanDto plan = createMealPlan(client, "Delete Entry Test", "#FF5733");

        CreateMealPlanEntryRequest createReq = new CreateMealPlanEntryRequest(
                LocalDate.of(2026, 1, 29), null, "To Delete", null
        );
        MealPlanEntryDto entry = createEntry(client, plan.id(), createReq);

        deleteEntry(client, plan.id(), entry.id());

        try {
            deleteEntry(client, plan.id(), entry.id());
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
        }
    }

    @Test
    void shouldReturn404ForNonexistentEntry() {
        RestClient client = restClient();
        MealPlanDto plan = createMealPlan(client, "Nonexistent Entry Test", "#FF5733");

        Long nonexistent = 123456789L;

        try {
            UpdateMealPlanEntryRequest req = new UpdateMealPlanEntryRequest(
                    LocalDate.of(2026, 1, 29), null, "Test", null
            );
            updateEntry(client, plan.id(), nonexistent, req);
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
        }
    }

    @Test
    void shouldReturn404ForEntryBelongingToDifferentPlan() {
        RestClient client = restClient();
        MealPlanDto plan1 = createMealPlan(client, "Plan A", "#FF5733");
        MealPlanDto plan2 = createMealPlan(client, "Plan B", "#00FF00");

        CreateMealPlanEntryRequest createReq = new CreateMealPlanEntryRequest(
                LocalDate.of(2026, 1, 29), null, "Entry in Plan A", null
        );
        MealPlanEntryDto entry = createEntry(client, plan1.id(), createReq);

        try {
            UpdateMealPlanEntryRequest updateReq = new UpdateMealPlanEntryRequest(
                    LocalDate.of(2026, 1, 29), null, "Moved", null
            );
            updateEntry(client, plan2.id(), entry.id(), updateReq);
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
        }
    }

    @Test
    void shouldRejectEntryWithBothRecipeIdAndPlaceholder() {
        RestClient client = restClient();
        MealPlanDto plan = createMealPlan(client, "Both Fields Test", "#FF5733");

        CreateMealPlanEntryRequest request = new CreateMealPlanEntryRequest(
                LocalDate.of(2026, 1, 29), UUID.randomUUID(), "Also placeholder", 4
        );

        try {
            createEntry(client, plan.id(), request);
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        }
    }

    @Test
    void shouldRejectEntryWithNeitherRecipeIdNorPlaceholder() {
        RestClient client = restClient();
        MealPlanDto plan = createMealPlan(client, "Neither Field Test", "#FF5733");

        CreateMealPlanEntryRequest request = new CreateMealPlanEntryRequest(
                LocalDate.of(2026, 1, 29), null, null, null
        );

        try {
            createEntry(client, plan.id(), request);
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        }
    }

    @Test
    void shouldRejectEntryWithRecipeIdButNoServingSize() {
        RestClient client = restClient();
        MealPlanDto plan = createMealPlan(client, "No Serving Size Test", "#FF5733");

        CreateMealPlanEntryRequest request = new CreateMealPlanEntryRequest(
                LocalDate.of(2026, 1, 29), UUID.randomUUID(), null, null
        );

        try {
            createEntry(client, plan.id(), request);
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        }
    }

    @Test
    void shouldPreventCrossUserEntryAccess() {
        RestClient client1 = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient client2 = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        MealPlanDto plan = createMealPlan(client1, "Private Plan Entries", "#FF0000");

        CreateMealPlanEntryRequest request = new CreateMealPlanEntryRequest(
                LocalDate.of(2026, 1, 29), null, "Private Entry", null
        );
        MealPlanEntryDto entry = createEntry(client1, plan.id(), request);

        try {
            UpdateMealPlanEntryRequest updateReq = new UpdateMealPlanEntryRequest(
                    LocalDate.of(2026, 1, 29), null, "Hacked", null
            );
            updateEntry(client2, plan.id(), entry.id(), updateReq);
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.FORBIDDEN.value());
        }
    }
}
