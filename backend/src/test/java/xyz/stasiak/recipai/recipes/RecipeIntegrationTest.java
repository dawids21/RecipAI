package xyz.stasiak.recipai.recipes;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import xyz.stasiak.recipai.TestSecurityConfiguration;
import xyz.stasiak.recipai.TestcontainersConfiguration;
import xyz.stasiak.recipai.recipes.collections.dto.CreateRecipesCollectionRequest;
import xyz.stasiak.recipai.recipes.collections.dto.RecipesCollectionListDto;
import xyz.stasiak.recipai.recipes.collections.dto.ShareRecipesCollectionRequest;
import xyz.stasiak.recipai.recipes.collections.dto.UnshareRecipesCollectionRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("SameParameterValue")
@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecipeIntegrationTest {

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
        ShareRecipeRequest request = new ShareRecipeRequest(email);
        client
                .post()
                .uri("/recipes/" + recipeId + "/share")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    private void unshareRecipe(RestClient client, UUID recipeId, String email) {
        UnshareRecipeRequest request = new UnshareRecipeRequest(email);
        client
                .post()
                .uri("/recipes/" + recipeId + "/unshare")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    private List<SharedUserDto> getSharedUsers(RestClient client, UUID recipeId) {
        return client
                .get()
                .uri("/recipes/" + recipeId + "/shared_users")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
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
        ShareRecipesCollectionRequest request = new ShareRecipesCollectionRequest(email);
        client
                .post()
                .uri("/collections/" + collectionId + "/share")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    private RecipeData createTestRecipeData() {
        return new RecipeData(
                List.of(new Ingredient("flour", "300g", null)),
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
                        new Ingredient("flour", "300g", null),
                        new Ingredient("tomato sauce", "200ml", null),
                        new Ingredient("mozzarella", "150g", null)
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
                        new Ingredient("spaghetti", "400g", null),
                        new Ingredient("eggs", "4", null),
                        new Ingredient("pancetta", "200g", null)
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
        assertThat(detailedRecipe.role()).isEqualTo(UserRole.OWNER);

        // UPDATE: Update the pizza recipe
        RecipeData updatedPizzaData = new RecipeData(
                List.of(
                        new Ingredient("flour", "400g", null),
                        new Ingredient("cheese", "200g", null),
                        new Ingredient("tomatoes", "300g", null)
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
        assertThat(updatedRecipe.data().ingredients().getFirst().quantity()).isEqualTo("400g");

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
            // Should not reach here
            assertThat(false).isTrue();
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
                List.of(new Ingredient("flour", "300g", null)),
                List.of(new Instruction("Make dough")),
                "",
                1
        );
        UUID randomId = UUID.randomUUID();

        try {
            updateRecipe(client, randomId, "Non-existent", data, null);
            // Should not reach here
            assertThat(false).isTrue();
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
            // Should not reach here
            assertThat(false).isTrue();
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
                List.of(new Ingredient("flour", "300g", null)),
                List.of(new Instruction("Make bread")),
                "",
                1
        );

        RecipeDetailsDto user1Recipe = createRecipe(user1Client, "User 1 Recipe", user1RecipeData, null);
        assertThat(user1Recipe).isNotNull();
        assertThat(user1Recipe.name()).isEqualTo("User 1 Recipe");

        // User 2 creates a recipe
        RecipeData user2RecipeData = new RecipeData(
                List.of(new Ingredient("sugar", "200g", null)),
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
                List.of(new Ingredient("secret ingredient", "100g", null)),
                List.of(new Instruction("Secret recipe step")),
                "",
                1
        );

        RecipeDetailsDto user1Recipe = createRecipe(user1Client, "Secret Recipe", recipeData, null);
        assertThat(user1Recipe).isNotNull();

        // User 2 should not be able to access user 1's recipe
        try {
            getRecipe(user2Client, user1Recipe.id());
            // Should not reach here
            assertThat(false).isTrue();
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // User 2 should not be able to update user 1's recipe
        try {
            updateRecipe(user2Client, user1Recipe.id(), "Hacked Recipe", recipeData, null);
            // Should not reach here
            assertThat(false).isTrue();
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // User 2 should not be able to delete user 1's recipe
        try {
            deleteRecipe(user2Client, user1Recipe.id());
            // Should not reach here
            assertThat(false).isTrue();
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
                List.of(new Ingredient("secret ingredient", "100g", null)),
                List.of(new Instruction("Secret recipe step")),
                "",
                1
        );

        RecipeDetailsDto ownerRecipe = createRecipe(user1Client, "Shared Recipe", recipeData, null);
        assertThat(ownerRecipe).isNotNull();
        assertThat(ownerRecipe.role()).isEqualTo(UserRole.OWNER);

        // User 2 should not have access initially
        try {
            getRecipe(user2Client, ownerRecipe.id());
            assertThat(false).isTrue(); // Should not reach here
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // User 1 shares recipe with User 2
        shareRecipe(user1Client, ownerRecipe.id(), "user2@example.com");

        // User 2 should now have EDITOR access
        RecipeDetailsDto sharedRecipe = getRecipe(user2Client, ownerRecipe.id());
        assertThat(sharedRecipe).isNotNull();
        assertThat(sharedRecipe.role()).isEqualTo(UserRole.EDITOR);
        assertThat(sharedRecipe.name()).isEqualTo("Shared Recipe");

        // Test shared users endpoint after sharing - should show OWNER first, then EDITOR
        List<SharedUserDto> sharedUsersAfterSharing = getSharedUsers(user1Client, ownerRecipe.id());
        assertThat(sharedUsersAfterSharing).isNotNull();
        assertThat(sharedUsersAfterSharing).hasSize(2);
        assertThat(sharedUsersAfterSharing.get(0).email()).isEqualTo("user1@example.com");
        assertThat(sharedUsersAfterSharing.get(0).role()).isEqualTo(UserRole.OWNER);
        assertThat(sharedUsersAfterSharing.get(1).email()).isEqualTo("user2@example.com");
        assertThat(sharedUsersAfterSharing.get(1).role()).isEqualTo(UserRole.EDITOR);

        // User 2 (EDITOR) should be able to update the recipe
        RecipeData updatedData = new RecipeData(
                List.of(new Ingredient("updated ingredient", "200g", null)),
                List.of(new Instruction("Updated recipe step")),
                "",
                2
        );

        RecipeDetailsDto updatedRecipe = updateRecipe(user2Client, ownerRecipe.id(), "Updated Shared Recipe", updatedData, null);
        assertThat(updatedRecipe).isNotNull();
        assertThat(updatedRecipe.name()).isEqualTo("Updated Shared Recipe");
        assertThat(updatedRecipe.role()).isEqualTo(UserRole.EDITOR);

        // User 2 (EDITOR) should NOT be able to delete the recipe
        try {
            deleteRecipe(user2Client, ownerRecipe.id());
            assertThat(false).isTrue(); // Should not reach here
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // User 1 unshares the recipe from User 2
        unshareRecipe(user1Client, ownerRecipe.id(), "user2@example.com");

        // User 2 should no longer have access
        try {
            getRecipe(user2Client, ownerRecipe.id());
            assertThat(false).isTrue(); // Should not reach here
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // Test shared users endpoint after unsharing - should show only OWNER
        List<SharedUserDto> sharedUsersAfterUnsharing = getSharedUsers(user1Client, ownerRecipe.id());
        assertThat(sharedUsersAfterUnsharing).isNotNull();
        assertThat(sharedUsersAfterUnsharing).hasSize(1);
        assertThat(sharedUsersAfterUnsharing.getFirst().email()).isEqualTo("user1@example.com");
        assertThat(sharedUsersAfterUnsharing.getFirst().role()).isEqualTo(UserRole.OWNER);

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
                List.of(new Ingredient("ingredient", "100g", null)),
                List.of(new Instruction("Step")),
                "",
                1
        );

        RecipeDetailsDto ownerRecipe = createRecipe(user1Client, "Editor Sharing Test Recipe", recipeData, null);
        assertThat(ownerRecipe).isNotNull();

        // User 1 shares recipe with User 2 (making User 2 an EDITOR)
        shareRecipe(user1Client, ownerRecipe.id(), "user2@example.com");

        // User 2 (EDITOR) should be able to share the recipe with a third user
        shareRecipe(user2Client, ownerRecipe.id(), "user@example.com");

        // Verify the third user now has access
        RecipeDetailsDto thirdUserRecipe = getRecipe(user3Client, ownerRecipe.id());
        assertThat(thirdUserRecipe).isNotNull();
        assertThat(thirdUserRecipe.role()).isEqualTo(UserRole.EDITOR);

        // User 2 (EDITOR) should be able to unshare the recipe from the third user
        unshareRecipe(user2Client, ownerRecipe.id(), "user@example.com");

        // Verify third user no longer has access
        try {
            getRecipe(user3Client, ownerRecipe.id());
            assertThat(false).isTrue(); // Should not reach here
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // User 2 (EDITOR) should NOT be able to unshare the OWNER (User 1)
        try {
            unshareRecipe(user2Client, ownerRecipe.id(), "user1@example.com");
            assertThat(false).isTrue(); // Should not reach here
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // Verify User 1 (OWNER) still has access
        RecipeDetailsDto ownerStillHasAccess = getRecipe(user1Client, ownerRecipe.id());
        assertThat(ownerStillHasAccess).isNotNull();
        assertThat(ownerStillHasAccess.role()).isEqualTo(UserRole.OWNER);
    }

    @Test
    void shouldHandleSharedRecipesInUserRecipeList() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates a recipe
        RecipeData recipeData = new RecipeData(
                List.of(new Ingredient("ingredient", "100g", null)),
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

        // User 1 shares recipe with User 2
        shareRecipe(user1Client, ownerRecipe.id(), "user2@example.com");

        // User 2 should now see the recipe in their list
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
            // Should not reach here
            assertThat(false).isTrue();
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
            // Should not reach here
            assertThat(false).isTrue();
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
    void shouldAccessRecipeInSharedCollection() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // Setup: User1 creates collection and recipe, shares collection with User2
        RecipesCollectionListDto collection = createCollection(user1Client, "Shared");
        RecipeData testData = createTestRecipeData();
        createRecipe(user1Client, "Pizza", testData, collection.id());

        shareCollection(user1Client, collection.id(), "user2@example.com");

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
            // Should not reach here
            assertThat(false).isTrue();
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
            assertThat(false).isTrue(); // Should not reach here
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // User1 shares collection with User2
        shareCollection(user1Client, collection.id(), "user2@example.com");

        // Test: User2 should now have access to recipe via shared collection
        RecipeDetailsDto recipeForUser2 = getRecipe(user2Client, createdRecipe.id());

        // Verify: User2 can access the recipe with EDITOR role (via collection)
        assertThat(recipeForUser2).isNotNull();
        assertThat(recipeForUser2.id()).isEqualTo(createdRecipe.id());
        assertThat(recipeForUser2.name()).isEqualTo("Pasta Carbonara");
        assertThat(recipeForUser2.role()).isEqualTo(UserRole.EDITOR);
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

        // User 1 shares recipe with User 2 (making User 2 an EDITOR)
        shareRecipe(user1Client, recipe.id(), "user2@example.com");

        // User 1 creates a collection
        RecipesCollectionListDto collection = createCollection(user1Client, "Italian Recipes");

        // User 1 shares collection with User 2
        shareCollection(user1Client, collection.id(), "user2@example.com");

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

        // User 2 should be able to access the recipe but not see the collection name
        RecipeDetailsDto recipeForUser2 = getRecipe(user2Client, recipe.id());

        assertThat(recipeForUser2).isNotNull();
        assertThat(recipeForUser2.id()).isEqualTo(recipe.id());
        assertThat(recipeForUser2.name()).isEqualTo("Secret Recipe");
        assertThat(recipeForUser2.role()).isEqualTo(UserRole.EDITOR);
        assertThat(recipeForUser2.collectionId()).isEqualTo(collection.id());
        assertThat(recipeForUser2.collectionName()).isNull();
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
        ShareRecipesCollectionRequest shareRequest = new ShareRecipesCollectionRequest("user2@example.com");
        user1Client
                .post()
                .uri("/collections/" + collection.id() + "/share")
                .body(shareRequest)
                .retrieve()
                .toBodilessEntity();

        // User 2 creates a recipe in the shared collection
        RecipeData recipeData = createTestRecipeData();
        RecipeDetailsDto recipe = createRecipe(user2Client, "User 2 Recipe", recipeData, collection.id());

        assertThat(recipe).isNotNull();
        assertThat(recipe.collectionId()).isEqualTo(collection.id());

        // User 1 unshares the collection from User 2
        UnshareRecipesCollectionRequest unshareRequest = new UnshareRecipesCollectionRequest("user2@example.com");
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
}