package xyz.stasiak.recipai.planning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import xyz.stasiak.recipai.IntegrationTest;
import xyz.stasiak.recipai.LimitsEnabled;
import xyz.stasiak.recipai.RecomputeMigration;
import xyz.stasiak.recipai.TestRestClients;
import xyz.stasiak.recipai.TestIdentities;
import xyz.stasiak.recipai.limits.LimitBalance;
import xyz.stasiak.recipai.limits.LimitsFacade;
import xyz.stasiak.recipai.permissions.dto.PendingInviteDto;
import xyz.stasiak.recipai.permissions.dto.PermissionDto;
import xyz.stasiak.recipai.permissions.dto.ResourceRole;
import xyz.stasiak.recipai.permissions.dto.ShareRequest;
import xyz.stasiak.recipai.permissions.dto.UnshareRequest;
import xyz.stasiak.recipai.planning.dto.*;
import xyz.stasiak.recipai.recipes.*;
import xyz.stasiak.recipai.recipes.collections.dto.CreateRecipesCollectionRequest;
import xyz.stasiak.recipai.recipes.collections.dto.RecipesCollectionListDto;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@IntegrationTest
class MealPlanIntegrationTest {

    @LocalServerPort
    private int port;

    private String owner;
    private String user1;
    private String user2;

    @BeforeEach
    void freshUsers() {
        owner = TestIdentities.freshToken();
        user1 = TestIdentities.freshToken();
        user2 = TestIdentities.freshToken();
    }

    private RestClient restClient() {
        return restClient(owner);
    }

    private RestClient restClient(String authToken) {
        return TestRestClients.forToken(port, authToken);
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
                List.of(new Ingredient("flour", new BigDecimal(300), "g", null)),
                List.of(new Instruction("Mix")),
                null,
                1
        );
        return createRecipe(client, name, data);
    }

    private RecipeDetailsDto createRecipe(RestClient client, String name, RecipeData data) {
        return createRecipe(client, name, data, null);
    }

    private RecipeDetailsDto createRecipe(RestClient client, String name, RecipeData data, UUID collectionId) {
        CreateRecipeRequest request = new CreateRecipeRequest(name, data, collectionId, List.of());
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

    private List<RecipeListDto> getAllRecipes(RestClient client) {
        return client
                .get()
                .uri("/recipes")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private RecipeData createTestRecipeData() {
        return new RecipeData(
                List.of(new Ingredient("flour", new BigDecimal(300), "g", null)),
                List.of(new Instruction("Mix")),
                null,
                1
        );
    }

    private void shareRecipe(RestClient client, UUID recipeId, String email) {
        ShareRequest request = new ShareRequest(email, ResourceRole.EDITOR);
        client
                .post()
                .uri("/recipes/" + recipeId + "/share")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    private List<PermissionDto> getRecipePermissions(RestClient client, UUID recipeId) {
        return client
                .get()
                .uri("/recipes/" + recipeId + "/permissions")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private List<PendingInviteDto> getPendingInvites(RestClient client) {
        return client
                .get()
                .uri("/invites")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private void acceptInvite(RestClient client, UUID inviteId) {
        client
                .post()
                .uri("/invites/" + inviteId + "/accept")
                .retrieve()
                .toBodilessEntity();
    }

    private UUID findPendingInviteId(RestClient client, String resourceType, String label) {
        return getPendingInvites(client).stream()
                .filter(invite -> invite.resourceType().equals(resourceType) && invite.label().equals(label))
                .findFirst()
                .orElseThrow()
                .id();
    }

    private void acceptPendingRecipeInvite(RestClient client, String recipeName) {
        acceptInvite(client, findPendingInviteId(client, "RECIPE", recipeName));
    }

    private void acceptPendingMealPlanInvite(RestClient client, String planName) {
        acceptInvite(client, findPendingInviteId(client, "MEAL_PLAN", planName));
    }

    private void acceptPendingCollectionInvite(RestClient client, String collectionName) {
        acceptInvite(client, findPendingInviteId(client, "RECIPES_COLLECTION", collectionName));
    }

    private RecipesCollectionListDto createCollection(RestClient client, String name) {
        CreateRecipesCollectionRequest request = new CreateRecipesCollectionRequest(name);
        return client
                .post()
                .uri("/collections")
                .body(request)
                .retrieve()
                .body(RecipesCollectionListDto.class);
    }

    private void shareCollection(RestClient client, UUID collectionId, String email) {
        ShareRequest request = new ShareRequest(email, ResourceRole.EDITOR);
        client
                .post()
                .uri("/collections/" + collectionId + "/share")
                .body(request)
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

    private List<PermissionDto> getPermissions(RestClient client, UUID planId) {
        return client
                .get()
                .uri("/meal-plans/" + planId + "/permissions")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private void shareMealPlan(RestClient client, UUID planId, String email) {
        ShareRequest request = new ShareRequest(email, ResourceRole.EDITOR);
        client
                .post()
                .uri("/meal-plans/" + planId + "/share")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    private void unshareMealPlan(RestClient client, UUID planId, String email) {
        UnshareRequest request = new UnshareRequest(email);
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
        assertThat(created.role()).isEqualTo(ResourceRole.OWNER);
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
        RestClient client1 = restClient(user1);
        RestClient client2 = restClient(user2);

        MealPlanDto plan1 = createMealPlan(client1, "User1 Plan", "#FF0000");
        MealPlanDto plan2 = createMealPlan(client2, "User2 Plan", "#0000FF");

        List<MealPlanDto> user1Plans = getAllMealPlans(client1);
        List<MealPlanDto> user2Plans = getAllMealPlans(client2);

        assertThat(user1Plans).extracting(MealPlanDto::id).contains(plan1.id()).doesNotContain(plan2.id());
        assertThat(user2Plans).extracting(MealPlanDto::id).contains(plan2.id()).doesNotContain(plan1.id());
    }

    @Test
    void shouldPreventCrossUserAccess() {
        RestClient client1 = restClient(user1);
        RestClient client2 = restClient(user2);

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
                LocalDate.of(2026, 2, 1), null, "Updated", null, plan.id()
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
                    LocalDate.of(2026, 1, 29), null, "Test", null, plan.id()
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
                    LocalDate.of(2026, 1, 29), null, "Moved", null, plan1.id()
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
        RestClient client1 = restClient(user1);
        RestClient client2 = restClient(user2);

        MealPlanDto plan = createMealPlan(client1, "Private Plan Entries", "#FF0000");

        CreateMealPlanEntryRequest request = new CreateMealPlanEntryRequest(
                LocalDate.of(2026, 1, 29), null, "Private Entry", null
        );
        MealPlanEntryDto entry = createEntry(client1, plan.id(), request);

        try {
            UpdateMealPlanEntryRequest updateReq = new UpdateMealPlanEntryRequest(
                    LocalDate.of(2026, 1, 29), null, "Hacked", null, plan.id()
            );
            updateEntry(client2, plan.id(), entry.id(), updateReq);
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.FORBIDDEN.value());
        }
    }

    @Test
    void shouldListSharedUsersForOwnPlan() {
        RestClient client = restClient(user1);
        MealPlanDto plan = createMealPlan(client, "Shared Users Test", "#FF5733");

        List<PermissionDto> permissions = getPermissions(client, plan.id());

        assertThat(permissions).containsExactly(new PermissionDto(TestIdentities.emailOf(user1), ResourceRole.OWNER, false));
    }

    @Test
    void shouldShareMealPlanWithAnotherUser() {
        RestClient client1 = restClient(user1);
        RestClient client2 = restClient(user2);

        MealPlanDto plan = createMealPlan(client1, "Shared Plan", "#FF5733");

        // Sharing creates a pending invite - grants nothing yet
        shareMealPlan(client1, plan.id(), TestIdentities.emailOf(user2));

        assertThat(getAllMealPlans(client2)).extracting(MealPlanDto::id).doesNotContain(plan.id());

        List<PermissionDto> permissionsWhilePending = getPermissions(client1, plan.id());
        assertThat(permissionsWhilePending).containsExactly(
                new PermissionDto(TestIdentities.emailOf(user1), ResourceRole.OWNER, false),
                new PermissionDto(TestIdentities.emailOf(user2), ResourceRole.EDITOR, true)
        );

        acceptPendingMealPlanInvite(client2, plan.name());

        List<PermissionDto> permissionsAfterAccept = getPermissions(client1, plan.id());
        assertThat(permissionsAfterAccept).containsExactly(
                new PermissionDto(TestIdentities.emailOf(user1), ResourceRole.OWNER, false),
                new PermissionDto(TestIdentities.emailOf(user2), ResourceRole.EDITOR, false)
        );

        List<MealPlanDto> user2Plans = getAllMealPlans(client2);
        assertThat(user2Plans).extracting(MealPlanDto::id).contains(plan.id());
        assertThat(user2Plans.stream().filter(p -> p.id().equals(plan.id())).findFirst().orElseThrow().role())
                .isEqualTo(ResourceRole.EDITOR);
    }

    @Test
    void shouldRefuseSecondShareWhenTargetAlreadyHasAccess() {
        RestClient client1 = restClient(user1);
        RestClient client2 = restClient(user2);

        MealPlanDto plan = createMealPlan(client1, "Already Has Access Test", "#FF5733");
        shareMealPlan(client1, plan.id(), TestIdentities.emailOf(user2));
        acceptPendingMealPlanInvite(client2, plan.name());

        try {
            shareMealPlan(client1, plan.id(), TestIdentities.emailOf(user2));
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.CONFLICT.value());
            Map<String, Object> body = ex.getResponseBodyAs(new ParameterizedTypeReference<>() {
            });
            assertThat(body.get("reason")).isEqualTo("ALREADY_HAS_ACCESS");
        }
    }

    @Test
    void shouldListPlansWithRoleFromOneAccessMap() {
        RestClient client1 = restClient(user1);
        RestClient client2 = restClient(user2);

        MealPlanDto plan = createMealPlan(client1, "Role From Access Map Test", "#FF5733");
        shareMealPlan(client1, plan.id(), TestIdentities.emailOf(user2));
        acceptPendingMealPlanInvite(client2, plan.name());

        List<MealPlanDto> ownerPlans = getAllMealPlans(client1);
        assertThat(ownerPlans.stream().filter(p -> p.id().equals(plan.id())).findFirst().orElseThrow().role())
                .isEqualTo(ResourceRole.OWNER);

        List<MealPlanDto> editorPlans = getAllMealPlans(client2);
        assertThat(editorPlans.stream().filter(p -> p.id().equals(plan.id())).findFirst().orElseThrow().role())
                .isEqualTo(ResourceRole.EDITOR);
    }

    @Test
    void shouldAllowEditorToEditSharedPlan() {
        RestClient client1 = restClient(user1);
        RestClient client2 = restClient(user2);

        MealPlanDto plan = createMealPlan(client1, "Editor Test", "#FF5733");
        shareMealPlan(client1, plan.id(), TestIdentities.emailOf(user2));
        acceptPendingMealPlanInvite(client2, plan.name());

        MealPlanDto updated = updateMealPlan(client2, plan.id(), "Updated by Editor", "#00FF00");

        assertThat(updated.name()).isEqualTo("Updated by Editor");
        assertThat(updated.color()).isEqualTo("#00FF00");
        assertThat(updated.role()).isEqualTo(ResourceRole.EDITOR);
    }

    @Test
    void shouldAllowEditorToCreateEntries() {
        RestClient client1 = restClient(user1);
        RestClient client2 = restClient(user2);

        MealPlanDto plan = createMealPlan(client1, "Editor Entry Test", "#FF5733");
        shareMealPlan(client1, plan.id(), TestIdentities.emailOf(user2));
        acceptPendingMealPlanInvite(client2, plan.name());

        CreateMealPlanEntryRequest request = new CreateMealPlanEntryRequest(
                LocalDate.of(2026, 2, 1), null, "Entry by Editor", null
        );
        MealPlanEntryDto entry = createEntry(client2, plan.id(), request);

        assertThat(entry.planId()).isEqualTo(plan.id());
        assertThat(entry.placeholderText()).isEqualTo("Entry by Editor");
    }

    @Test
    void shouldPreventEditorFromDeletingPlan() {
        RestClient client1 = restClient(user1);
        RestClient client2 = restClient(user2);

        MealPlanDto plan = createMealPlan(client1, "Editor Delete Test", "#FF5733");
        shareMealPlan(client1, plan.id(), TestIdentities.emailOf(user2));
        acceptPendingMealPlanInvite(client2, plan.name());

        try {
            deleteMealPlan(client2, plan.id());
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.FORBIDDEN.value());
        }
    }

    @Test
    void shouldUnshareMealPlan() {
        RestClient client1 = restClient(user1);
        RestClient client2 = restClient(user2);

        MealPlanDto plan = createMealPlan(client1, "Unshare Test", "#FF5733");
        shareMealPlan(client1, plan.id(), TestIdentities.emailOf(user2));
        acceptPendingMealPlanInvite(client2, plan.name());

        List<MealPlanDto> user2Plans = getAllMealPlans(client2);
        assertThat(user2Plans).extracting(MealPlanDto::id).contains(plan.id());

        unshareMealPlan(client1, plan.id(), TestIdentities.emailOf(user2));

        List<PermissionDto> permissions = getPermissions(client1, plan.id());
        assertThat(permissions).containsExactly(new PermissionDto(TestIdentities.emailOf(user1), ResourceRole.OWNER, false));

        List<MealPlanDto> user2PlansAfter = getAllMealPlans(client2);
        assertThat(user2PlansAfter).extracting(MealPlanDto::id).doesNotContain(plan.id());
    }

    @Test
    void shouldPreventUnsharingOwner() {
        RestClient client1 = restClient(user1);
        RestClient client2 = restClient(user2);

        MealPlanDto plan = createMealPlan(client1, "Unshare Owner Test", "#FF5733");
        shareMealPlan(client1, plan.id(), TestIdentities.emailOf(user2));
        acceptPendingMealPlanInvite(client2, plan.name());

        try {
            unshareMealPlan(client2, plan.id(), TestIdentities.emailOf(user1));
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.FORBIDDEN.value());
        }

        try {
            unshareMealPlan(client1, plan.id(), TestIdentities.emailOf(user1));
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.FORBIDDEN.value());
        }
    }

    @Test
    void shouldReturn404WhenSharingNonexistentPlan() {
        RestClient client = restClient(user1);
        UUID nonexistent = UUID.randomUUID();

        try {
            shareMealPlan(client, nonexistent, TestIdentities.emailOf(user2));
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
        }
    }

    @Test
    void shouldReturn403WhenUnauthorizedUserTriesToShare() {
        RestClient client1 = restClient(user1);
        RestClient client2 = restClient(user2);

        MealPlanDto plan = createMealPlan(client1, "Unauthorized Share Test", "#FF5733");

        try {
            shareMealPlan(client2, plan.id(), TestIdentities.emailOf("other"));
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.FORBIDDEN.value());
        }
    }

    @Test
    void shouldValidateEmailFormatInShareRequest() {
        RestClient client = restClient(user1);
        MealPlanDto plan = createMealPlan(client, "Email Validation Test", "#FF5733");

        ShareRequest request = new ShareRequest("invalid-email", ResourceRole.EDITOR);
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
        RestClient client1 = restClient(user1);
        RestClient client2 = restClient(user2);
        RestClient client3 = restClient(owner);

        MealPlanDto plan = createMealPlan(client1, "Editor Share Test", "#FF5733");
        shareMealPlan(client1, plan.id(), TestIdentities.emailOf(user2));
        acceptPendingMealPlanInvite(client2, plan.name());

        shareMealPlan(client2, plan.id(), TestIdentities.emailOf(owner));
        acceptPendingMealPlanInvite(client3, plan.name());

        List<PermissionDto> permissions = getPermissions(client1, plan.id());
        assertThat(permissions).hasSize(3);
        assertThat(permissions).extracting(PermissionDto::email)
                .contains(TestIdentities.emailOf(user1), TestIdentities.emailOf(user2), TestIdentities.emailOf(owner));
    }

    @Test
    void shouldGetCalendarView() {
        RestClient client = restClient(user1);

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
    void shouldRefuseCalendarRecipeAccessWhenNeitherPathReaches() {
        RestClient client1 = restClient(user1);
        RestClient client2 = restClient(user2);

        MealPlanDto plan = createMealPlan(client1, "Shared Plan", "#FF5733");
        shareMealPlan(client1, plan.id(), TestIdentities.emailOf(user2));
        acceptPendingMealPlanInvite(client2, plan.name());

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

        // Neither path reaches: user2 holds no direct recipe permission and no collection access
        assertThat(getRecipePermissions(client1, recipe.id())).extracting(PermissionDto::email)
                .doesNotContain(TestIdentities.emailOf(user2));
        assertThat(getAllRecipes(client2)).extracting(RecipeListDto::id).doesNotContain(recipe.id());
    }

    @Test
    void shouldGrantCalendarRecipeAccessThroughDirectRecipePermission() {
        RestClient client1 = restClient(user1);
        RestClient client2 = restClient(user2);

        MealPlanDto plan = createMealPlan(client1, "Shared Plan For Invite", "#FF5733");
        shareMealPlan(client1, plan.id(), TestIdentities.emailOf(user2));
        acceptPendingMealPlanInvite(client2, plan.name());

        RecipeDetailsDto recipe = createRecipe(client1, "Invited Recipe");

        CreateMealPlanEntryRequest entry = new CreateMealPlanEntryRequest(
                LocalDate.of(2026, 2, 1), recipe.id(), null, 2);
        createEntry(client1, plan.id(), entry);

        // Before the invite is accepted, user2 has no access to the recipe
        Map<LocalDate, List<MealPlanCalendarViewDto>> calendarBefore = getCalendarView(
                client2, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 1), plan.id().toString());
        assertThat(calendarBefore.get(LocalDate.of(2026, 2, 1)).getFirst().hasRecipeAccess()).isFalse();

        shareRecipe(client1, recipe.id(), TestIdentities.emailOf(user2));
        acceptPendingRecipeInvite(client2, recipe.name());

        Map<LocalDate, List<MealPlanCalendarViewDto>> calendarAfter = getCalendarView(
                client2, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 1), plan.id().toString());

        assertThat(calendarAfter).hasSize(1);
        List<MealPlanCalendarViewDto> entries = calendarAfter.get(LocalDate.of(2026, 2, 1));
        assertThat(entries).hasSize(1);

        MealPlanCalendarViewDto calendarEntry = entries.getFirst();
        assertThat(calendarEntry.recipeId()).isEqualTo(recipe.id());
        assertThat(calendarEntry.hasRecipeAccess()).isTrue();
    }

    @Test
    void shouldGrantCalendarRecipeAccessThroughSharedCollection() {
        RestClient client1 = restClient(user1);
        RestClient client2 = restClient(user2);

        MealPlanDto plan = createMealPlan(client1, "Shared Plan For Collection Access", "#FF5733");
        shareMealPlan(client1, plan.id(), TestIdentities.emailOf(user2));
        acceptPendingMealPlanInvite(client2, plan.name());

        RecipesCollectionListDto collection = createCollection(client1, "Calendar Collection");
        shareCollection(client1, collection.id(), TestIdentities.emailOf(user2));
        acceptPendingCollectionInvite(client2, collection.name());

        RecipeDetailsDto recipe = createRecipe(client1, "Collection Recipe", createTestRecipeData(), collection.id());

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
        assertThat(calendarEntry.hasRecipeAccess()).isTrue();

        // No direct recipe permission exists - access is entirely collection-derived
        assertThat(getRecipePermissions(client1, recipe.id())).extracting(PermissionDto::email)
                .doesNotContain(TestIdentities.emailOf(user2));
    }

    @Test
    void shouldReturnEmptyCalendarWhenNoRequestedPlanIsAccessible() {
        RestClient client1 = restClient(user1);
        RestClient client2 = restClient(user2);

        MealPlanDto plan = createMealPlan(client1, "Unshared Plan For Calendar", "#FF5733");

        Map<LocalDate, List<MealPlanCalendarViewDto>> calendar = getCalendarView(
                client2, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 1), plan.id().toString());

        // Today's join silently dropped an unreachable plan - the intersection preserves that: no 403
        assertThat(calendar).isEmpty();
    }

    @Test
    void shouldIncludeOnlyAccessiblePlansWhenSomeRequestedPlansAreNot() {
        RestClient client1 = restClient(user1);
        RestClient client2 = restClient(user2);

        MealPlanDto sharedPlan = createMealPlan(client1, "Shared Plan For Mixed Access", "#FF5733");
        shareMealPlan(client1, sharedPlan.id(), TestIdentities.emailOf(user2));
        acceptPendingMealPlanInvite(client2, sharedPlan.name());

        MealPlanDto privatePlan = createMealPlan(client1, "Private Plan For Mixed Access", "#00FF00");

        LocalDate date = LocalDate.of(2026, 2, 1);
        createEntry(client1, sharedPlan.id(), new CreateMealPlanEntryRequest(date, null, "Shared Entry", null));
        createEntry(client1, privatePlan.id(), new CreateMealPlanEntryRequest(date, null, "Private Entry", null));

        String requestedPlanIds = sharedPlan.id() + "," + privatePlan.id();
        Map<LocalDate, List<MealPlanCalendarViewDto>> calendar = getCalendarView(
                client2, date, date, requestedPlanIds);

        assertThat(calendar).hasSize(1);
        assertThat(calendar.get(date)).hasSize(1);
        assertThat(calendar.get(date).getFirst().planId()).isEqualTo(sharedPlan.id());
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
        assertThat(response.items().getFirst().quantity()).isEqualByComparingTo(new BigDecimal("600"));
        assertThat(response.items().getFirst().unit()).isEqualTo("g");
        assertThat(response.items().getFirst().source()).isEqualTo("Pasta");
        assertThat(response.inaccessibleRecipeNames()).isEmpty();

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
        assertThat(response.inaccessibleRecipeNames()).isEmpty();
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
        assertThat(response.inaccessibleRecipeNames()).isEmpty();
    }

    @Test
    void shouldSkipInaccessibleRecipesAndReturnWarnings() {
        RestClient client1 = restClient(user1);
        RestClient client2 = restClient(user2);

        RecipeDetailsDto privateRecipe = createRecipe(client1, "Private Recipe");
        MealPlanDto plan = createMealPlan(client1, "Shared Plan With Private Recipe", "#FF5733");
        shareMealPlan(client1, plan.id(), TestIdentities.emailOf(user2));
        acceptPendingMealPlanInvite(client2, plan.name());

        LocalDate date = LocalDate.of(2026, 3, 1);
        createEntry(client1, plan.id(), new CreateMealPlanEntryRequest(date, privateRecipe.id(), null, 2));

        GeneratedShoppingListResponse response = generateShoppingListItems(
                client2, List.of(plan.id()), List.of(date));

        assertThat(response.items()).isEmpty();
        assertThat(response.inaccessibleRecipeNames()).hasSize(1);
        assertThat(response.inaccessibleRecipeNames().getFirst()).isEqualTo("Private Recipe");

        deleteRecipe(client1, privateRecipe.id());
    }

    @Test
    void shouldGenerateShoppingListItemsForAnEditorWithoutTheDeJoinedFilter() {
        RestClient client1 = restClient(user1);
        RestClient client2 = restClient(user2);

        RecipeDetailsDto recipe = createRecipe(client1, "Editor Accessible Recipe");
        MealPlanDto plan = createMealPlan(client1, "Shared Plan For Editor Generation", "#FF5733");
        shareMealPlan(client1, plan.id(), TestIdentities.emailOf(user2));
        acceptPendingMealPlanInvite(client2, plan.name());
        shareRecipe(client1, recipe.id(), TestIdentities.emailOf(user2));
        acceptPendingRecipeInvite(client2, recipe.name());

        LocalDate date = LocalDate.of(2026, 3, 1);
        createEntry(client1, plan.id(), new CreateMealPlanEntryRequest(date, recipe.id(), null, 2));

        GeneratedShoppingListResponse response = generateShoppingListItems(
                client2, List.of(plan.id()), List.of(date));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().name()).isEqualTo("flour");
        assertThat(response.inaccessibleRecipeNames()).isEmpty();

        deleteRecipe(client1, recipe.id());
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
        RestClient client1 = restClient(user1);
        RestClient client2 = restClient(user2);

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
    void shouldApplyServingSizeMultiplierToNonNumericQuantity() {
        RestClient client = restClient();

        RecipeData data = new RecipeData(
                List.of(new Ingredient("salt", null, null, "to taste")),
                List.of(new Instruction("Season")),
                null,
                1
        );
        RecipeDetailsDto recipe = createRecipe(client, "Seasoned Dish", data);
        MealPlanDto plan = createMealPlan(client, "Non-numeric Quantity Plan", "#FF5733");

        LocalDate date = LocalDate.of(2026, 3, 1);
        createEntry(client, plan.id(), new CreateMealPlanEntryRequest(date, recipe.id(), null, 3));

        GeneratedShoppingListResponse response = generateShoppingListItems(
                client, List.of(plan.id()), List.of(date));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().name()).isEqualTo("salt (to taste)");
        assertThat(response.items().getFirst().quantity()).isEqualByComparingTo(new BigDecimal("3"));
        assertThat(response.items().getFirst().unit()).isNull();
        assertThat(response.items().getFirst().source()).isEqualTo("Seasoned Dish");
        assertThat(response.inaccessibleRecipeNames()).isEmpty();

        deleteRecipe(client, recipe.id());
    }

    @Test
    void shouldMoveEntryToAnotherPlan() {
        RestClient client = restClient();

        MealPlanDto sourcePlan = createMealPlan(client, "Source Plan", "#FF5733");
        MealPlanDto targetPlan = createMealPlan(client, "Target Plan", "#00FF00");

        CreateMealPlanEntryRequest createReq = new CreateMealPlanEntryRequest(
                LocalDate.of(2026, 2, 1), null, "Movable Entry", null
        );
        MealPlanEntryDto entry = createEntry(client, sourcePlan.id(), createReq);
        assertThat(entry.planId()).isEqualTo(sourcePlan.id());

        UpdateMealPlanEntryRequest moveReq = new UpdateMealPlanEntryRequest(
                LocalDate.of(2026, 2, 1), null, "Movable Entry", null, targetPlan.id()
        );
        MealPlanEntryDto moved = updateEntry(client, sourcePlan.id(), entry.id(), moveReq);

        assertThat(moved.planId()).isEqualTo(targetPlan.id());
        assertThat(moved.placeholderText()).isEqualTo("Movable Entry");
    }

    @Test
    void shouldReturn404WhenMovingEntryToNonexistentPlan() {
        RestClient client = restClient();

        MealPlanDto sourcePlan = createMealPlan(client, "Source Plan", "#FF5733");
        CreateMealPlanEntryRequest createReq = new CreateMealPlanEntryRequest(
                LocalDate.of(2026, 2, 1), null, "Entry", null
        );
        MealPlanEntryDto entry = createEntry(client, sourcePlan.id(), createReq);

        UUID nonexistentPlanId = UUID.randomUUID();
        UpdateMealPlanEntryRequest moveReq = new UpdateMealPlanEntryRequest(
                LocalDate.of(2026, 2, 1), null, "Entry", null, nonexistentPlanId
        );

        try {
            updateEntry(client, sourcePlan.id(), entry.id(), moveReq);
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
        }
    }

    @Test
    void shouldReturn403WhenMovingEntryToInaccessiblePlan() {
        RestClient client1 = restClient(user1);
        RestClient client2 = restClient(user2);

        MealPlanDto user1Plan = createMealPlan(client1, "User1 Source Plan", "#FF0000");
        MealPlanDto user2Plan = createMealPlan(client2, "User2 Private Plan", "#0000FF");

        CreateMealPlanEntryRequest createReq = new CreateMealPlanEntryRequest(
                LocalDate.of(2026, 2, 1), null, "Entry", null
        );
        MealPlanEntryDto entry = createEntry(client1, user1Plan.id(), createReq);

        UpdateMealPlanEntryRequest moveReq = new UpdateMealPlanEntryRequest(
                LocalDate.of(2026, 2, 1), null, "Entry", null, user2Plan.id()
        );

        try {
            updateEntry(client1, user1Plan.id(), entry.id(), moveReq);
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.FORBIDDEN.value());
        }
    }

    @Test
    void shouldValidateDateRangeInCalendarView() {
        RestClient client = restClient(user1);

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

    @Nested
    @LimitsEnabled
    class LimitsEnforced {

        private String ownerSubject;

        @Autowired
        private LimitsFacade limitsFacade;

        @Autowired
        private JdbcClient jdbcClient;

        @Autowired
        private DataSource dataSource;

        @BeforeEach
        void setUpQuota() {
            ownerSubject = TestIdentities.emailOf(owner);
            setLimitQuota("MEAL_PLAN", ownerSubject, 2);
        }

        private int usedFor(String subject) {
            return limitsFacade.getBalance(subject, MealPlanService.MEAL_PLAN_RESOURCE)
                    .map(LimitBalance::used)
                    .orElse(0);
        }

        private void setLimitQuota(String resource, String subject, int maxValue) {
            setLimitQuota(resource, subject, "STOCK", maxValue);
        }

        /**
         * Upserts the quota: {@code limit_config} has no write API, so there is no business path to it.
         */
        private void setLimitQuota(String resource, String subject, String kind, int maxValue) {
            jdbcClient.sql("""
                            INSERT INTO recipai.limit_config (id, resource, subject, kind, max_value, period)
                            VALUES (:id, :resource, :subject, :kind, :maxValue, NULL)
                            ON CONFLICT (resource, subject) DO UPDATE SET
                                kind      = EXCLUDED.kind,
                                max_value = EXCLUDED.max_value
                            """)
                    .param("id", UUID.randomUUID())
                    .param("resource", resource)
                    .param("subject", subject)
                    .param("kind", kind)
                    .param("maxValue", maxValue)
                    .update();
        }

        @Test
        void shouldRefuseThirdCreateWithLimitDetails() {
            RestClient client = restClient();
            createMealPlan(client, "Plan 1", "#FF5733");
            createMealPlan(client, "Plan 2", "#FF5733");

            try {
                createMealPlan(client, "Plan 3", "#FF5733");
                fail("Should have thrown exception");
            } catch (RestClientResponseException ex) {
                assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
                assertThat(ex.getResponseHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

                Map<String, Object> body = ex.getResponseBodyAs(new ParameterizedTypeReference<Map<String, Object>>() {
                });
                assertThat(body).isNotNull();
                assertThat(body.get("resource")).isEqualTo("MEAL_PLAN");
                assertThat(body.get("kind")).isEqualTo("STOCK");
                assertThat(body.get("limit")).isEqualTo(2);
                assertThat(body.get("used")).isEqualTo(2);
            }
        }

        @Test
        void shouldCarryNoRetryAfterOnStockRefusal() {
            RestClient client = restClient();
            createMealPlan(client, "Plan 1", "#FF5733");
            createMealPlan(client, "Plan 2", "#FF5733");

            try {
                createMealPlan(client, "Plan 3", "#FF5733");
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
        void shouldAllowReadAndUpdateWhileOverQuotaButKeepCreationRefused() {
            RestClient client = restClient();
            MealPlanDto plan1 = createMealPlan(client, "Plan 1", "#FF5733");
            createMealPlan(client, "Plan 2", "#FF5733");

            setLimitQuota("MEAL_PLAN", ownerSubject, 1);

            List<MealPlanDto> plans = getAllMealPlans(client);
            assertThat(plans).extracting(MealPlanDto::id).contains(plan1.id());

            MealPlanDto updated = updateMealPlan(client, plan1.id(), "Plan 1 Updated", "#00FF00");
            assertThat(updated.name()).isEqualTo("Plan 1 Updated");

            try {
                createMealPlan(client, "Plan 3", "#FF5733");
                fail("Should have thrown exception");
            } catch (RestClientResponseException ex) {
                assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
            }
        }

        @Test
        void shouldAdmitNextCreateAndDropBalanceAfterDelete() {
            RestClient client = restClient();
            MealPlanDto plan1 = createMealPlan(client, "Plan 1", "#FF5733");
            createMealPlan(client, "Plan 2", "#FF5733");
            assertThat(usedFor(ownerSubject)).isEqualTo(2);

            deleteMealPlan(client, plan1.id());
            assertThat(usedFor(ownerSubject)).isEqualTo(1);

            createMealPlan(client, "Plan 3", "#FF5733");
            assertThat(usedFor(ownerSubject)).isEqualTo(2);
        }

        @Test
        void shouldLeaveRecipientBalanceUntouchedOnShareAndUnshare() {
            RestClient client = restClient();
            RestClient recipientClient = restClient(user2);
            MealPlanDto plan = createMealPlan(client, "Shared Plan", "#FF5733");
            assertThat(usedFor(ownerSubject)).isEqualTo(1);

            shareMealPlan(client, plan.id(), TestIdentities.emailOf(user2));
            acceptPendingMealPlanInvite(recipientClient, plan.name());
            assertThat(usedFor(TestIdentities.emailOf(user2))).isZero();

            unshareMealPlan(client, plan.id(), TestIdentities.emailOf(user2));
            assertThat(usedFor(TestIdentities.emailOf(user2))).isZero();
        }

        @Test
        void shouldRepairDriftToActualOwnedCountViaRecompute() {
            RestClient client = restClient();
            createMealPlan(client, "Plan 1", "#FF5733");
            createMealPlan(client, "Plan 2", "#FF5733");
            assertThat(usedFor(ownerSubject)).isEqualTo(2);

            // Deliberate drift: no business path can move used away from the owned count.
            jdbcClient.sql("UPDATE recipai.limit_usage SET used = 99 WHERE resource = 'MEAL_PLAN' AND subject = :subject")
                    .param("subject", ownerSubject)
                    .update();
            assertThat(usedFor(ownerSubject)).isEqualTo(99);

            RecomputeMigration.run(dataSource);

            assertThat(usedFor(ownerSubject)).isEqualTo(2);
        }

        @Test
        void shouldClearUsageForSubjectThatOwnsNothing() {
            String ghost = TestIdentities.emailOf(TestIdentities.freshToken());
            // A usage row for a subject that owns nothing: no business path leaves one behind.
            jdbcClient.sql("""
                            INSERT INTO recipai.limit_usage (resource, subject, used, period_start)
                            VALUES ('MEAL_PLAN', :subject, 5, now())
                            """)
                    .param("subject", ghost)
                    .update();
            assertThat(usedFor(ghost)).isEqualTo(5);

            RecomputeMigration.run(dataSource);

            assertThat(limitsFacade.getBalance(ghost, MealPlanService.MEAL_PLAN_RESOURCE)).isEmpty();
        }

        @Test
        void shouldSpareFlowConfiguredSubjectFromRecompute() {
            RestClient client = restClient();
            setLimitQuota("MEAL_PLAN", ownerSubject, "FLOW", 5);
            try {
                MealPlanDto first = createMealPlan(client, "Flow 1", "#FF5733");
                createMealPlan(client, "Flow 2", "#FF5733");
                // A flow release refunds nothing, so the balance stays at 2 while only one is owned.
                deleteMealPlan(client, first.id());
                assertThat(usedFor(ownerSubject)).isEqualTo(2);

                RecomputeMigration.run(dataSource);

                assertThat(usedFor(ownerSubject)).isEqualTo(2);
            } finally {
                setLimitQuota("MEAL_PLAN", ownerSubject, 2);
                limitsFacade.clear(ownerSubject, MealPlanService.MEAL_PLAN_RESOURCE);
            }
        }

        @Test
        void shouldSpareSubjectWithoutOverrideWhenResourceDefaultIsFlow() {
            String defaultFlowSubject = TestIdentities.emailOf(user1);
            RestClient client = restClient(user1);
            setLimitQuota("MEAL_PLAN", null, "FLOW", 5);
            try {
                MealPlanDto first = createMealPlan(client, "Flow 1", "#FF5733");
                createMealPlan(client, "Flow 2", "#FF5733");
                // A flow release refunds nothing, so the balance stays at 2 while only one is owned.
                deleteMealPlan(client, first.id());
                assertThat(usedFor(defaultFlowSubject)).isEqualTo(2);

                RecomputeMigration.run(dataSource);

                assertThat(usedFor(defaultFlowSubject)).isEqualTo(2);
            } finally {
                setLimitQuota("MEAL_PLAN", null, "STOCK", 2);
                limitsFacade.clear(defaultFlowSubject, MealPlanService.MEAL_PLAN_RESOURCE);
            }
        }

        @Test
        void shouldChangeNothingOnSecondRecomputeRun() {
            RestClient client = restClient();
            createMealPlan(client, "Plan 1", "#FF5733");

            RecomputeMigration.run(dataSource);
            int firstRun = usedFor(ownerSubject);

            RecomputeMigration.run(dataSource);
            int secondRun = usedFor(ownerSubject);

            assertThat(secondRun).isEqualTo(firstRun);
            assertThat(secondRun).isEqualTo(1);
        }

        private Map<String, Object> getBalance(RestClient client) {
            return client.get()
                    .uri("/meal-plans/balance")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        }

        @Test
        void shouldTrackUsageAcrossCreateAndDelete() {
            RestClient client = restClient();
            assertThat(getBalance(client).get("used")).isEqualTo(0);

            MealPlanDto plan1 = createMealPlan(client, "Plan 1", "#FF5733");
            createMealPlan(client, "Plan 2", "#33FF57");
            assertThat(getBalance(client).get("used")).isEqualTo(2);

            deleteMealPlan(client, plan1.id());
            assertThat(getBalance(client).get("used")).isEqualTo(1);
        }
    }
}
