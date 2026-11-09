package xyz.stasiak.recipai.settings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import xyz.stasiak.recipai.IntegrationTest;
import xyz.stasiak.recipai.LimitsEnabled;
import xyz.stasiak.recipai.TestIdentities;
import xyz.stasiak.recipai.TestRestClients;
import xyz.stasiak.recipai.limits.LimitQuota;
import xyz.stasiak.recipai.limits.LimitsFacade;
import xyz.stasiak.recipai.permissions.dto.PendingInviteDto;
import xyz.stasiak.recipai.permissions.dto.PermissionDto;
import xyz.stasiak.recipai.permissions.dto.ResourceRole;
import xyz.stasiak.recipai.permissions.dto.ShareRequest;
import xyz.stasiak.recipai.planning.dto.CreateMealPlanEntryRequest;
import xyz.stasiak.recipai.planning.dto.CreateMealPlanRequest;
import xyz.stasiak.recipai.planning.dto.MealPlanCalendarViewDto;
import xyz.stasiak.recipai.planning.dto.MealPlanDto;
import xyz.stasiak.recipai.planning.dto.MealPlanEntryDto;
import xyz.stasiak.recipai.recipes.CreateRecipeRequest;
import xyz.stasiak.recipai.recipes.Ingredient;
import xyz.stasiak.recipai.recipes.Instruction;
import xyz.stasiak.recipai.recipes.RecipeData;
import xyz.stasiak.recipai.recipes.RecipeDetailsDto;
import xyz.stasiak.recipai.recipes.RecipeListDto;
import xyz.stasiak.recipai.recipes.collections.dto.CreateRecipesCollectionRequest;
import xyz.stasiak.recipai.recipes.collections.dto.RecipesCollectionListDto;
import xyz.stasiak.recipai.shoppinglists.dto.CreateShoppingListItemRequest;
import xyz.stasiak.recipai.shoppinglists.dto.CreateShoppingListRequest;
import xyz.stasiak.recipai.shoppinglists.dto.ShoppingListDto;
import xyz.stasiak.recipai.shoppinglists.dto.ShoppingListItemDto;
import xyz.stasiak.recipai.shoppinglists.dto.ShoppingListListDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

@IntegrationTest
class AccountDeletionIntegrationTest {

    private static final LocalDate PLAN_DATE = LocalDate.of(2026, 1, 29);

    @LocalServerPort
    private int port;

    @Autowired
    private LimitsFacade limitsFacade;

    @Autowired
    private AccountDeletionService accountDeletionService;

    private String owner;
    private String editor;
    private String stranger;

    @BeforeEach
    void freshUsers() {
        owner = TestIdentities.freshToken();
        editor = TestIdentities.freshToken();
        stranger = TestIdentities.freshToken();
    }

    private RestClient restClient(String authToken) {
        return TestRestClients.forToken(port, authToken);
    }

    private void deleteAccount(RestClient client) {
        client
                .delete()
                .uri("/settings/account")
                .retrieve()
                .toBodilessEntity();
    }

    private RecipeDetailsDto createRecipe(RestClient client, String name) {
        RecipeData data = new RecipeData(
                List.of(new Ingredient("flour", new BigDecimal(300), "g", null)),
                List.of(new Instruction("Make dough")),
                "",
                1
        );
        return client
                .post()
                .uri("/recipes")
                .body(new CreateRecipeRequest(name, data, null, List.of()))
                .retrieve()
                .body(RecipeDetailsDto.class);
    }

    private List<RecipeListDto> getAllRecipes(RestClient client) {
        return client
                .get()
                .uri("/recipes")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private void shareRecipe(RestClient client, UUID recipeId, String email) {
        client
                .post()
                .uri("/recipes/" + recipeId + "/share")
                .body(new ShareRequest(email, ResourceRole.EDITOR))
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

    private RecipesCollectionListDto createCollection(RestClient client, String name) {
        return client
                .post()
                .uri("/collections")
                .body(new CreateRecipesCollectionRequest(name))
                .retrieve()
                .body(RecipesCollectionListDto.class);
    }

    private List<RecipesCollectionListDto> getAllCollections(RestClient client) {
        return client
                .get()
                .uri("/collections")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private ShoppingListListDto createShoppingList(RestClient client, String name) {
        return client
                .post()
                .uri("/shopping-lists")
                .body(new CreateShoppingListRequest(name))
                .retrieve()
                .body(ShoppingListListDto.class);
    }

    private ShoppingListItemDto createShoppingListItem(RestClient client, UUID listId, String name, int position) {
        return client
                .post()
                .uri("/shopping-lists/" + listId + "/items")
                .body(new CreateShoppingListItemRequest(name, BigDecimal.ONE, "pcs", false, BigDecimal.valueOf(position)))
                .retrieve()
                .body(ShoppingListItemDto.class);
    }

    private ShoppingListDto getShoppingList(RestClient client, UUID listId) {
        return client
                .get()
                .uri("/shopping-lists/" + listId)
                .retrieve()
                .body(ShoppingListDto.class);
    }

    private List<ShoppingListListDto> getAllShoppingLists(RestClient client) {
        return client
                .get()
                .uri("/shopping-lists")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private void shareShoppingList(RestClient client, UUID listId, String email) {
        client
                .post()
                .uri("/shopping-lists/" + listId + "/share")
                .body(new ShareRequest(email, ResourceRole.EDITOR))
                .retrieve()
                .toBodilessEntity();
    }

    private List<PermissionDto> getShoppingListPermissions(RestClient client, UUID listId) {
        return client
                .get()
                .uri("/shopping-lists/" + listId + "/permissions")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private MealPlanDto createMealPlan(RestClient client, String name) {
        return client
                .post()
                .uri("/meal-plans")
                .body(new CreateMealPlanRequest(name, "#FF0000"))
                .retrieve()
                .body(MealPlanDto.class);
    }

    private MealPlanEntryDto createMealPlanEntry(RestClient client, UUID planId, UUID recipeId) {
        return client
                .post()
                .uri("/meal-plans/" + planId + "/entries")
                .body(new CreateMealPlanEntryRequest(PLAN_DATE, recipeId, null, 2))
                .retrieve()
                .body(MealPlanEntryDto.class);
    }

    private List<MealPlanDto> getAllMealPlans(RestClient client) {
        return client
                .get()
                .uri("/meal-plans")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private Map<LocalDate, List<MealPlanCalendarViewDto>> getCalendarView(RestClient client, UUID planId) {
        return client
                .get()
                .uri("/meal-plans/calendar?startDate=" + PLAN_DATE + "&endDate=" + PLAN_DATE + "&planIds=" + planId)
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

    // Every subject is fresh, so the one pending invite is the one the test just sent.
    private void acceptOnlyPendingInvite(RestClient client) {
        UUID inviteId = getPendingInvites(client).getFirst().id();
        client
                .post()
                .uri("/invites/" + inviteId + "/accept")
                .retrieve()
                .toBodilessEntity();
    }

    @Test
    void shouldReturnNoContentWhenAccountOwnsNothing() {
        var response = restClient(owner)
                .delete()
                .uri("/settings/account")
                .retrieve()
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void shouldRejectUnauthenticatedDeletion() {
        RestClient anonymous = restClient(owner).mutate()
                .defaultHeaders(headers -> headers.remove(HttpHeaders.AUTHORIZATION))
                .build();

        try {
            deleteAccount(anonymous);
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            // CSRF protection refuses a token-less state-changing request before authentication runs,
            // so this is 403 rather than 401 - the same as every other unauthenticated DELETE.
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.FORBIDDEN.value());
        }
    }

    @Test
    void shouldRemoveEveryOwnedResourceWhenAccountIsDeleted() {
        RestClient client = restClient(owner);
        createRecipe(client, "Recipe");
        createCollection(client, "Collection");
        ShoppingListListDto list = createShoppingList(client, "List");
        createShoppingListItem(client, list.id(), "Milk", 1);
        createShoppingListItem(client, list.id(), "Eggs", 2);
        createMealPlan(client, "Plan");

        deleteAccount(client);

        assertThat(getAllRecipes(client)).isEmpty();
        assertThat(getAllCollections(client)).isEmpty();
        assertThat(getAllShoppingLists(client)).isEmpty();
        assertThat(getAllMealPlans(client)).isEmpty();
    }

    @Test
    void shouldRemoveSharedResourceForItsEditorWhenOwnerIsDeleted() {
        RestClient ownerClient = restClient(owner);
        RestClient editorClient = restClient(editor);
        RecipeDetailsDto recipe = createRecipe(ownerClient, "Shared Recipe");
        shareRecipe(ownerClient, recipe.id(), TestIdentities.emailOf(editor));
        acceptOnlyPendingInvite(editorClient);
        assertThat(getAllRecipes(editorClient)).extracting(RecipeListDto::id).containsExactly(recipe.id());

        deleteAccount(ownerClient);

        assertThat(getAllRecipes(editorClient)).isEmpty();
        try {
            editorClient.get().uri("/recipes/" + recipe.id()).retrieve().toBodilessEntity();
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
        }
    }

    @Test
    void shouldRemoveInvitesAddressedToTheDeletedAccount() {
        RestClient strangerClient = restClient(stranger);
        String ownerEmail = TestIdentities.emailOf(owner);
        RecipeDetailsDto recipe = createRecipe(strangerClient, "Stranger's Recipe");
        shareRecipe(strangerClient, recipe.id(), ownerEmail);
        assertThat(getRecipePermissions(strangerClient, recipe.id())).extracting(PermissionDto::email).contains(ownerEmail);

        deleteAccount(restClient(owner));

        assertThat(getRecipePermissions(strangerClient, recipe.id())).extracting(PermissionDto::email).doesNotContain(ownerEmail);
    }

    @Test
    void shouldRemoveInvitesSentByTheDeletedAccount() {
        RestClient ownerClient = restClient(owner);
        RestClient strangerClient = restClient(stranger);
        RecipeDetailsDto recipe = createRecipe(ownerClient, "Owner's Recipe");
        shareRecipe(ownerClient, recipe.id(), TestIdentities.emailOf(stranger));
        assertThat(getPendingInvites(strangerClient)).hasSize(1);

        deleteAccount(ownerClient);

        assertThat(getPendingInvites(strangerClient)).isEmpty();
    }

    @Test
    void shouldRewriteThirdPartyMealPlanEntriesWhenOwnersRecipeIsPurged() {
        RestClient ownerClient = restClient(owner);
        RestClient editorClient = restClient(editor);
        RecipeDetailsDto recipe = createRecipe(ownerClient, "Shared Recipe");
        shareRecipe(ownerClient, recipe.id(), TestIdentities.emailOf(editor));
        acceptOnlyPendingInvite(editorClient);
        MealPlanDto editorPlan = createMealPlan(editorClient, "Editor's Plan");
        createMealPlanEntry(editorClient, editorPlan.id(), recipe.id());

        deleteAccount(ownerClient);

        List<MealPlanCalendarViewDto> entries = getCalendarView(editorClient, editorPlan.id()).get(PLAN_DATE);
        assertThat(entries).hasSize(1);
        MealPlanCalendarViewDto entry = entries.getFirst();
        assertThat(entry.placeholderText()).isEqualTo(recipe.name());
        assertThat(entry.recipeId()).isNull();
        assertThat(entry.servingSize()).isNull();
    }

    @Test
    void shouldLeaveOtherPeoplesResourcesIntact() {
        RestClient ownerClient = restClient(owner);
        RestClient strangerClient = restClient(stranger);
        String ownerEmail = TestIdentities.emailOf(owner);
        ShoppingListListDto list = createShoppingList(strangerClient, "Stranger's List");
        createShoppingListItem(strangerClient, list.id(), "Milk", 1);
        createShoppingListItem(strangerClient, list.id(), "Eggs", 2);
        shareShoppingList(strangerClient, list.id(), ownerEmail);
        acceptOnlyPendingInvite(ownerClient);
        assertThat(getShoppingListPermissions(strangerClient, list.id())).extracting(PermissionDto::email).contains(ownerEmail);

        deleteAccount(ownerClient);

        ShoppingListDto survivor = getShoppingList(strangerClient, list.id());
        assertThat(survivor.items()).extracting(ShoppingListItemDto::name).containsExactlyInAnyOrder("Milk", "Eggs");
        assertThat(getShoppingListPermissions(strangerClient, list.id())).extracting(PermissionDto::email).doesNotContain(ownerEmail);
    }

    @Test
    void shouldResetUsageWhenAccountIsDeleted() {
        RestClient client = restClient(owner);
        String ownerEmail = TestIdentities.emailOf(owner);
        createRecipe(client, "Recipe");
        createCollection(client, "Collection");
        createMealPlan(client, "Plan");
        ShoppingListListDto list = createShoppingList(client, "List");
        createShoppingListItem(client, list.id(), "Milk", 1);
        assertThat(limitsFacade.getBalance(ownerEmail, "RECIPE")).isPresent();
        assertThat(limitsFacade.getBalance(list.id().toString(), "SHOPPING_LIST_ITEM")).isPresent();

        deleteAccount(client);

        assertThat(limitsFacade.getBalance(ownerEmail, "RECIPE")).isEmpty();
        assertThat(limitsFacade.getBalance(ownerEmail, "RECIPES_COLLECTION")).isEmpty();
        assertThat(limitsFacade.getBalance(ownerEmail, "SHOPPING_LIST")).isEmpty();
        assertThat(limitsFacade.getBalance(ownerEmail, "MEAL_PLAN")).isEmpty();
        assertThat(limitsFacade.getBalance(list.id().toString(), "SHOPPING_LIST_ITEM")).isEmpty();
    }

    @Test
    void shouldSucceedWhenCalledTwice() {
        RestClient client = restClient(owner);
        createRecipe(client, "Recipe");
        deleteAccount(client);

        var response = client
                .delete()
                .uri("/settings/account")
                .retrieve()
                .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void shouldRefuseDeletionWhenEmailIsMissing(String email) {
        assertThatThrownBy(() -> accountDeletionService.deleteAccount(email))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Nested
    @LimitsEnabled
    class QuotaOverrideCleared {

        @Autowired
        private JdbcClient jdbcClient;

        /**
         * Inserts a per-subject override: {@code limit_config} has no write API, so there is no business path to it.
         */
        private void setLimitQuota(String resource, String subject, int maxValue) {
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
        void shouldRestoreDefaultQuotaWhenAccountIsDeleted() {
            String ownerEmail = TestIdentities.emailOf(owner);
            int defaultLimit = limitsFacade.getQuota(TestIdentities.emailOf(stranger), "RECIPE")
                    .map(LimitQuota::limit)
                    .orElseThrow();
            int override = defaultLimit + 37;
            setLimitQuota("RECIPE", ownerEmail, override);
            assertThat(limitsFacade.getQuota(ownerEmail, "RECIPE")).map(LimitQuota::limit).contains(override);

            deleteAccount(restClient(owner));

            assertThat(limitsFacade.getQuota(ownerEmail, "RECIPE")).map(LimitQuota::limit).contains(defaultLimit);
        }
    }
}
