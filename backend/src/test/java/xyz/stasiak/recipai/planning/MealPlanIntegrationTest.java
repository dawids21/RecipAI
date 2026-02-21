package xyz.stasiak.recipai.planning;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
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
import xyz.stasiak.recipai.planning.dto.SharedUserDto;
import xyz.stasiak.recipai.recipes.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MealPlanIntegrationTest {

    @LocalServerPort
    private int port;

    @Value("${recipai.meal-plan.max-owned-plans}")
    private int maxOwnedPlans;

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

    private RecipeDetailsDto createRecipe(RestClient client, String name) {
        RecipeData data = new RecipeData(
                List.of(new Ingredient("flour", "300", "g")),
                List.of(new Instruction("Mix")),
                null,
                1
        );
        CreateRecipeRequest request = new CreateRecipeRequest(name, data, null, List.of());
        return client
                .post()
                .uri("/recipes")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(RecipeDetailsDto.class);
    }

    private void deleteRecipe(RestClient client, UUID id) {
        client
                .delete()
                .uri("/recipes/" + id)
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

    private List<SharedUserDto> getSharedUsers(RestClient client, UUID planId) {
        return client
                .get()
                .uri("/meal-plans/" + planId + "/users")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private void shareMealPlan(RestClient client, UUID planId, String email) {
        ShareMealPlanRequest request = new ShareMealPlanRequest(email);
        client
                .post()
                .uri("/meal-plans/" + planId + "/share")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    private void unshareMealPlan(RestClient client, UUID planId, String email) {
        UnshareMealPlanRequest request = new UnshareMealPlanRequest(email);
        client
                .post()
                .uri("/meal-plans/" + planId + "/unshare")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    private GeneratedShoppingListResponse generateShoppingListItems(
            RestClient client, List<UUID> planIds, List<LocalDate> dates) {
        GenerateShoppingListItemsRequest request = new GenerateShoppingListItemsRequest(planIds, dates);
        return client
                .post()
                .uri("/meal-plans/generate-shopping-list")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(GeneratedShoppingListResponse.class);
    }

    private Map<LocalDate, List<MealPlanCalendarViewDto>> getCalendarView(
            RestClient client, LocalDate startDate, LocalDate endDate, String planIds) {
        String uri = "/meal-plans/calendar?startDate=" + startDate + "&endDate=" + endDate;
        if (planIds != null) {
            uri += "&planIds=" + planIds;
        }
        return client
                .get()
                .uri(uri)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
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
    void shouldEnforcePlanLimit() {
        RestClient client = restClient();

        for (int i = 0; i < maxOwnedPlans; i++) {
            createMealPlan(client, "Plan " + i, "#FF5733");
        }

        try {
            createMealPlan(client, "Plan over limit", "#FF5733");
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
    void shouldRejectEntryWithPlaceholderTextAndServingSize() {
        RestClient client = restClient();
        MealPlanDto plan = createMealPlan(client, "Placeholder With Serving Size Test", "#FF5733");

        CreateMealPlanEntryRequest request = new CreateMealPlanEntryRequest(
                LocalDate.of(2026, 1, 29), null, "Leftovers", 4
        );

        try {
            createEntry(client, plan.id(), request);
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        }
    }

    @Test
    void shouldConvertEntryToPlaceholderWhenRecipeIsDeleted() {
        RestClient client = restClient();

        RecipeDetailsDto recipe = createRecipe(client, "Test Recipe");

        MealPlanDto plan = createMealPlan(client, "Recipe Delete Test", "#FF5733");
        CreateMealPlanEntryRequest entryRequest = new CreateMealPlanEntryRequest(
                LocalDate.of(2026, 1, 29), recipe.id(), null, 4
        );
        MealPlanEntryDto entry = createEntry(client, plan.id(), entryRequest);

        assertThat(entry.recipeId()).isEqualTo(recipe.id());
        assertThat(entry.servingSize()).isEqualTo(4);
        assertThat(entry.placeholderText()).isNull();

        deleteRecipe(client, recipe.id());

        Map<LocalDate, List<MealPlanCalendarViewDto>> calendar = getCalendarView(
                client, LocalDate.of(2026, 1, 29), LocalDate.of(2026, 1, 29), plan.id().toString());

        assertThat(calendar).hasSize(1);
        List<MealPlanCalendarViewDto> entries = calendar.get(LocalDate.of(2026, 1, 29));
        assertThat(entries).hasSize(1);

        MealPlanCalendarViewDto calendarEntry = entries.getFirst();
        assertThat(calendarEntry.recipeId()).isNull();
        assertThat(calendarEntry.recipeName()).isNull();
        assertThat(calendarEntry.placeholderText()).isEqualTo(recipe.name());
        assertThat(calendarEntry.hasRecipeAccess()).isTrue();
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

    @Test
    void shouldListSharedUsersForOwnPlan() {
        RestClient client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        MealPlanDto plan = createMealPlan(client, "Shared Users Test", "#FF5733");

        List<SharedUserDto> users = getSharedUsers(client, plan.id());

        assertThat(users).hasSize(1);
        assertThat(users.getFirst().email()).isEqualTo("user1@example.com");
        assertThat(users.getFirst().role()).isEqualTo(UserRole.OWNER);
    }

    @Test
    void shouldShareMealPlanWithAnotherUser() {
        RestClient client1 = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient client2 = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        MealPlanDto plan = createMealPlan(client1, "Shared Plan", "#FF5733");

        shareMealPlan(client1, plan.id(), "user2@example.com");

        List<SharedUserDto> users = getSharedUsers(client1, plan.id());
        assertThat(users).hasSize(2);
        assertThat(users.get(0).email()).isEqualTo("user1@example.com");
        assertThat(users.get(0).role()).isEqualTo(UserRole.OWNER);
        assertThat(users.get(1).email()).isEqualTo("user2@example.com");
        assertThat(users.get(1).role()).isEqualTo(UserRole.EDITOR);

        List<MealPlanDto> user2Plans = getAllMealPlans(client2);
        assertThat(user2Plans).extracting(MealPlanDto::id).contains(plan.id());
        assertThat(user2Plans.stream().filter(p -> p.id().equals(plan.id())).findFirst().orElseThrow().role())
                .isEqualTo(UserRole.EDITOR);
    }

    @Test
    void shouldBeIdempotentWhenSharingTwice() {
        RestClient client1 = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);

        MealPlanDto plan = createMealPlan(client1, "Idempotent Share Test", "#FF5733");

        shareMealPlan(client1, plan.id(), "user2@example.com");
        List<SharedUserDto> users1 = getSharedUsers(client1, plan.id());

        shareMealPlan(client1, plan.id(), "user2@example.com");
        List<SharedUserDto> users2 = getSharedUsers(client1, plan.id());

        assertThat(users1).hasSize(2);
        assertThat(users2).hasSize(2);
        assertThat(users1).isEqualTo(users2);
    }

    @Test
    void shouldAllowEditorToEditSharedPlan() {
        RestClient client1 = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient client2 = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        MealPlanDto plan = createMealPlan(client1, "Editor Test", "#FF5733");
        shareMealPlan(client1, plan.id(), "user2@example.com");

        MealPlanDto updated = updateMealPlan(client2, plan.id(), "Updated by Editor", "#00FF00");

        assertThat(updated.name()).isEqualTo("Updated by Editor");
        assertThat(updated.color()).isEqualTo("#00FF00");
        assertThat(updated.role()).isEqualTo(UserRole.EDITOR);
    }

    @Test
    void shouldAllowEditorToCreateEntries() {
        RestClient client1 = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient client2 = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        MealPlanDto plan = createMealPlan(client1, "Editor Entry Test", "#FF5733");
        shareMealPlan(client1, plan.id(), "user2@example.com");

        CreateMealPlanEntryRequest request = new CreateMealPlanEntryRequest(
                LocalDate.of(2026, 2, 1), null, "Entry by Editor", null
        );
        MealPlanEntryDto entry = createEntry(client2, plan.id(), request);

        assertThat(entry.planId()).isEqualTo(plan.id());
        assertThat(entry.placeholderText()).isEqualTo("Entry by Editor");
    }

    @Test
    void shouldPreventEditorFromDeletingPlan() {
        RestClient client1 = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient client2 = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        MealPlanDto plan = createMealPlan(client1, "Editor Delete Test", "#FF5733");
        shareMealPlan(client1, plan.id(), "user2@example.com");

        try {
            deleteMealPlan(client2, plan.id());
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.FORBIDDEN.value());
        }
    }

    @Test
    void shouldUnshareMealPlan() {
        RestClient client1 = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient client2 = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        MealPlanDto plan = createMealPlan(client1, "Unshare Test", "#FF5733");
        shareMealPlan(client1, plan.id(), "user2@example.com");

        List<MealPlanDto> user2Plans = getAllMealPlans(client2);
        assertThat(user2Plans).extracting(MealPlanDto::id).contains(plan.id());

        unshareMealPlan(client1, plan.id(), "user2@example.com");

        List<SharedUserDto> users = getSharedUsers(client1, plan.id());
        assertThat(users).hasSize(1);
        assertThat(users.getFirst().email()).isEqualTo("user1@example.com");

        List<MealPlanDto> user2PlansAfter = getAllMealPlans(client2);
        assertThat(user2PlansAfter).extracting(MealPlanDto::id).doesNotContain(plan.id());
    }

    @Test
    void shouldPreventUnsharingOwner() {
        RestClient client1 = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient client2 = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        MealPlanDto plan = createMealPlan(client1, "Unshare Owner Test", "#FF5733");
        shareMealPlan(client1, plan.id(), "user2@example.com");

        try {
            unshareMealPlan(client2, plan.id(), "user1@example.com");
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.FORBIDDEN.value());
        }

        try {
            unshareMealPlan(client1, plan.id(), "user1@example.com");
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.FORBIDDEN.value());
        }
    }

    @Test
    void shouldReturn404WhenSharingNonexistentPlan() {
        RestClient client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        UUID nonexistent = UUID.randomUUID();

        try {
            shareMealPlan(client, nonexistent, "user2@example.com");
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
        }
    }

    @Test
    void shouldReturn403WhenUnauthorizedUserTriesToShare() {
        RestClient client1 = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient client2 = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        MealPlanDto plan = createMealPlan(client1, "Unauthorized Share Test", "#FF5733");

        try {
            shareMealPlan(client2, plan.id(), "other@example.com");
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.FORBIDDEN.value());
        }
    }

    @Test
    void shouldValidateEmailFormatInShareRequest() {
        RestClient client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        MealPlanDto plan = createMealPlan(client, "Email Validation Test", "#FF5733");

        ShareMealPlanRequest request = new ShareMealPlanRequest("invalid-email");
        try {
            client
                    .post()
                    .uri("/meal-plans/" + plan.id() + "/share")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        }
    }

    @Test
    void shouldAllowEditorToShareMealPlan() {
        RestClient client1 = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient client2 = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        MealPlanDto plan = createMealPlan(client1, "Editor Share Test", "#FF5733");
        shareMealPlan(client1, plan.id(), "user2@example.com");

        shareMealPlan(client2, plan.id(), "user@example.com");

        List<SharedUserDto> users = getSharedUsers(client1, plan.id());
        assertThat(users).hasSize(3);
        assertThat(users).extracting(SharedUserDto::email)
                .contains("user1@example.com", "user2@example.com", "user@example.com");
    }

    @Test
    void shouldGetCalendarView() {
        RestClient client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);

        MealPlanDto plan1 = createMealPlan(client, "Plan 1", "#FF5733");
        MealPlanDto plan2 = createMealPlan(client, "Plan 2", "#33FF57");

        RecipeDetailsDto recipe = createRecipe(client, "Calendar Recipe");

        LocalDate date1 = LocalDate.of(2026, 2, 1);
        LocalDate date2 = LocalDate.of(2026, 2, 2);

        CreateMealPlanEntryRequest entry1 = new CreateMealPlanEntryRequest(date1, recipe.id(), null, 2);
        CreateMealPlanEntryRequest entry2 = new CreateMealPlanEntryRequest(date1, null, "Placeholder", null);
        CreateMealPlanEntryRequest entry3 = new CreateMealPlanEntryRequest(date2, recipe.id(), null, 1);

        createEntry(client, plan1.id(), entry1);
        createEntry(client, plan2.id(), entry2);
        createEntry(client, plan1.id(), entry3);

        String allPlanIds = plan1.id() + "," + plan2.id();
        Map<LocalDate, List<MealPlanCalendarViewDto>> calendar = getCalendarView(
                client, date1, date2, allPlanIds);

        assertThat(calendar).hasSize(2);

        List<MealPlanCalendarViewDto> date1Entries = calendar.get(date1);
        assertThat(date1Entries).hasSize(2);

        MealPlanCalendarViewDto recipeEntry = date1Entries.stream()
                .filter(e -> e.recipeId() != null)
                .findFirst()
                .orElseThrow();
        assertThat(recipeEntry.planId()).isEqualTo(plan1.id());
        assertThat(recipeEntry.planColor()).isEqualTo("#FF5733");
        assertThat(recipeEntry.recipeId()).isEqualTo(recipe.id());
        assertThat(recipeEntry.recipeName()).isEqualTo("Calendar Recipe");
        assertThat(recipeEntry.placeholderText()).isNull();
        assertThat(recipeEntry.servingSize()).isEqualTo(2);
        assertThat(recipeEntry.hasRecipeAccess()).isTrue();

        MealPlanCalendarViewDto placeholderEntry = date1Entries.stream()
                .filter(e -> e.recipeId() == null)
                .findFirst()
                .orElseThrow();
        assertThat(placeholderEntry.planId()).isEqualTo(plan2.id());
        assertThat(placeholderEntry.planColor()).isEqualTo("#33FF57");
        assertThat(placeholderEntry.recipeId()).isNull();
        assertThat(placeholderEntry.recipeName()).isNull();
        assertThat(placeholderEntry.placeholderText()).isEqualTo("Placeholder");
        assertThat(placeholderEntry.servingSize()).isNull();
        assertThat(placeholderEntry.hasRecipeAccess()).isTrue();

        List<MealPlanCalendarViewDto> date2Entries = calendar.get(date2);
        assertThat(date2Entries).hasSize(1);
        assertThat(date2Entries.getFirst().recipeId()).isEqualTo(recipe.id());

        Map<LocalDate, List<MealPlanCalendarViewDto>> filteredCalendar = getCalendarView(
                client, date1, date2, plan1.id().toString());

        assertThat(filteredCalendar).hasSize(2);
        assertThat(filteredCalendar.get(date1)).hasSize(1);
        assertThat(filteredCalendar.get(date1).getFirst().planId()).isEqualTo(plan1.id());
    }

    @Test
    void shouldIndicateRestrictedRecipeAccess() {
        RestClient client1 = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient client2 = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        MealPlanDto plan = createMealPlan(client1, "Shared Plan", "#FF5733");
        shareMealPlan(client1, plan.id(), "user2@example.com");

        RecipeDetailsDto recipe = createRecipe(client1, "Private Recipe");

        CreateMealPlanEntryRequest entry = new CreateMealPlanEntryRequest(
                LocalDate.of(2026, 2, 1), recipe.id(), null, 2);
        createEntry(client1, plan.id(), entry);

        Map<LocalDate, List<MealPlanCalendarViewDto>> calendar = getCalendarView(
                client2, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 1), plan.id().toString());

        assertThat(calendar).hasSize(1);
        List<MealPlanCalendarViewDto> entries = calendar.get(LocalDate.of(2026, 2, 1));
        assertThat(entries).hasSize(1);

        MealPlanCalendarViewDto calendarEntry = entries.getFirst();
        assertThat(calendarEntry.recipeId()).isEqualTo(recipe.id());
        assertThat(calendarEntry.recipeName()).isEqualTo("Private Recipe");
        assertThat(calendarEntry.hasRecipeAccess()).isFalse();
    }

    @Test
    void shouldGenerateShoppingListItemsFromPlannedMeals() {
        RestClient client = restClient();

        RecipeDetailsDto recipe = createRecipe(client, "Pasta");
        MealPlanDto plan = createMealPlan(client, "Shopping Test Plan", "#FF5733");

        LocalDate date = LocalDate.of(2026, 3, 1);
        createEntry(client, plan.id(), new CreateMealPlanEntryRequest(date, recipe.id(), null, 2));

        GeneratedShoppingListResponse response = generateShoppingListItems(
                client, List.of(plan.id()), List.of(date));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().name()).isEqualTo("flour");
        assertThat(response.items().getFirst().quantity()).isEqualTo("300");
        assertThat(response.items().getFirst().unit()).isEqualTo("g");
        assertThat(response.warnings()).isEmpty();

        deleteRecipe(client, recipe.id());
    }

    @Test
    void shouldReturnEmptyListWhenEntriesHaveOnlyPlaceholders() {
        RestClient client = restClient();

        MealPlanDto plan = createMealPlan(client, "Placeholder Only Plan", "#FF5733");
        LocalDate date = LocalDate.of(2026, 3, 1);
        createEntry(client, plan.id(), new CreateMealPlanEntryRequest(date, null, "Leftovers", null));

        GeneratedShoppingListResponse response = generateShoppingListItems(
                client, List.of(plan.id()), List.of(date));

        assertThat(response.items()).isEmpty();
        assertThat(response.warnings()).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenNoDatesMatch() {
        RestClient client = restClient();

        MealPlanDto plan = createMealPlan(client, "No Date Match Plan", "#FF5733");
        createEntry(client, plan.id(), new CreateMealPlanEntryRequest(
                LocalDate.of(2026, 3, 1), null, "Leftovers", null));

        GeneratedShoppingListResponse response = generateShoppingListItems(
                client, List.of(plan.id()), List.of(LocalDate.of(2026, 3, 2)));

        assertThat(response.items()).isEmpty();
        assertThat(response.warnings()).isEmpty();
    }

    @Test
    void shouldSkipInaccessibleRecipesAndReturnWarnings() {
        RestClient client1 = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient client2 = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        RecipeDetailsDto privateRecipe = createRecipe(client1, "Private Recipe");
        MealPlanDto plan = createMealPlan(client1, "Shared Plan With Private Recipe", "#FF5733");
        shareMealPlan(client1, plan.id(), "user2@example.com");

        LocalDate date = LocalDate.of(2026, 3, 1);
        createEntry(client1, plan.id(), new CreateMealPlanEntryRequest(date, privateRecipe.id(), null, 2));

        GeneratedShoppingListResponse response = generateShoppingListItems(
                client2, List.of(plan.id()), List.of(date));

        assertThat(response.items()).isEmpty();
        assertThat(response.warnings()).hasSize(1);
        assertThat(response.warnings().getFirst()).contains("Private Recipe");

        deleteRecipe(client1, privateRecipe.id());
    }

    @Test
    void shouldReturn404WhenRequestedPlanDoesNotExist() {
        RestClient client = restClient();
        UUID nonexistent = UUID.randomUUID();

        try {
            generateShoppingListItems(client, List.of(nonexistent), List.of(LocalDate.of(2026, 3, 1)));
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
        }
    }

    @Test
    void shouldReturn403WhenUserLacksAccessToRequestedPlan() {
        RestClient client1 = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient client2 = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        RecipeDetailsDto recipe = createRecipe(client1, "Secret Recipe");
        MealPlanDto plan = createMealPlan(client1, "Private Plan for Shopping", "#FF5733");

        LocalDate date = LocalDate.of(2026, 3, 1);
        createEntry(client1, plan.id(), new CreateMealPlanEntryRequest(date, recipe.id(), null, 2));

        try {
            generateShoppingListItems(client2, List.of(plan.id()), List.of(date));
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.FORBIDDEN.value());
        }

        deleteRecipe(client1, recipe.id());
    }

    @Test
    void shouldValidateDateRangeInCalendarView() {
        RestClient client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);

        MealPlanDto plan = createMealPlan(client, "Date Range Test", "#FF5733");
        String planIds = plan.id().toString();

        try {
            getCalendarView(client, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), planIds);
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value());
            assertThat(ex.getResponseBodyAsString()).contains("startDate must be before or equal to endDate");
        }

        try {
            getCalendarView(client, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 1), planIds);
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value());
            assertThat(ex.getResponseBodyAsString()).contains("Date range cannot exceed 3 months");
        }
    }
}
