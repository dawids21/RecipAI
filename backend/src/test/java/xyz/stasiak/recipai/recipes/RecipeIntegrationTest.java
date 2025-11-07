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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
                )
        );
        CreateRecipeRequest pizzaRequest = new CreateRecipeRequest("Pizza Margherita", pizzaData);

        RecipeDto pizzaResponse = client
                .post()
                .uri("/recipes")
                .body(pizzaRequest)
                .retrieve()
                .body(RecipeDto.class);
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
                )
        );
        CreateRecipeRequest pastaRequest = new CreateRecipeRequest("Spaghetti Carbonara", pastaData);

        RecipeDto pastaResponse = client
                .post()
                .uri("/recipes")
                .body(pastaRequest)
                .retrieve()
                .body(RecipeDto.class);
        assertThat(pastaResponse).isNotNull();
        assertThat(pastaResponse.name()).isEqualTo("Spaghetti Carbonara");

        // READ: List all recipes - check that our created recipes are present
        List<RecipeListDto> listResponse = client
                .get()
                .uri("/recipes")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        assertThat(listResponse).isNotEmpty();
        assertThat(listResponse)
                .extracting(RecipeListDto::id)
                .contains(pizzaResponse.id(), pastaResponse.id());
        assertThat(listResponse)
                .extracting(RecipeListDto::name)
                .contains("Pizza Margherita", "Spaghetti Carbonara");

        // READ: Get detailed recipe
        RecipeDto detailedRecipe = client
                .get()
                .uri("/recipes/" + pizzaResponse.id())
                .retrieve()
                .body(RecipeDto.class);
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
                )
        );
        UpdateRecipeRequest updateRequest = new UpdateRecipeRequest("Updated Pizza Margherita", updatedPizzaData);

        RecipeDto updatedRecipe = client
                .put()
                .uri("/recipes/" + pizzaResponse.id())
                .body(updateRequest)
                .retrieve()
                .body(RecipeDto.class);

        assertThat(updatedRecipe).isNotNull();
        assertThat(updatedRecipe.id()).isEqualTo(pizzaResponse.id());
        assertThat(updatedRecipe.name()).isEqualTo("Updated Pizza Margherita");
        assertThat(updatedRecipe.data().ingredients()).hasSize(3);
        assertThat(updatedRecipe.data().instructions()).hasSize(3);
        assertThat(updatedRecipe.data().ingredients().getFirst().quantity()).isEqualTo("400g");

        // READ: Verify GET shows updated data
        RecipeDto fetchedUpdatedRecipe = client
                .get()
                .uri("/recipes/" + pizzaResponse.id())
                .retrieve()
                .body(RecipeDto.class);
        assertThat(fetchedUpdatedRecipe).isNotNull();
        assertThat(fetchedUpdatedRecipe.name()).isEqualTo("Updated Pizza Margherita");
        assertThat(fetchedUpdatedRecipe.data().ingredients()).hasSize(3);

        // DELETE: Delete the pasta recipe
        client
                .delete()
                .uri("/recipes/" + pastaResponse.id())
                .retrieve()
                .toBodilessEntity();

        // READ: Verify deleted recipe returns 404
        try {
            client
                    .get()
                    .uri("/recipes/" + pastaResponse.id())
                    .retrieve()
                    .body(RecipeDto.class);
            // Should not reach here
            assertThat(false).isTrue();
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(404);
        }

        // READ: Verify updated recipe still exists in list
        List<RecipeListDto> finalListResponse = client
                .get()
                .uri("/recipes")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

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
                List.of(new Instruction("Make dough"))
        );
        UpdateRecipeRequest updateRequest = new UpdateRecipeRequest("Non-existent", data);
        UUID randomId = UUID.randomUUID();

        try {
            client
                    .put()
                    .uri("/recipes/" + randomId)
                    .body(updateRequest)
                    .retrieve()
                    .body(RecipeDto.class);
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
            client
                    .delete()
                    .uri("/recipes/" + randomId)
                    .retrieve()
                    .toBodilessEntity();
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
                List.of(new Instruction("Make bread"))
        );
        CreateRecipeRequest user1Request = new CreateRecipeRequest("User 1 Recipe", user1RecipeData);

        RecipeDto user1Recipe = user1Client
                .post()
                .uri("/recipes")
                .body(user1Request)
                .retrieve()
                .body(RecipeDto.class);
        assertThat(user1Recipe).isNotNull();
        assertThat(user1Recipe.name()).isEqualTo("User 1 Recipe");

        // User 2 creates a recipe
        RecipeData user2RecipeData = new RecipeData(
                List.of(new Ingredient("sugar", "200g", null)),
                List.of(new Instruction("Make cake"))
        );
        CreateRecipeRequest user2Request = new CreateRecipeRequest("User 2 Recipe", user2RecipeData);

        RecipeDto user2Recipe = user2Client
                .post()
                .uri("/recipes")
                .body(user2Request)
                .retrieve()
                .body(RecipeDto.class);
        assertThat(user2Recipe).isNotNull();
        assertThat(user2Recipe.name()).isEqualTo("User 2 Recipe");

        // User 1 should see their own recipes (including those created in other tests)
        List<RecipeListDto> user1Recipes = user1Client
                .get()
                .uri("/recipes")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        assertThat(user1Recipes)
                .extracting(RecipeListDto::id)
                .contains(user1Recipe.id());
        assertThat(user1Recipes)
                .extracting(RecipeListDto::name)
                .contains("User 1 Recipe");

        // User 2 should only see their own recipes
        List<RecipeListDto> user2Recipes = user2Client
                .get()
                .uri("/recipes")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

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
                List.of(new Instruction("Secret recipe step"))
        );
        CreateRecipeRequest request = new CreateRecipeRequest("Secret Recipe", recipeData);

        RecipeDto user1Recipe = user1Client
                .post()
                .uri("/recipes")
                .body(request)
                .retrieve()
                .body(RecipeDto.class);
        assertThat(user1Recipe).isNotNull();

        // User 2 should not be able to access user 1's recipe
        try {
            user2Client
                    .get()
                    .uri("/recipes/" + user1Recipe.id())
                    .retrieve()
                    .body(RecipeDto.class);
            // Should not reach here
            assertThat(false).isTrue();
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // User 2 should not be able to update user 1's recipe
        UpdateRecipeRequest updateRequest = new UpdateRecipeRequest("Hacked Recipe", recipeData);
        try {
            user2Client
                    .put()
                    .uri("/recipes/" + user1Recipe.id())
                    .body(updateRequest)
                    .retrieve()
                    .body(RecipeDto.class);
            // Should not reach here
            assertThat(false).isTrue();
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // User 2 should not be able to delete user 1's recipe
        try {
            user2Client
                    .delete()
                    .uri("/recipes/" + user1Recipe.id())
                    .retrieve()
                    .toBodilessEntity();
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
                List.of(new Instruction("Secret recipe step"))
        );
        CreateRecipeRequest request = new CreateRecipeRequest("Shared Recipe", recipeData);

        RecipeDto ownerRecipe = user1Client
                .post()
                .uri("/recipes")
                .body(request)
                .retrieve()
                .body(RecipeDto.class);
        assertThat(ownerRecipe).isNotNull();
        assertThat(ownerRecipe.role()).isEqualTo(UserRole.OWNER);

        // User 2 should not have access initially
        try {
            user2Client
                    .get()
                    .uri("/recipes/" + ownerRecipe.id())
                    .retrieve()
                    .body(RecipeDto.class);
            assertThat(false).isTrue(); // Should not reach here
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // User 1 shares recipe with User 2
        ShareRecipeRequest shareRequest = new ShareRecipeRequest("user2@example.com");
        user1Client
                .post()
                .uri("/recipes/" + ownerRecipe.id() + "/share")
                .body(shareRequest)
                .retrieve()
                .toBodilessEntity();

        // User 2 should now have EDITOR access
        RecipeDto sharedRecipe = user2Client
                .get()
                .uri("/recipes/" + ownerRecipe.id())
                .retrieve()
                .body(RecipeDto.class);
        assertThat(sharedRecipe).isNotNull();
        assertThat(sharedRecipe.role()).isEqualTo(UserRole.EDITOR);
        assertThat(sharedRecipe.name()).isEqualTo("Shared Recipe");

        // Test shared users endpoint after sharing - should show OWNER first, then EDITOR
        List<SharedUserDto> sharedUsersAfterSharing = user1Client
                .get()
                .uri("/recipes/" + ownerRecipe.id() + "/shared_users")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        assertThat(sharedUsersAfterSharing).isNotNull();
        assertThat(sharedUsersAfterSharing).hasSize(2);
        assertThat(sharedUsersAfterSharing.get(0).email()).isEqualTo("user1@example.com");
        assertThat(sharedUsersAfterSharing.get(0).role()).isEqualTo(UserRole.OWNER);
        assertThat(sharedUsersAfterSharing.get(1).email()).isEqualTo("user2@example.com");
        assertThat(sharedUsersAfterSharing.get(1).role()).isEqualTo(UserRole.EDITOR);

        // User 2 (EDITOR) should be able to update the recipe
        RecipeData updatedData = new RecipeData(
                List.of(new Ingredient("updated ingredient", "200g", null)),
                List.of(new Instruction("Updated recipe step"))
        );
        UpdateRecipeRequest updateRequest = new UpdateRecipeRequest("Updated Shared Recipe", updatedData);

        RecipeDto updatedRecipe = user2Client
                .put()
                .uri("/recipes/" + ownerRecipe.id())
                .body(updateRequest)
                .retrieve()
                .body(RecipeDto.class);
        assertThat(updatedRecipe).isNotNull();
        assertThat(updatedRecipe.name()).isEqualTo("Updated Shared Recipe");
        assertThat(updatedRecipe.role()).isEqualTo(UserRole.EDITOR);

        // User 2 (EDITOR) should NOT be able to delete the recipe
        try {
            user2Client
                    .delete()
                    .uri("/recipes/" + ownerRecipe.id())
                    .retrieve()
                    .toBodilessEntity();
            assertThat(false).isTrue(); // Should not reach here
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // User 1 unshares the recipe from User 2
        UnshareRecipeRequest unshareRequest = new UnshareRecipeRequest("user2@example.com");
        user1Client
                .post()
                .uri("/recipes/" + ownerRecipe.id() + "/unshare")
                .body(unshareRequest)
                .retrieve()
                .toBodilessEntity();

        // User 2 should no longer have access
        try {
            user2Client
                    .get()
                    .uri("/recipes/" + ownerRecipe.id())
                    .retrieve()
                    .body(RecipeDto.class);
            assertThat(false).isTrue(); // Should not reach here
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // Test shared users endpoint after unsharing - should show only OWNER
        List<SharedUserDto> sharedUsersAfterUnsharing = user1Client
                .get()
                .uri("/recipes/" + ownerRecipe.id() + "/shared_users")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        assertThat(sharedUsersAfterUnsharing).isNotNull();
        assertThat(sharedUsersAfterUnsharing).hasSize(1);
        assertThat(sharedUsersAfterUnsharing.getFirst().email()).isEqualTo("user1@example.com");
        assertThat(sharedUsersAfterUnsharing.getFirst().role()).isEqualTo(UserRole.OWNER);

        // User 1 (OWNER) should still be able to delete the recipe
        user1Client
                .delete()
                .uri("/recipes/" + ownerRecipe.id())
                .retrieve()
                .toBodilessEntity();
    }

    @Test
    void shouldAllowEditorsToShareAndUnshareButPreventUnsharingOwner() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);
        RestClient defaultClient = restClient(TestSecurityConfiguration.AUTH_TOKEN);

        // User 1 creates a recipe
        RecipeData recipeData = new RecipeData(
                List.of(new Ingredient("ingredient", "100g", null)),
                List.of(new Instruction("Step"))
        );
        CreateRecipeRequest request = new CreateRecipeRequest("Editor Sharing Test Recipe", recipeData);

        RecipeDto ownerRecipe = user1Client
                .post()
                .uri("/recipes")
                .body(request)
                .retrieve()
                .body(RecipeDto.class);
        assertThat(ownerRecipe).isNotNull();

        // User 1 shares recipe with User 2 (making User 2 an EDITOR)
        ShareRecipeRequest shareRequest = new ShareRecipeRequest("user2@example.com");
        user1Client
                .post()
                .uri("/recipes/" + ownerRecipe.id() + "/share")
                .body(shareRequest)
                .retrieve()
                .toBodilessEntity();

        // User 2 (EDITOR) should be able to share the recipe with a third user
        ShareRecipeRequest editorShareRequest = new ShareRecipeRequest("user@example.com");
        user2Client
                .post()
                .uri("/recipes/" + ownerRecipe.id() + "/share")
                .body(editorShareRequest)
                .retrieve()
                .toBodilessEntity();

        // Verify the third user now has access
        RecipeDto thirdUserRecipe = defaultClient
                .get()
                .uri("/recipes/" + ownerRecipe.id())
                .retrieve()
                .body(RecipeDto.class);
        assertThat(thirdUserRecipe).isNotNull();
        assertThat(thirdUserRecipe.role()).isEqualTo(UserRole.EDITOR);

        // User 2 (EDITOR) should be able to unshare the recipe from the third user
        UnshareRecipeRequest editorUnshareRequest = new UnshareRecipeRequest("user@example.com");
        user2Client
                .post()
                .uri("/recipes/" + ownerRecipe.id() + "/unshare")
                .body(editorUnshareRequest)
                .retrieve()
                .toBodilessEntity();

        // Verify third user no longer has access
        try {
            defaultClient
                    .get()
                    .uri("/recipes/" + ownerRecipe.id())
                    .retrieve()
                    .body(RecipeDto.class);
            assertThat(false).isTrue(); // Should not reach here
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // User 2 (EDITOR) should NOT be able to unshare the OWNER (User 1)
        UnshareRecipeRequest unshareOwnerRequest = new UnshareRecipeRequest("user1@example.com");
        try {
            user2Client
                    .post()
                    .uri("/recipes/" + ownerRecipe.id() + "/unshare")
                    .body(unshareOwnerRequest)
                    .retrieve()
                    .toBodilessEntity();
            assertThat(false).isTrue(); // Should not reach here
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // Verify User 1 (OWNER) still has access
        RecipeDto ownerStillHasAccess = user1Client
                .get()
                .uri("/recipes/" + ownerRecipe.id())
                .retrieve()
                .body(RecipeDto.class);
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
                List.of(new Instruction("Step"))
        );
        CreateRecipeRequest request = new CreateRecipeRequest("Recipe To Be Shared", recipeData);

        RecipeDto ownerRecipe = user1Client
                .post()
                .uri("/recipes")
                .body(request)
                .retrieve()
                .body(RecipeDto.class);
        assertThat(ownerRecipe).isNotNull();

        // User 2 should not see the recipe in their list initially
        List<RecipeListDto> user2RecipesBefore = user2Client
                .get()
                .uri("/recipes")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        assertThat(user2RecipesBefore)
                .extracting(RecipeListDto::id)
                .doesNotContain(ownerRecipe.id());

        // User 1 shares recipe with User 2
        ShareRecipeRequest shareRequest = new ShareRecipeRequest("user2@example.com");
        user1Client
                .post()
                .uri("/recipes/" + ownerRecipe.id() + "/share")
                .body(shareRequest)
                .retrieve()
                .toBodilessEntity();

        // User 2 should now see the recipe in their list
        List<RecipeListDto> user2RecipesAfter = user2Client
                .get()
                .uri("/recipes")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        assertThat(user2RecipesAfter)
                .extracting(RecipeListDto::id)
                .contains(ownerRecipe.id());
        assertThat(user2RecipesAfter)
                .extracting(RecipeListDto::name)
                .contains("Recipe To Be Shared");
    }
}