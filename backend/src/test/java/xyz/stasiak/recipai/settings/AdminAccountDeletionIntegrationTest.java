package xyz.stasiak.recipai.settings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import xyz.stasiak.recipai.IntegrationTest;
import xyz.stasiak.recipai.TestIdentities;
import xyz.stasiak.recipai.TestRestClients;
import xyz.stasiak.recipai.planning.dto.CreateMealPlanRequest;
import xyz.stasiak.recipai.planning.dto.MealPlanDto;
import xyz.stasiak.recipai.recipes.CreateRecipeRequest;
import xyz.stasiak.recipai.recipes.Ingredient;
import xyz.stasiak.recipai.recipes.Instruction;
import xyz.stasiak.recipai.recipes.RecipeData;
import xyz.stasiak.recipai.recipes.RecipeDetailsDto;
import xyz.stasiak.recipai.recipes.RecipeListDto;
import xyz.stasiak.recipai.recipes.collections.dto.CreateRecipesCollectionRequest;
import xyz.stasiak.recipai.recipes.collections.dto.RecipesCollectionListDto;
import xyz.stasiak.recipai.shoppinglists.dto.CreateShoppingListRequest;
import xyz.stasiak.recipai.shoppinglists.dto.ShoppingListListDto;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@IntegrationTest
class AdminAccountDeletionIntegrationTest {

    /**
     * The dev administrator, allowlisted in application-dev.yml. Shared across tests because it owns
     * nothing - it only ever names other accounts - so sharing it leaks no state.
     */
    private static final String ADMIN = "admin";

    @LocalServerPort
    private int port;

    private String target;
    private String nonAdmin;

    @BeforeEach
    void freshUsers() {
        target = TestIdentities.freshToken();
        nonAdmin = TestIdentities.freshToken();
    }

    private RestClient restClient(String authToken) {
        return TestRestClients.forToken(port, authToken);
    }

    private ResponseEntity<Void> adminDelete(RestClient client, String email) {
        return client
                .delete()
                .uri(b -> b.path("/admin/accounts").queryParam("email", email).build())
                .retrieve()
                .toBodilessEntity();
    }

    private void assertStatus(Runnable call, HttpStatus expected) {
        try {
            call.run();
            fail("Should have thrown exception");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(expected.value());
        }
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

    private void createCollection(RestClient client, String name) {
        client
                .post()
                .uri("/collections")
                .body(new CreateRecipesCollectionRequest(name))
                .retrieve()
                .toBodilessEntity();
    }

    private List<RecipesCollectionListDto> getAllCollections(RestClient client) {
        return client
                .get()
                .uri("/collections")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private void createShoppingList(RestClient client, String name) {
        client
                .post()
                .uri("/shopping-lists")
                .body(new CreateShoppingListRequest(name))
                .retrieve()
                .toBodilessEntity();
    }

    private List<ShoppingListListDto> getAllShoppingLists(RestClient client) {
        return client
                .get()
                .uri("/shopping-lists")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private void createMealPlan(RestClient client, String name) {
        client
                .post()
                .uri("/meal-plans")
                .body(new CreateMealPlanRequest(name, "#FF0000"))
                .retrieve()
                .toBodilessEntity();
    }

    private List<MealPlanDto> getAllMealPlans(RestClient client) {
        return client
                .get()
                .uri("/meal-plans")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    @Test
    void shouldDeleteNamedAccountWhenCallerIsAdmin() {
        RestClient targetClient = restClient(target);
        createRecipe(targetClient, "Recipe");
        createCollection(targetClient, "Collection");
        createShoppingList(targetClient, "List");
        createMealPlan(targetClient, "Plan");

        var response = adminDelete(restClient(ADMIN), TestIdentities.emailOf(target));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(getAllRecipes(targetClient)).isEmpty();
        assertThat(getAllCollections(targetClient)).isEmpty();
        assertThat(getAllShoppingLists(targetClient)).isEmpty();
        assertThat(getAllMealPlans(targetClient)).isEmpty();
    }

    @Test
    void shouldReturn403AndKeepDataWhenCallerIsNotAdmin() {
        RestClient targetClient = restClient(target);
        RecipeDetailsDto recipe = createRecipe(targetClient, "Recipe");

        assertStatus(() -> adminDelete(restClient(nonAdmin), TestIdentities.emailOf(target)), HttpStatus.FORBIDDEN);

        assertThat(getAllRecipes(targetClient)).extracting(RecipeListDto::id).containsExactly(recipe.id());
    }

    @Test
    void shouldReturn403WhenNonAdminTargetsOwnEmail() {
        RestClient nonAdminClient = restClient(nonAdmin);
        RecipeDetailsDto recipe = createRecipe(nonAdminClient, "Recipe");

        assertStatus(() -> adminDelete(nonAdminClient, TestIdentities.emailOf(nonAdmin)), HttpStatus.FORBIDDEN);

        assertThat(getAllRecipes(nonAdminClient)).extracting(RecipeListDto::id).containsExactly(recipe.id());
    }

    @Test
    void shouldReturn204WhenNamedAccountOwnsNothing() {
        var response = adminDelete(restClient(ADMIN), TestIdentities.emailOf(TestIdentities.freshToken()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void shouldReturn400WhenEmailIsMissing() {
        assertStatus(
                () -> restClient(ADMIN).delete().uri("/admin/accounts").retrieve().toBodilessEntity(),
                HttpStatus.BAD_REQUEST
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-email", "  "})
    void shouldReturn400WhenEmailIsMalformed(String email) {
        RestClient targetClient = restClient(target);
        RecipeDetailsDto recipe = createRecipe(targetClient, "Recipe");

        assertStatus(() -> adminDelete(restClient(ADMIN), email), HttpStatus.BAD_REQUEST);

        assertThat(getAllRecipes(targetClient)).extracting(RecipeListDto::id).containsExactly(recipe.id());
    }

    @Test
    void shouldReturn403WhenNonAdminSendsMalformedEmail() {
        // The chain refuses before validation runs, so a non-admin cannot learn the route exists.
        assertStatus(() -> adminDelete(restClient(nonAdmin), "not-an-email"), HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldRejectUnauthenticatedAdminDeletion() {
        RestClient anonymous = restClient(nonAdmin).mutate()
                .defaultHeaders(headers -> headers.remove(HttpHeaders.AUTHORIZATION))
                .build();

        // CSRF protection refuses a token-less state-changing request before authentication runs,
        // so this is 403 rather than 401 - the same as every other unauthenticated DELETE.
        assertStatus(() -> adminDelete(anonymous, TestIdentities.emailOf(target)), HttpStatus.FORBIDDEN);
    }
}
