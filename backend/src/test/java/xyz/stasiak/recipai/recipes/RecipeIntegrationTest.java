package xyz.stasiak.recipai.recipes;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import xyz.stasiak.recipai.RecomputeMigration;
import xyz.stasiak.recipai.TestSecurityConfiguration;
import xyz.stasiak.recipai.TestcontainersConfiguration;
import xyz.stasiak.recipai.limits.LimitBalance;
import xyz.stasiak.recipai.limits.LimitsFacade;
import xyz.stasiak.recipai.permissions.dto.PendingInviteDto;
import xyz.stasiak.recipai.permissions.dto.PermissionDto;
import xyz.stasiak.recipai.permissions.dto.ResourceRole;
import xyz.stasiak.recipai.permissions.dto.ShareRequest;
import xyz.stasiak.recipai.permissions.dto.UnshareRequest;
import xyz.stasiak.recipai.recipes.collections.dto.CreateRecipesCollectionRequest;
import xyz.stasiak.recipai.recipes.collections.dto.RecipesCollectionListDto;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@SuppressWarnings("SameParameterValue")
@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "recipai.limits.enabled=false")
class RecipeIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private RecipeFacade recipeFacade;

    private RestClient restClient() {
        return restClient(TestSecurityConfiguration.AUTH_TOKEN);
    }

    private RestClient restClient(String authToken) {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader("Authorization", "Bearer " + authToken)
                .build();
    }

    private RecipeDetailsDto createRecipe(RestClient client, String name, RecipeData data, UUID collectionId) {
        CreateRecipeRequest request = new CreateRecipeRequest(name, data, collectionId, List.of());
        return client
                .post()
                .uri("/recipes")
                .body(request)
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

    private List<RecipeListDto> getRecipesByCollection(RestClient client, UUID recipesCollectionId) {
        return client
                .get()
                .uri("/recipes?collectionId=" + recipesCollectionId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private List<RecipeListDto> getUnassignedRecipes(RestClient client) {
        return client
                .get()
                .uri("/recipes?unassigned=true")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private RecipeDetailsDto getRecipe(RestClient client, UUID id) {
        return client
                .get()
                .uri("/recipes/" + id)
                .retrieve()
                .body(RecipeDetailsDto.class);
    }

    private RecipeDetailsDto updateRecipe(RestClient client, UUID id, String name, RecipeData data, UUID collectionId) {
        UpdateRecipeRequest request = new UpdateRecipeRequest(name, data, collectionId, List.of());
        return client
                .put()
                .uri("/recipes/" + id)
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

    private void shareRecipe(RestClient client, UUID recipeId, String email) {
        ShareRequest request = new ShareRequest(email, ResourceRole.EDITOR);
        client
                .post()
                .uri("/recipes/" + recipeId + "/share")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    private void unshareRecipe(RestClient client, UUID recipeId, String email) {
        UnshareRequest request = new UnshareRequest(email);
        client
                .post()
                .uri("/recipes/" + recipeId + "/unshare")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    private List<PermissionDto> getPermissions(RestClient client, UUID recipeId) {
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

    private void unshareCollection(RestClient client, UUID collectionId, String email) {
        UnshareRequest request = new UnshareRequest(email);
        client
                .post()
                .uri("/collections/" + collectionId + "/unshare")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    private RecipeData createTestRecipeData() {
        return new RecipeData(
                List.of(new Ingredient("flour", new BigDecimal(300), "g", null)),
                List.of(new Instruction("Make dough")),
                "",
                1
        );
    }

    @Test
    void shouldCreateReadUpdateDeleteRecipes() {
        RestClient client = restClient();

        // CREATE: Create first recipe
        RecipeData pizzaData = new RecipeData(
                List.of(
                        new Ingredient("flour", new BigDecimal(300), "g", null),
                        new Ingredient("tomato sauce", new BigDecimal(200), "ml", null),
                        new Ingredient("mozzarella", new BigDecimal(150), "g", null)
                ),
                List.of(
                        new Instruction("Make dough"),
                        new Instruction("Add sauce and toppings"),
                        new Instruction("Bake for 15 minutes")
                ),
                "",
                4
        );

        RecipeDetailsDto pizzaResponse = createRecipe(client, "Pizza Margherita", pizzaData, null);
        assertThat(pizzaResponse).isNotNull();
        assertThat(pizzaResponse.name()).isEqualTo("Pizza Margherita");

        // CREATE: Create second recipe
        RecipeData pastaData = new RecipeData(
                List.of(
                        new Ingredient("spaghetti", new BigDecimal(400), "g", null),
                        new Ingredient("eggs", new BigDecimal(4), null, null),
                        new Ingredient("pancetta", new BigDecimal(200), "g", null)
                ),
                List.of(
                        new Instruction("Cook pasta"),
                        new Instruction("Fry pancetta"),
                        new Instruction("Mix with eggs")
                ),
                "",
                2
        );

        RecipeDetailsDto pastaResponse = createRecipe(client, "Spaghetti Carbonara", pastaData, null);
        assertThat(pastaResponse).isNotNull();
        assertThat(pastaResponse.name()).isEqualTo("Spaghetti Carbonara");

        // READ: List all recipes - check that our created recipes are present
        List<RecipeListDto> listResponse = getAllRecipes(client);

        assertThat(listResponse).isNotEmpty();
        assertThat(listResponse)
                .extracting(RecipeListDto::id)
                .contains(pizzaResponse.id(), pastaResponse.id());
        assertThat(listResponse)
                .extracting(RecipeListDto::name)
                .contains("Pizza Margherita", "Spaghetti Carbonara");

        // READ: Get detailed recipe
        RecipeDetailsDto detailedRecipe = getRecipe(client, pizzaResponse.id());
        assertThat(detailedRecipe).isNotNull();
        assertThat(detailedRecipe.name()).isEqualTo("Pizza Margherita");
        assertThat(detailedRecipe.data().ingredients()).hasSize(3);
        assertThat(detailedRecipe.data().instructions()).hasSize(3);
        assertThat(detailedRecipe.data().ingredients().getFirst().name()).isEqualTo("flour");
        assertThat(detailedRecipe.data().instructions().getFirst().step()).isEqualTo("Make dough");
        assertThat(detailedRecipe.role()).isEqualTo(ResourceRole.OWNER);

        // UPDATE: Update the pizza recipe
        RecipeData updatedPizzaData = new RecipeData(
                List.of(
                        new Ingredient("flour", new BigDecimal(400), "g", null),
                        new Ingredient("cheese", new BigDecimal(200), "g", null),
                        new Ingredient("tomatoes", new BigDecimal(300), "g", null)
                ),
                List.of(
                        new Instruction("Make better dough"),
                        new Instruction("Add cheese and tomatoes"),
                        new Instruction("Bake for 20 minutes")
                ),
                "",
                6
        );

        RecipeDetailsDto updatedRecipe = updateRecipe(client, pizzaResponse.id(), "Updated Pizza Margherita", updatedPizzaData, null);

        assertThat(updatedRecipe).isNotNull();
        assertThat(updatedRecipe.id()).isEqualTo(pizzaResponse.id());
        assertThat(updatedRecipe.name()).isEqualTo("Updated Pizza Margherita");
        assertThat(updatedRecipe.data().ingredients()).hasSize(3);
        assertThat(updatedRecipe.data().instructions()).hasSize(3);
        assertThat(updatedRecipe.data().ingredients().getFirst().quantity()).isEqualByComparingTo(new BigDecimal(400));

        // READ: Verify GET shows updated data
        RecipeDetailsDto fetchedUpdatedRecipe = getRecipe(client, pizzaResponse.id());
        assertThat(fetchedUpdatedRecipe).isNotNull();
        assertThat(fetchedUpdatedRecipe.name()).isEqualTo("Updated Pizza Margherita");
        assertThat(fetchedUpdatedRecipe.data().ingredients()).hasSize(3);

        // DELETE: Delete the pasta recipe
        deleteRecipe(client, pastaResponse.id());

        // READ: Verify deleted recipe returns 404
        try {
            getRecipe(client, pastaResponse.id());
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(404);
        }

        // READ: Verify updated recipe still exists in list
        List<RecipeListDto> finalListResponse = getAllRecipes(client);

        assertThat(finalListResponse)
                .extracting(RecipeListDto::id)
                .contains(pizzaResponse.id())
                .doesNotContain(pastaResponse.id());
        assertThat(finalListResponse)
                .extracting(RecipeListDto::name)
                .contains("Updated Pizza Margherita")
                .doesNotContain("Spaghetti Carbonara");
    }

    @Test
    void shouldReturn404WhenUpdatingNonExistentRecipe() {
        RestClient client = restClient();
        RecipeData data = new RecipeData(
                List.of(new Ingredient("flour", new BigDecimal(300), "g", null)),
                List.of(new Instruction("Make dough")),
                "",
                1
        );
        UUID randomId = UUID.randomUUID();

        try {
            updateRecipe(client, randomId, "Non-existent", data, null);
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(404);
        }
    }

    @Test
    void shouldReturn404WhenDeletingNonExistentRecipe() {
        RestClient client = restClient();
        UUID randomId = UUID.randomUUID();

        try {
            deleteRecipe(client, randomId);
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(404);
        }
    }

    @Test
    void shouldIsolateRecipesBetweenUsers() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates a recipe
        RecipeData user1RecipeData = new RecipeData(
                List.of(new Ingredient("flour", new BigDecimal(300), "g", null)),
                List.of(new Instruction("Make bread")),
                "",
                1
        );

        RecipeDetailsDto user1Recipe = createRecipe(user1Client, "User 1 Recipe", user1RecipeData, null);
        assertThat(user1Recipe).isNotNull();
        assertThat(user1Recipe.name()).isEqualTo("User 1 Recipe");

        // User 2 creates a recipe
        RecipeData user2RecipeData = new RecipeData(
                List.of(new Ingredient("sugar", new BigDecimal(200), "g", null)),
                List.of(new Instruction("Make cake")),
                "",
                1
        );

        RecipeDetailsDto user2Recipe = createRecipe(user2Client, "User 2 Recipe", user2RecipeData, null);
        assertThat(user2Recipe).isNotNull();
        assertThat(user2Recipe.name()).isEqualTo("User 2 Recipe");

        // User 1 should see their own recipes (including those created in other tests)
        List<RecipeListDto> user1Recipes = getAllRecipes(user1Client);

        assertThat(user1Recipes)
                .extracting(RecipeListDto::id)
                .contains(user1Recipe.id());
        assertThat(user1Recipes)
                .extracting(RecipeListDto::name)
                .contains("User 1 Recipe");

        // User 2 should only see their own recipes
        List<RecipeListDto> user2Recipes = getAllRecipes(user2Client);

        assertThat(user2Recipes)
                .extracting(RecipeListDto::id)
                .contains(user2Recipe.id())
                .doesNotContain(user1Recipe.id());
        assertThat(user2Recipes)
                .extracting(RecipeListDto::name)
                .contains("User 2 Recipe")
                .doesNotContain("User 1 Recipe");
    }

    @Test
    void shouldPreventCrossUserAccess() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates a recipe
        RecipeData recipeData = new RecipeData(
                List.of(new Ingredient("secret ingredient", new BigDecimal(100), "g", null)),
                List.of(new Instruction("Secret recipe step")),
                "",
                1
        );

        RecipeDetailsDto user1Recipe = createRecipe(user1Client, "Secret Recipe", recipeData, null);
        assertThat(user1Recipe).isNotNull();

        // User 2 should not be able to access user 1's recipe
        try {
            getRecipe(user2Client, user1Recipe.id());
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // User 2 should not be able to update user 1's recipe
        try {
            updateRecipe(user2Client, user1Recipe.id(), "Hacked Recipe", recipeData, null);
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // User 2 should not be able to delete user 1's recipe
        try {
            deleteRecipe(user2Client, user1Recipe.id());
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }
    }

    @Test
    void shouldShareAndUnshareRecipes() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates a recipe
        RecipeData recipeData = new RecipeData(
                List.of(new Ingredient("secret ingredient", new BigDecimal(100), "g", null)),
                List.of(new Instruction("Secret recipe step")),
                "",
                1
        );

        RecipeDetailsDto ownerRecipe = createRecipe(user1Client, "Shared Recipe", recipeData, null);
        assertThat(ownerRecipe).isNotNull();
        assertThat(ownerRecipe.role()).isEqualTo(ResourceRole.OWNER);

        // User 2 should not have access initially
        try {
            getRecipe(user2Client, ownerRecipe.id());
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // User 1 shares recipe with User 2 - creates a pending invite, grants nothing yet
        shareRecipe(user1Client, ownerRecipe.id(), "user2@example.com");

        try {
            getRecipe(user2Client, ownerRecipe.id());
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }
        assertThat(getAllRecipes(user2Client)).extracting(RecipeListDto::id).doesNotContain(ownerRecipe.id());

        // Verify the permissions list - owner granted, user2 pending
        List<PermissionDto> permissionsWhilePending = getPermissions(user1Client, ownerRecipe.id());
        assertThat(permissionsWhilePending).containsExactly(
                new PermissionDto("user1@example.com", ResourceRole.OWNER, false),
                new PermissionDto("user2@example.com", ResourceRole.EDITOR, true)
        );

        // User 2 accepts the invite
        acceptPendingRecipeInvite(user2Client, ownerRecipe.name());

        // User 2 should now have EDITOR access
        RecipeDetailsDto sharedRecipe = getRecipe(user2Client, ownerRecipe.id());
        assertThat(sharedRecipe).isNotNull();
        assertThat(sharedRecipe.role()).isEqualTo(ResourceRole.EDITOR);
        assertThat(sharedRecipe.name()).isEqualTo("Shared Recipe");

        // Verify the permissions list now shows both granted, neither pending
        List<PermissionDto> permissionsAfterAccept = getPermissions(user1Client, ownerRecipe.id());
        assertThat(permissionsAfterAccept).containsExactly(
                new PermissionDto("user1@example.com", ResourceRole.OWNER, false),
                new PermissionDto("user2@example.com", ResourceRole.EDITOR, false)
        );

        // User 2 (EDITOR) should be able to update the recipe
        RecipeData updatedData = new RecipeData(
                List.of(new Ingredient("updated ingredient", new BigDecimal(200), "g", null)),
                List.of(new Instruction("Updated recipe step")),
                "",
                2
        );

        RecipeDetailsDto updatedRecipe = updateRecipe(user2Client, ownerRecipe.id(), "Updated Shared Recipe", updatedData, null);
        assertThat(updatedRecipe).isNotNull();
        assertThat(updatedRecipe.name()).isEqualTo("Updated Shared Recipe");
        assertThat(updatedRecipe.role()).isEqualTo(ResourceRole.EDITOR);

        // User 2 (EDITOR) should NOT be able to delete the recipe
        try {
            deleteRecipe(user2Client, ownerRecipe.id());
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // User 1 unshares the recipe from User 2
        unshareRecipe(user1Client, ownerRecipe.id(), "user2@example.com");

        // User 2 should no longer have access
        try {
            getRecipe(user2Client, ownerRecipe.id());
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // Test permissions endpoint after unsharing - should show only OWNER
        List<PermissionDto> permissionsAfterUnsharing = getPermissions(user1Client, ownerRecipe.id());
        assertThat(permissionsAfterUnsharing).containsExactly(
                new PermissionDto("user1@example.com", ResourceRole.OWNER, false)
        );

        // User 1 (OWNER) should still be able to delete the recipe
        deleteRecipe(user1Client, ownerRecipe.id());
    }

    @Test
    void shouldAllowEditorsToShareAndUnshareButPreventUnsharingOwner() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);
        RestClient user3Client = restClient(TestSecurityConfiguration.AUTH_TOKEN);

        // User 1 creates a recipe
        RecipeData recipeData = new RecipeData(
                List.of(new Ingredient("ingredient", new BigDecimal(100), "g", null)),
                List.of(new Instruction("Step")),
                "",
                1
        );

        RecipeDetailsDto ownerRecipe = createRecipe(user1Client, "Editor Sharing Test Recipe", recipeData, null);
        assertThat(ownerRecipe).isNotNull();

        // User 1 shares recipe with User 2 (making User 2 an EDITOR once accepted)
        shareRecipe(user1Client, ownerRecipe.id(), "user2@example.com");
        acceptPendingRecipeInvite(user2Client, ownerRecipe.name());

        // User 2 (EDITOR) should be able to invite a third user - it stays pending
        shareRecipe(user2Client, ownerRecipe.id(), "user@example.com");

        try {
            getRecipe(user3Client, ownerRecipe.id());
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // User 2 (EDITOR) should be able to cancel the pending invite
        unshareRecipe(user2Client, ownerRecipe.id(), "user@example.com");

        // Verify third user no longer has a pending invite
        List<PermissionDto> permissions = getPermissions(user1Client, ownerRecipe.id());
        assertThat(permissions).extracting(PermissionDto::email).doesNotContain("user@example.com");

        // User 2 (EDITOR) should NOT be able to unshare the OWNER (User 1)
        try {
            unshareRecipe(user2Client, ownerRecipe.id(), "user1@example.com");
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // Verify User 1 (OWNER) still has access
        RecipeDetailsDto ownerStillHasAccess = getRecipe(user1Client, ownerRecipe.id());
        assertThat(ownerStillHasAccess).isNotNull();
        assertThat(ownerStillHasAccess.role()).isEqualTo(ResourceRole.OWNER);
    }

    @Test
    void shouldPreventEditorFromUnsharingThemselves() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        RecipeDetailsDto recipe = createRecipe(user1Client, "Self Unshare Test", createTestRecipeData(), null);
        shareRecipe(user1Client, recipe.id(), "user2@example.com");
        acceptPendingRecipeInvite(user2Client, recipe.name());

        // User 2 cannot unshare themselves - the self-unshare guard applies to every role
        try {
            unshareRecipe(user2Client, recipe.id(), "user2@example.com");
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // User 2 still has access
        RecipeDetailsDto sharedRecipe = getRecipe(user2Client, recipe.id());
        assertThat(sharedRecipe).isNotNull();
    }

    @Test
    void shouldRefuseSecondInviteWhenOneIsAlreadyPending() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);

        RecipeDetailsDto recipe = createRecipe(user1Client, "Duplicate Invite Test", createTestRecipeData(), null);
        shareRecipe(user1Client, recipe.id(), "user2@example.com");

        // Share again while the first invite is still pending - refused, not silently ignored
        try {
            shareRecipe(user1Client, recipe.id(), "user2@example.com");
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(409);
            Map<String, Object> body = ex.getResponseBodyAs(new ParameterizedTypeReference<>() {
            });
            assertThat(body.get("reason")).isEqualTo("ALREADY_INVITED");
        }
    }

    @Test
    void shouldRefuseInviteWhenTargetAlreadyHasAccess() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        RecipeDetailsDto recipe = createRecipe(user1Client, "Already Has Access Test", createTestRecipeData(), null);
        shareRecipe(user1Client, recipe.id(), "user2@example.com");
        acceptPendingRecipeInvite(user2Client, recipe.name());

        try {
            shareRecipe(user1Client, recipe.id(), "user2@example.com");
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(409);
            Map<String, Object> body = ex.getResponseBodyAs(new ParameterizedTypeReference<>() {
            });
            assertThat(body.get("reason")).isEqualTo("ALREADY_HAS_ACCESS");
        }

        // Inviting the resource's own owner is the same refusal
        try {
            shareRecipe(user1Client, recipe.id(), "user1@example.com");
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(409);
            Map<String, Object> body = ex.getResponseBodyAs(new ParameterizedTypeReference<>() {
            });
            assertThat(body.get("reason")).isEqualTo("ALREADY_HAS_ACCESS");
        }
    }

    @Test
    void shouldRemovePendingInviteWhenRecipeIsDeleted() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        RecipeDetailsDto recipe = createRecipe(user1Client, "Delete With Pending Invite", createTestRecipeData(), null);
        shareRecipe(user1Client, recipe.id(), "user2@example.com");

        deleteRecipe(user1Client, recipe.id());

        assertThat(getPendingInvites(user2Client))
                .filteredOn(invite -> invite.resourceType().equals("RECIPE") && invite.label().equals(recipe.name()))
                .isEmpty();
    }

    @Test
    void shouldReturn404WhenSharingUnknownRecipe() {
        RestClient client = restClient();
        UUID nonExistentId = UUID.randomUUID();

        try {
            shareRecipe(client, nonExistentId, "user2@example.com");
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(404);
        }
    }

    @Test
    void shouldHandleSharedRecipesInUserRecipeList() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates a recipe
        RecipeData recipeData = new RecipeData(
                List.of(new Ingredient("ingredient", new BigDecimal(100), "g", null)),
                List.of(new Instruction("Step")),
                "",
                1
        );

        RecipeDetailsDto ownerRecipe = createRecipe(user1Client, "Recipe To Be Shared", recipeData, null);
        assertThat(ownerRecipe).isNotNull();

        // User 2 should not see the recipe in their list initially
        List<RecipeListDto> user2RecipesBefore = getAllRecipes(user2Client);

        assertThat(user2RecipesBefore)
                .extracting(RecipeListDto::id)
                .doesNotContain(ownerRecipe.id());

        // User 1 shares recipe with User 2 - stays absent while the invite is pending
        shareRecipe(user1Client, ownerRecipe.id(), "user2@example.com");

        assertThat(getAllRecipes(user2Client))
                .extracting(RecipeListDto::id)
                .doesNotContain(ownerRecipe.id());

        // User 2 accepts - the recipe now appears in their list
        acceptPendingRecipeInvite(user2Client, ownerRecipe.name());

        List<RecipeListDto> user2RecipesAfter = getAllRecipes(user2Client);

        assertThat(user2RecipesAfter)
                .extracting(RecipeListDto::id)
                .contains(ownerRecipe.id());
        assertThat(user2RecipesAfter)
                .extracting(RecipeListDto::name)
                .contains("Recipe To Be Shared");
    }

    @Test
    void shouldCreateRecipeWithCollection() {
        RestClient client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);

        // Create a collection first
        RecipesCollectionListDto collection = createCollection(client, "Italian Recipes");
        assertThat(collection).isNotNull();
        assertThat(collection.name()).isEqualTo("Italian Recipes");

        // Create a recipe with collection assignment
        RecipeData recipeData = createTestRecipeData();

        RecipeDetailsDto response = createRecipe(client, "Pasta", recipeData, collection.id());

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Pasta");
        assertThat(response.collectionId()).isEqualTo(collection.id());
        assertThat(response.collectionName()).isEqualTo("Italian Recipes");
    }

    @Test
    void shouldCreateRecipeWithoutCollection() {
        RestClient client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);

        // Create a recipe without collection assignment
        RecipeData recipeData = createTestRecipeData();

        RecipeDetailsDto response = createRecipe(client, "Standalone Recipe", recipeData, null);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Standalone Recipe");
        assertThat(response.collectionId()).isNull();
        assertThat(response.collectionName()).isNull();
    }

    @Test
    void shouldUpdateRecipeCollectionAssignment() {
        RestClient client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);

        // Create a recipe without collection
        RecipeData recipeData = createTestRecipeData();

        RecipeDetailsDto createdRecipe = createRecipe(client, "Recipe to Assign", recipeData, null);

        assertThat(createdRecipe.collectionId()).isNull();

        // Create a collection
        RecipesCollectionListDto collection = createCollection(client, "My Collection");

        // Update recipe to assign it to the collection
        RecipeDetailsDto updatedRecipe = updateRecipe(client, createdRecipe.id(), "Recipe to Assign", recipeData, collection.id());

        assertThat(updatedRecipe).isNotNull();
        assertThat(updatedRecipe.collectionId()).isEqualTo(collection.id());
        assertThat(updatedRecipe.collectionName()).isEqualTo("My Collection");
    }

    @Test
    void shouldRemoveRecipeFromCollection() {
        RestClient client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);

        // Create a collection
        RecipesCollectionListDto collection = createCollection(client, "Temporary Collection");

        // Create a recipe in the collection
        RecipeData recipeData = createTestRecipeData();

        RecipeDetailsDto createdRecipe = createRecipe(client, "Recipe in Collection", recipeData, collection.id());

        assertThat(createdRecipe.collectionId()).isEqualTo(collection.id());

        // Update recipe to remove it from the collection (set collectionId to null)
        RecipeDetailsDto updatedRecipe = updateRecipe(client, createdRecipe.id(), "Recipe in Collection", recipeData, null);

        assertThat(updatedRecipe).isNotNull();
        assertThat(updatedRecipe.collectionId()).isNull();
        assertThat(updatedRecipe.collectionName()).isNull();
    }

    @Test
    void shouldReturnCollectionNameInRecipeDetail() {
        RestClient client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);

        // Create a collection
        RecipesCollectionListDto collection = createCollection(client, "Desserts");

        // Create a recipe in the collection
        RecipeData recipeData = createTestRecipeData();

        RecipeDetailsDto createdRecipe = createRecipe(client, "Tiramisu", recipeData, collection.id());

        // Fetch the recipe detail via GET endpoint
        RecipeDetailsDto fetchedRecipe = getRecipe(client, createdRecipe.id());

        assertThat(fetchedRecipe).isNotNull();
        assertThat(fetchedRecipe.name()).isEqualTo("Tiramisu");
        assertThat(fetchedRecipe.collectionId()).isEqualTo(collection.id());
        assertThat(fetchedRecipe.collectionName()).isEqualTo("Desserts");
    }

    @Test
    void shouldReturn404WhenAssigningToNonExistentCollection() {
        RestClient client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);

        // Try to create a recipe with a non-existent collection ID
        RecipeData recipeData = createTestRecipeData();
        UUID nonExistentCollectionId = UUID.randomUUID();

        try {
            createRecipe(client, "Invalid Recipe", recipeData, nonExistentCollectionId);
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(404);
        }
    }

    @Test
    void shouldReturn403WhenAssigningToUnauthorizedCollection() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates a collection
        RecipesCollectionListDto user1Collection = createCollection(user1Client, "User 1 Collection");

        // User 2 tries to create a recipe in User 1's collection (should fail with 403)
        RecipeData recipeData = createTestRecipeData();

        try {
            createRecipe(user2Client, "Unauthorized Recipe", recipeData, user1Collection.id());
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }
    }

    @Test
    void shouldFilterRecipesByCollectionId() {
        RestClient client = restClient();

        // Setup: Create collection and recipes
        RecipesCollectionListDto collection = createCollection(client, "Italian");

        RecipeData testData = createTestRecipeData();

        createRecipe(client, "Pizza", testData, collection.id());
        createRecipe(client, "Pasta", testData, collection.id());
        createRecipe(client, "Salad", testData, null); // Unassigned

        // Test: Filter by collectionId

        List<RecipeListDto> filtered = getRecipesByCollection(client, collection.id());

        // Verify: Only recipes in collection returned
        assertThat(filtered).extracting(RecipeListDto::name)
                .contains("Pizza", "Pasta");
    }

    @Test
    void shouldFilterRecipesByUnassigned() {
        RestClient client = restClient();

        // Setup: Create collection and recipes
        RecipesCollectionListDto collection = createCollection(client, "Italian");

        RecipeData testData = createTestRecipeData();

        createRecipe(client, "Pizza", testData, collection.id());
        createRecipe(client, "Salad", testData, null);
        createRecipe(client, "Soup", testData, null);

        // Test: Filter by unassigned=true
        List<RecipeListDto> unassigned = getUnassignedRecipes(client);

        // Verify: Only unassigned recipes returned
        assertThat(unassigned).extracting(RecipeListDto::name)
                .contains("Salad", "Soup");
    }

    @Test
    void shouldReturnAllAccessibleRecipesWhenNoFilter() {
        RestClient client = restClient();

        // Setup: Create recipes with different access paths
        RecipesCollectionListDto collection = createCollection(client, "Italian");

        RecipeData testData = createTestRecipeData();

        // Recipe via recipe permission only
        createRecipe(client, "Salad", testData, null);

        // Recipe via collection permission
        createRecipe(client, "Pizza", testData, collection.id());

        // Test: Get all recipes without filter
        List<RecipeListDto> all = getAllRecipes(client);

        // Verify: Both recipes accessible (via different permission paths)
        assertThat(all).extracting(RecipeListDto::name)
                .contains("Salad", "Pizza");
    }

    @Test
    void shouldListRecipesWhenCallerHasNoAccessibleCollections() {
        RestClient client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);

        RecipeDetailsDto recipe = createRecipe(client, "No Collections At All", createTestRecipeData(), null);

        // The caller holds a direct RECIPE permission and reaches no collection whatsoever - the
        // empty IN :collectionIds branch must not 500.
        List<RecipeListDto> recipes = getAllRecipes(client);

        assertThat(recipes).extracting(RecipeListDto::id).contains(recipe.id());
    }

    @Test
    void shouldListUnassignedRecipesWhenCallerHasNoAccessibleCollections() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        RecipesCollectionListDto collection = createCollection(user1Client, "Collection User 2 Cannot Reach");
        RecipeDetailsDto recipe = createRecipe(user1Client, "Directly Shared But Collection Unreachable", createTestRecipeData(), collection.id());
        shareRecipe(user1Client, recipe.id(), "user2@example.com");
        acceptPendingRecipeInvite(user2Client, recipe.name());

        // User 2 holds a direct RECIPE permission but reaches no collection at all - the empty
        // NOT IN :collectionIds must render as a true predicate, not invalid SQL, and the recipe
        // counts as unassigned from user2's perspective.
        List<RecipeListDto> unassigned = getUnassignedRecipes(user2Client);

        assertThat(unassigned).extracting(RecipeListDto::id).contains(recipe.id());
    }

    @Test
    void shouldExcludeRecipeInAReachableCollectionFromUnassignedList() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        RecipesCollectionListDto collection = createCollection(user1Client, "Reachable Collection For NOT IN Check");
        shareCollection(user1Client, collection.id(), "user2@example.com");
        acceptPendingCollectionInvite(user2Client, collection.name());

        // User 2 directly owns a recipe assigned to a collection they can also reach
        RecipeDetailsDto recipe = createRecipe(user2Client, "Directly Owned In Reachable Collection", createTestRecipeData(), collection.id());

        List<RecipeListDto> unassigned = getUnassignedRecipes(user2Client);

        assertThat(unassigned).extracting(RecipeListDto::id).doesNotContain(recipe.id());
    }

    @Test
    void shouldAccessRecipeInSharedCollection() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // Setup: User1 creates collection and recipe, shares collection with User2
        RecipesCollectionListDto collection = createCollection(user1Client, "Shared");
        RecipeData testData = createTestRecipeData();
        createRecipe(user1Client, "Pizza", testData, collection.id());

        shareCollection(user1Client, collection.id(), "user2@example.com");
        acceptPendingCollectionInvite(user2Client, collection.name());

        // Test: User2 filters by shared collection (no recipe permission needed)
        List<RecipeListDto> user2Recipes = getRecipesByCollection(user2Client, collection.id());

        // Verify: User2 can access recipe via collection permission (NOT recipe permission)
        assertThat(user2Recipes).extracting(RecipeListDto::name)
                .contains("Pizza");
    }

    @Test
    void shouldNotAccessRecipeInCollectionWithoutPermission() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // Setup: User1 creates collection with recipe (not shared)
        RecipesCollectionListDto collection = createCollection(user1Client, "Private");
        RecipeData testData = createTestRecipeData();
        createRecipe(user1Client, "Secret", testData, collection.id());

        // Test: User2 attempts to filter by User1's private collection
        try {
            getRecipesByCollection(user2Client, collection.id());
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            // Verify: 403 Forbidden (no collection permission)
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }
    }

    @Test
    void shouldAccessRecipeDetailInSharedCollectionWithEditorRole() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // Setup: User1 creates collection with recipe
        RecipesCollectionListDto collection = createCollection(user1Client, "Shared Recipes");
        RecipeData testData = createTestRecipeData();
        RecipeDetailsDto createdRecipe = createRecipe(user1Client, "Pasta Carbonara", testData, collection.id());

        // User2 should not have access initially
        try {
            getRecipe(user2Client, createdRecipe.id());
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // User1 shares collection with User2
        shareCollection(user1Client, collection.id(), "user2@example.com");
        acceptPendingCollectionInvite(user2Client, collection.name());

        // Test: User2 should now have access to recipe via shared collection
        RecipeDetailsDto recipeForUser2 = getRecipe(user2Client, createdRecipe.id());

        // Verify: User2 can access the recipe with EDITOR role (via collection)
        assertThat(recipeForUser2).isNotNull();
        assertThat(recipeForUser2.id()).isEqualTo(createdRecipe.id());
        assertThat(recipeForUser2.name()).isEqualTo("Pasta Carbonara");
        assertThat(recipeForUser2.role()).isEqualTo(ResourceRole.EDITOR);
        assertThat(recipeForUser2.collectionId()).isEqualTo(collection.id());
        assertThat(recipeForUser2.collectionName()).isEqualTo("Shared Recipes");
    }

    @Test
    void shouldIgnoreCollectionAssignmentChangeByEditor() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates a recipe without a collection
        RecipeData recipeData = createTestRecipeData();
        RecipeDetailsDto recipe = createRecipe(user1Client, "Pizza", recipeData, null);

        // User 1 shares recipe with User 2 (making User 2 an EDITOR once accepted)
        shareRecipe(user1Client, recipe.id(), "user2@example.com");
        acceptPendingRecipeInvite(user2Client, recipe.name());

        // User 1 creates a collection
        RecipesCollectionListDto collection = createCollection(user1Client, "Italian Recipes");

        // User 1 shares collection with User 2
        shareCollection(user1Client, collection.id(), "user2@example.com");
        acceptPendingCollectionInvite(user2Client, collection.name());

        // User 2 (EDITOR) tries to assign the recipe to the collection - should succeed but ignore the change
        RecipeDetailsDto updatedRecipe = updateRecipe(user2Client, recipe.id(), "Updated Pizza", recipeData, collection.id());

        // Verify the recipe name was updated but collection assignment was ignored
        assertThat(updatedRecipe).isNotNull();
        assertThat(updatedRecipe.name()).isEqualTo("Updated Pizza");
        assertThat(updatedRecipe.collectionId()).isNull(); // Collection assignment ignored

        // Verify via GET request
        RecipeDetailsDto verifyRecipe = getRecipe(user1Client, recipe.id());
        assertThat(verifyRecipe.name()).isEqualTo("Updated Pizza");
        assertThat(verifyRecipe.collectionId()).isNull();
    }

    @Test
    void shouldReturnNullCollectionNameWhenUserLacksCollectionAccess() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates a collection
        RecipesCollectionListDto collection = createCollection(user1Client, "Secret Collection");

        // User 1 creates a recipe in the collection
        RecipeData recipeData = createTestRecipeData();
        RecipeDetailsDto recipe = createRecipe(user1Client, "Secret Recipe", recipeData, collection.id());

        // User 1 shares the recipe (but NOT the collection) with User 2
        shareRecipe(user1Client, recipe.id(), "user2@example.com");
        acceptPendingRecipeInvite(user2Client, recipe.name());

        // User 2 should be able to access the recipe but not see the collection name
        RecipeDetailsDto recipeForUser2 = getRecipe(user2Client, recipe.id());

        assertThat(recipeForUser2).isNotNull();
        assertThat(recipeForUser2.id()).isEqualTo(recipe.id());
        assertThat(recipeForUser2.name()).isEqualTo("Secret Recipe");
        assertThat(recipeForUser2.role()).isEqualTo(ResourceRole.EDITOR);
        assertThat(recipeForUser2.collectionId()).isEqualTo(collection.id());
        assertThat(recipeForUser2.collectionName()).isNull();
    }

    @Test
    void shouldAllowEditorToUpdateRecipeWhileEchoingBackCollectionIdTheyCannotAccess() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates a collection
        RecipesCollectionListDto collection = createCollection(user1Client, "Inaccessible Collection");

        // User 1 creates a recipe in the collection
        RecipeData recipeData = createTestRecipeData();
        RecipeDetailsDto recipe = createRecipe(user1Client, "Editable Secret Recipe", recipeData, collection.id());

        // User 1 shares the recipe (but NOT the collection) with User 2
        shareRecipe(user1Client, recipe.id(), "user2@example.com");
        acceptPendingRecipeInvite(user2Client, recipe.name());

        // User 2 (EDITOR, no access to the collection) echoes the recipe's existing collectionId back
        // in an update. This must not be treated as a move into a collection User 2 can't see - it
        // should succeed like any other in-place edit, since the collection id is discarded for
        // non-owners regardless.
        RecipeDetailsDto updatedRecipe = updateRecipe(user2Client, recipe.id(), "Updated Editable Secret Recipe", recipeData, collection.id());

        assertThat(updatedRecipe).isNotNull();
        assertThat(updatedRecipe.name()).isEqualTo("Updated Editable Secret Recipe");
        assertThat(updatedRecipe.role()).isEqualTo(ResourceRole.EDITOR);
        assertThat(updatedRecipe.collectionId()).isEqualTo(collection.id());
        assertThat(updatedRecipe.collectionName()).isNull();
    }

    @Test
    void shouldRemoveOwnedRecipesFromCollectionWhenUnshared() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates a collection
        CreateRecipesCollectionRequest collectionRequest = new CreateRecipesCollectionRequest("Shared Collection");
        RecipesCollectionListDto collection = user1Client
                .post()
                .uri("/collections")
                .body(collectionRequest)
                .retrieve()
                .body(RecipesCollectionListDto.class);

        // User 1 shares collection with User 2
        assertThat(collection).isNotNull();
        ShareRequest shareRequest = new ShareRequest("user2@example.com", ResourceRole.EDITOR);
        user1Client
                .post()
                .uri("/collections/" + collection.id() + "/share")
                .body(shareRequest)
                .retrieve()
                .toBodilessEntity();
        acceptPendingCollectionInvite(user2Client, collection.name());

        // User 2 creates a recipe in the shared collection
        RecipeData recipeData = createTestRecipeData();
        RecipeDetailsDto recipe = createRecipe(user2Client, "User 2 Recipe", recipeData, collection.id());

        assertThat(recipe).isNotNull();
        assertThat(recipe.collectionId()).isEqualTo(collection.id());

        // User 1 unshares the collection from User 2
        UnshareRequest unshareRequest = new UnshareRequest("user2@example.com");
        user1Client
                .post()
                .uri("/collections/" + collection.id() + "/unshare")
                .body(unshareRequest)
                .retrieve()
                .toBodilessEntity();

        // User 2's recipe should now be removed from the collection
        RecipeDetailsDto updatedRecipe = getRecipe(user2Client, recipe.id());

        assertThat(updatedRecipe).isNotNull();
        assertThat(updatedRecipe.collectionId()).isNull();
        assertThat(updatedRecipe.collectionName()).isNull();
    }

    @Test
    void shouldNotDetachRecipesWhenCancellingAPendingCollectionInvite() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 2 owns a recipe assigned to a collection they already have accepted access to
        RecipesCollectionListDto ownedCollection = createCollection(user1Client, "User 2 Accessible Collection");
        shareCollection(user1Client, ownedCollection.id(), "user2@example.com");
        acceptPendingCollectionInvite(user2Client, ownedCollection.name());
        RecipeDetailsDto recipe = createRecipe(user2Client, "User 2 Recipe Elsewhere", createTestRecipeData(), ownedCollection.id());

        // User 1 invites User 2 to a second, unrelated collection but never gets accepted
        RecipesCollectionListDto pendingCollection = createCollection(user1Client, "Cancelled Before Accept");
        shareCollection(user1Client, pendingCollection.id(), "user2@example.com");

        // Cancelling the still-pending invite must not publish RecipesCollectionUnshared
        unshareCollection(user1Client, pendingCollection.id(), "user2@example.com");

        // The recipe user2 owns elsewhere is untouched
        RecipeDetailsDto stillAssigned = getRecipe(user2Client, recipe.id());
        assertThat(stillAssigned.collectionId()).isEqualTo(ownedCollection.id());
    }

    @Test
    void shouldListCollectionDerivedRecipeWithoutAnyDirectPermission() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        RecipesCollectionListDto collection = createCollection(user1Client, "Collection Only Access");
        RecipeDetailsDto recipe = createRecipe(user1Client, "Collection Derived Recipe", createTestRecipeData(), collection.id());

        shareCollection(user1Client, collection.id(), "user2@example.com");
        acceptPendingCollectionInvite(user2Client, collection.name());

        // User 2 holds no direct RECIPE permission at all - the empty-IN case must not 500
        List<RecipeListDto> user2Recipes = getAllRecipes(user2Client);

        assertThat(user2Recipes).extracting(RecipeListDto::id).contains(recipe.id());
    }

    @Test
    void shouldExcludeCollectionDerivedRecipeFromUnassignedList() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        RecipesCollectionListDto collection = createCollection(user1Client, "Collection Only Access Unassigned");
        RecipeDetailsDto recipe = createRecipe(user1Client, "Collection Derived Recipe Unassigned Check", createTestRecipeData(), collection.id());

        shareCollection(user1Client, collection.id(), "user2@example.com");
        acceptPendingCollectionInvite(user2Client, collection.name());

        // The deliberate short-circuit: collection-derived access never counts as unassigned
        List<RecipeListDto> unassigned = getUnassignedRecipes(user2Client);

        assertThat(unassigned).extracting(RecipeListDto::id).doesNotContain(recipe.id());
    }

    @Test
    void shouldKeepDirectRoleForRecipeAlsoReachableThroughCollection() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        RecipesCollectionListDto collection = createCollection(user1Client, "Shared Collection With Owned Recipe");
        shareCollection(user1Client, collection.id(), "user2@example.com");
        acceptPendingCollectionInvite(user2Client, collection.name());

        // User 2 owns a recipe placed directly into the collection they can also reach
        RecipeDetailsDto recipe = createRecipe(user2Client, "User 2 Owned Recipe", createTestRecipeData(), collection.id());

        // Composition never lowers an answer: the direct OWNER row wins outright
        RecipeDetailsDto fetched = getRecipe(user2Client, recipe.id());
        assertThat(fetched.role()).isEqualTo(ResourceRole.OWNER);
    }

    @Test
    void shouldStillInviteToRecipeAlreadyReachableThroughSharedCollection() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        RecipesCollectionListDto collection = createCollection(user1Client, "Shared Collection For Direct Invite");
        RecipeDetailsDto recipe = createRecipe(user1Client, "Reachable Via Collection", createTestRecipeData(), collection.id());
        shareCollection(user1Client, collection.id(), "user2@example.com");
        acceptPendingCollectionInvite(user2Client, collection.name());

        // User 2 can already reach the recipe through the shared collection (a synthetic EDITOR)
        RecipeDetailsDto beforeInvite = getRecipe(user2Client, recipe.id());
        assertThat(beforeInvite.role()).isEqualTo(ResourceRole.EDITOR);

        // The refusal rules see only granted rows, so the direct invite is still created
        shareRecipe(user1Client, recipe.id(), "user2@example.com");
        acceptPendingRecipeInvite(user2Client, recipe.name());

        // The direct row shadows the composition with the same answer
        RecipeDetailsDto afterAccept = getRecipe(user2Client, recipe.id());
        assertThat(afterAccept.role()).isEqualTo(ResourceRole.EDITOR);
    }

    @Test
    void shouldNotListCollectionDerivedUsersInPermissions() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        RecipesCollectionListDto collection = createCollection(user1Client, "Collection For Permissions Test");
        RecipeDetailsDto recipe = createRecipe(user1Client, "Permissions Test Recipe", createTestRecipeData(), collection.id());
        shareCollection(user1Client, collection.id(), "user2@example.com");

        List<PermissionDto> permissions = getPermissions(user1Client, recipe.id());

        assertThat(permissions).containsExactly(new PermissionDto("user1@example.com", ResourceRole.OWNER, false));
    }

    @Test
    void shouldReturnIngredientsForRecipe() {
        RestClient client = restClient();
        RecipeData data = new RecipeData(
                List.of(new Ingredient("Flour", new BigDecimal(300), "g", null), new Ingredient("Sugar", new BigDecimal(100), "g", null)),
                List.of(new Instruction("Mix")),
                null, 1
        );
        RecipeDetailsDto recipe = createRecipe(client, "Cake", data, null);

        RecipeInfoResult result = recipeFacade.getRecipes(List.of(recipe.id()), "user@example.com");

        assertThat(result.recipes()).hasSize(1);
        assertThat(result.recipes().getFirst().id()).isEqualTo(recipe.id());
        assertThat(result.recipes().getFirst().name()).isEqualTo("Cake");
        assertThat(result.recipes().getFirst().servingSize()).isEqualTo(1);
        assertThat(result.recipes().getFirst().ingredients().get(0)).isEqualTo(new Ingredient("Flour", new BigDecimal(300), "g", null));
        assertThat(result.recipes().getFirst().ingredients().get(1)).isEqualTo(new Ingredient("Sugar", new BigDecimal(100), "g", null));
        assertThat(result.inaccessibleRecipeNames()).isEmpty();
    }

    @Test
    void shouldFlattenIngredientsAcrossMultipleRecipes() {
        RestClient client = restClient();
        RecipeData data1 = new RecipeData(
                List.of(new Ingredient("Eggs", new BigDecimal(3), null, null)),
                List.of(new Instruction("Beat")),
                null, 1
        );
        RecipeData data2 = new RecipeData(
                List.of(new Ingredient("Butter", new BigDecimal(100), "g", null), new Ingredient("Milk", new BigDecimal(200), "ml", null)),
                List.of(new Instruction("Melt")),
                null, 1
        );
        RecipeDetailsDto recipe1 = createRecipe(client, "Omelette", data1, null);
        RecipeDetailsDto recipe2 = createRecipe(client, "Sauce", data2, null);

        RecipeInfoResult result = recipeFacade.getRecipes(List.of(recipe1.id(), recipe2.id()), "user@example.com");

        assertThat(result.recipes()).hasSize(2);
        assertThat(result.recipes().stream().flatMap(r -> r.ingredients().stream()))
                .extracting(Ingredient::name).containsExactly("Eggs", "Butter", "Milk");
        assertThat(result.inaccessibleRecipeNames()).isEmpty();
    }

    @Test
    void shouldReturnEmptyListForUnknownRecipeId() {
        RecipeInfoResult result = recipeFacade.getRecipes(List.of(UUID.randomUUID()), "user@example.com");

        assertThat(result.recipes()).isEmpty();
        assertThat(result.inaccessibleRecipeNames()).isEmpty();
    }

    @Nested
    @TestPropertySource(properties = "recipai.limits.enabled=true")
    class LimitsEnforced {

        private static final String SUBJECT = "user@example.com";

        @Autowired
        private LimitsFacade limitsFacade;

        @Autowired
        private JdbcClient jdbcClient;

        @Autowired
        private DataSource dataSource;

        @BeforeEach
        void setUpQuota() {
            setLimitQuota("RECIPE", SUBJECT, 2);
        }

        @AfterEach
        void tearDown() {
            for (String token : List.of(
                    TestSecurityConfiguration.AUTH_TOKEN,
                    TestSecurityConfiguration.AUTH_TOKEN_USER_1,
                    TestSecurityConfiguration.AUTH_TOKEN_USER_2)) {
                RestClient client = restClient(token);
                for (RecipeListDto recipe : getAllRecipes(client)) {
                    try {
                        deleteRecipe(client, recipe.id());
                    } catch (RestClientResponseException ignored) {
                        // not the owner, ignore
                    }
                }
            }

            // Teardown of rows no API deletes: the config override, and usage fabricated for subjects
            // with no API presence.
            jdbcClient.sql("DELETE FROM recipai.limit_config WHERE resource = 'RECIPE' AND subject IS NOT NULL").update();
            jdbcClient.sql("""
                            DELETE FROM recipai.limit_usage
                             WHERE resource = 'RECIPE' AND subject NOT IN (:subject, :user1, :user2)
                            """)
                    .param("subject", SUBJECT)
                    .param("user1", "user1@example.com")
                    .param("user2", "user2@example.com")
                    .update();

            assertThat(usedFor(SUBJECT)).isZero();
        }

        private int usedFor(String subject) {
            return limitsFacade.getBalance(subject, RecipeService.RECIPE_RESOURCE)
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
            createRecipe(client, "Recipe 1", createTestRecipeData(), null);
            createRecipe(client, "Recipe 2", createTestRecipeData(), null);

            try {
                createRecipe(client, "Recipe 3", createTestRecipeData(), null);
                fail("Should have thrown exception");
            } catch (RestClientResponseException ex) {
                assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
                assertThat(ex.getResponseHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

                Map<String, Object> body = ex.getResponseBodyAs(new ParameterizedTypeReference<Map<String, Object>>() {
                });
                assertThat(body).isNotNull();
                assertThat(body.get("resource")).isEqualTo("RECIPE");
                assertThat(body.get("kind")).isEqualTo("STOCK");
                assertThat(body.get("limit")).isEqualTo(2);
                assertThat(body.get("used")).isEqualTo(2);
            }
        }

        @Test
        void shouldCarryNoRetryAfterOnStockRefusal() {
            RestClient client = restClient();
            createRecipe(client, "Recipe 1", createTestRecipeData(), null);
            createRecipe(client, "Recipe 2", createTestRecipeData(), null);

            try {
                createRecipe(client, "Recipe 3", createTestRecipeData(), null);
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
            RecipeDetailsDto recipe1 = createRecipe(client, "Recipe 1", createTestRecipeData(), null);
            createRecipe(client, "Recipe 2", createTestRecipeData(), null);

            setLimitQuota("RECIPE", SUBJECT, 1);

            RecipeDetailsDto fetched = getRecipe(client, recipe1.id());
            assertThat(fetched.id()).isEqualTo(recipe1.id());

            RecipeDetailsDto updated = updateRecipe(client, recipe1.id(), "Recipe 1 Updated", createTestRecipeData(), null);
            assertThat(updated.name()).isEqualTo("Recipe 1 Updated");

            try {
                createRecipe(client, "Recipe 3", createTestRecipeData(), null);
                fail("Should have thrown exception");
            } catch (RestClientResponseException ex) {
                assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
            }
        }

        @Test
        void shouldAdmitNextCreateAndDropBalanceAfterDelete() {
            RestClient client = restClient();
            RecipeDetailsDto recipe1 = createRecipe(client, "Recipe 1", createTestRecipeData(), null);
            createRecipe(client, "Recipe 2", createTestRecipeData(), null);
            assertThat(usedFor(SUBJECT)).isEqualTo(2);

            deleteRecipe(client, recipe1.id());
            assertThat(usedFor(SUBJECT)).isEqualTo(1);

            createRecipe(client, "Recipe 3", createTestRecipeData(), null);
            assertThat(usedFor(SUBJECT)).isEqualTo(2);
        }

        @Test
        void shouldRollBackReservationWhenCreateFailsAfterReserve() {
            RestClient client = restClient();
            UUID nonExistentCollectionId = UUID.randomUUID();

            try {
                createRecipe(client, "Doomed Recipe", createTestRecipeData(), nonExistentCollectionId);
                fail("Should have thrown exception");
            } catch (RestClientResponseException ex) {
                assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
            }

            assertThat(usedFor(SUBJECT)).isZero();
        }

        @Test
        void shouldLeaveRecipientBalanceUntouchedOnShareAndUnshare() {
            RestClient client = restClient();
            RestClient recipientClient = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);
            RecipeDetailsDto recipe = createRecipe(client, "Shared Recipe", createTestRecipeData(), null);
            assertThat(usedFor(SUBJECT)).isEqualTo(1);

            shareRecipe(client, recipe.id(), "user2@example.com");
            acceptPendingRecipeInvite(recipientClient, recipe.name());
            assertThat(usedFor("user2@example.com")).isZero();

            unshareRecipe(client, recipe.id(), "user2@example.com");
            assertThat(usedFor("user2@example.com")).isZero();
        }

        @Test
        void shouldNotCountPendingInviteTowardsRecipientQuota() {
            RestClient client = restClient();
            RecipeDetailsDto recipe = createRecipe(client, "Shared Recipe", createTestRecipeData(), null);
            assertThat(usedFor(SUBJECT)).isEqualTo(1);

            shareRecipe(client, recipe.id(), "user2@example.com");

            assertThat(usedFor("user2@example.com")).isZero();
            assertThat(usedFor(SUBJECT)).isEqualTo(1);
        }

        @Test
        void shouldRepairDriftToActualOwnedCountViaRecompute() {
            RestClient client = restClient();
            createRecipe(client, "Recipe 1", createTestRecipeData(), null);
            createRecipe(client, "Recipe 2", createTestRecipeData(), null);
            assertThat(usedFor(SUBJECT)).isEqualTo(2);

            // Deliberate drift: no business path can move used away from the owned count.
            jdbcClient.sql("UPDATE recipai.limit_usage SET used = 99 WHERE resource = 'RECIPE' AND subject = :subject")
                    .param("subject", SUBJECT)
                    .update();
            assertThat(usedFor(SUBJECT)).isEqualTo(99);

            RecomputeMigration.run(dataSource);

            assertThat(usedFor(SUBJECT)).isEqualTo(2);
        }

        @Test
        void shouldClearUsageForSubjectThatOwnsNothing() {
            String ghost = "ghost@example.com";
            // A usage row for a subject that owns nothing: no business path leaves one behind.
            jdbcClient.sql("""
                            INSERT INTO recipai.limit_usage (resource, subject, used, period_start)
                            VALUES ('RECIPE', :subject, 5, now())
                            """)
                    .param("subject", ghost)
                    .update();
            assertThat(usedFor(ghost)).isEqualTo(5);

            RecomputeMigration.run(dataSource);

            assertThat(limitsFacade.getBalance(ghost, RecipeService.RECIPE_RESOURCE)).isEmpty();
        }

        @Test
        void shouldSpareFlowConfiguredSubjectFromRecompute() {
            RestClient client = restClient();
            setLimitQuota("RECIPE", SUBJECT, "FLOW", 5);
            try {
                RecipeDetailsDto recipe1 = createRecipe(client, "Recipe 1", createTestRecipeData(), null);
                createRecipe(client, "Recipe 2", createTestRecipeData(), null);
                // A flow release refunds nothing, so the balance stays at 2 while one recipe is owned.
                deleteRecipe(client, recipe1.id());
                assertThat(usedFor(SUBJECT)).isEqualTo(2);

                RecomputeMigration.run(dataSource);

                assertThat(usedFor(SUBJECT)).isEqualTo(2);
            } finally {
                setLimitQuota("RECIPE", SUBJECT, 2);
                limitsFacade.clear(SUBJECT, RecipeService.RECIPE_RESOURCE);
            }
        }

        @Test
        void shouldSpareSubjectWithoutOverrideWhenResourceDefaultIsFlow() {
            String defaultFlowSubject = "user1@example.com";
            RestClient client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
            setLimitQuota("RECIPE", null, "FLOW", 5);
            try {
                RecipeDetailsDto recipe1 = createRecipe(client, "Recipe 1", createTestRecipeData(), null);
                createRecipe(client, "Recipe 2", createTestRecipeData(), null);
                // A flow release refunds nothing, so the balance stays at 2 while one recipe is owned.
                deleteRecipe(client, recipe1.id());
                assertThat(usedFor(defaultFlowSubject)).isEqualTo(2);

                RecomputeMigration.run(dataSource);

                assertThat(usedFor(defaultFlowSubject)).isEqualTo(2);
            } finally {
                setLimitQuota("RECIPE", null, "STOCK", 5);
                limitsFacade.clear(defaultFlowSubject, RecipeService.RECIPE_RESOURCE);
            }
        }

        @Test
        void shouldChangeNothingOnSecondRecomputeRun() {
            RestClient client = restClient();
            createRecipe(client, "Recipe 1", createTestRecipeData(), null);

            RecomputeMigration.run(dataSource);
            int firstRun = usedFor(SUBJECT);

            RecomputeMigration.run(dataSource);
            int secondRun = usedFor(SUBJECT);

            assertThat(secondRun).isEqualTo(firstRun);
            assertThat(secondRun).isEqualTo(1);
        }

        private Map<String, Object> getBalance(RestClient client) {
            return client.get()
                    .uri("/recipes/balance")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        }

        @Test
        void shouldReturnZeroUsageForSubjectThatHasCreatedNothing() {
            Map<String, Object> usage = getBalance(restClient());

            assertThat(usage.get("used")).isEqualTo(0);
        }

        @Test
        void shouldTrackUsageAcrossCreateAndDelete() {
            RestClient client = restClient();
            RecipeDetailsDto recipe1 = createRecipe(client, "Recipe 1", createTestRecipeData(), null);
            createRecipe(client, "Recipe 2", createTestRecipeData(), null);

            assertThat(getBalance(client).get("used")).isEqualTo(2);

            deleteRecipe(client, recipe1.id());

            assertThat(getBalance(client).get("used")).isEqualTo(1);
        }

        @Test
        void shouldMatchUsedCarriedOn429BodyWhenQuotaIsHit() {
            RestClient client = restClient();
            createRecipe(client, "Recipe 1", createTestRecipeData(), null);
            createRecipe(client, "Recipe 2", createTestRecipeData(), null);

            try {
                createRecipe(client, "Recipe 3", createTestRecipeData(), null);
                fail("Should have thrown exception");
            } catch (RestClientResponseException ex) {
                Map<String, Object> body = ex.getResponseBodyAs(new ParameterizedTypeReference<Map<String, Object>>() {
                });
                assertThat(getBalance(client).get("used")).isEqualTo(body.get("used"));
            }
        }
    }
}
