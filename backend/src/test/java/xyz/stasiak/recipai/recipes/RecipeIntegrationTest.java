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

        RecipeDto pizzaResponse = restClient()
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

        RecipeDto pastaResponse = restClient()
                .post()
                .uri("/recipes")
                .body(pastaRequest)
                .retrieve()
                .body(RecipeDto.class);
        assertThat(pastaResponse).isNotNull();
        assertThat(pastaResponse.name()).isEqualTo("Spaghetti Carbonara");

        // READ: List all recipes - check that our created recipes are present
        List<RecipeListDto> listResponse = restClient()
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
        RecipeDto detailedRecipe = restClient()
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

        RecipeDto updatedRecipe = restClient()
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
        RecipeDto fetchedUpdatedRecipe = restClient()
                .get()
                .uri("/recipes/" + pizzaResponse.id())
                .retrieve()
                .body(RecipeDto.class);
        assertThat(fetchedUpdatedRecipe).isNotNull();
        assertThat(fetchedUpdatedRecipe.name()).isEqualTo("Updated Pizza Margherita");
        assertThat(fetchedUpdatedRecipe.data().ingredients()).hasSize(3);

        // DELETE: Delete the pasta recipe
        restClient()
                .delete()
                .uri("/recipes/" + pastaResponse.id())
                .retrieve()
                .toBodilessEntity();

        // READ: Verify deleted recipe returns 404
        try {
            restClient()
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
        List<RecipeListDto> finalListResponse = restClient()
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
        RecipeData data = new RecipeData(
                List.of(new Ingredient("flour", "300g", null)),
                List.of(new Instruction("Make dough"))
        );
        UpdateRecipeRequest updateRequest = new UpdateRecipeRequest("Non-existent", data);
        UUID randomId = UUID.randomUUID();

        try {
            restClient()
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
        UUID randomId = UUID.randomUUID();

        try {
            restClient()
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
        // User 1 creates a recipe
        RecipeData user1RecipeData = new RecipeData(
                List.of(new Ingredient("flour", "300g", null)),
                List.of(new Instruction("Make bread"))
        );
        CreateRecipeRequest user1Request = new CreateRecipeRequest("User 1 Recipe", user1RecipeData);

        RecipeDto user1Recipe = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1)
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

        RecipeDto user2Recipe = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2)
                .post()
                .uri("/recipes")
                .body(user2Request)
                .retrieve()
                .body(RecipeDto.class);
        assertThat(user2Recipe).isNotNull();
        assertThat(user2Recipe.name()).isEqualTo("User 2 Recipe");

        // User 1 should see their own recipes (including those created in other tests)
        List<RecipeListDto> user1Recipes = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1)
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
        List<RecipeListDto> user2Recipes = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2)
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
        // User 1 creates a recipe
        RecipeData recipeData = new RecipeData(
                List.of(new Ingredient("secret ingredient", "100g", null)),
                List.of(new Instruction("Secret recipe step"))
        );
        CreateRecipeRequest request = new CreateRecipeRequest("Secret Recipe", recipeData);

        RecipeDto user1Recipe = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1)
                .post()
                .uri("/recipes")
                .body(request)
                .retrieve()
                .body(RecipeDto.class);
        assertThat(user1Recipe).isNotNull();

        // User 2 should not be able to access user 1's recipe
        try {
            restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2)
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
            restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2)
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
            restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2)
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
        // User 1 creates a recipe
        RecipeData recipeData = new RecipeData(
                List.of(new Ingredient("secret ingredient", "100g", null)),
                List.of(new Instruction("Secret recipe step"))
        );
        CreateRecipeRequest request = new CreateRecipeRequest("Shared Recipe", recipeData);

        RecipeDto ownerRecipe = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1)
                .post()
                .uri("/recipes")
                .body(request)
                .retrieve()
                .body(RecipeDto.class);
        assertThat(ownerRecipe).isNotNull();
        assertThat(ownerRecipe.role()).isEqualTo(UserRole.OWNER);

        // User 2 should not have access initially
        try {
            restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2)
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
        restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1)
                .post()
                .uri("/recipes/" + ownerRecipe.id() + "/share")
                .body(shareRequest)
                .retrieve()
                .toBodilessEntity();

        // User 2 should now have EDITOR access
        RecipeDto sharedRecipe = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2)
                .get()
                .uri("/recipes/" + ownerRecipe.id())
                .retrieve()
                .body(RecipeDto.class);
        assertThat(sharedRecipe).isNotNull();
        assertThat(sharedRecipe.role()).isEqualTo(UserRole.EDITOR);
        assertThat(sharedRecipe.name()).isEqualTo("Shared Recipe");

        // User 2 (EDITOR) should be able to update the recipe
        RecipeData updatedData = new RecipeData(
                List.of(new Ingredient("updated ingredient", "200g", null)),
                List.of(new Instruction("Updated recipe step"))
        );
        UpdateRecipeRequest updateRequest = new UpdateRecipeRequest("Updated Shared Recipe", updatedData);

        RecipeDto updatedRecipe = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2)
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
            restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2)
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
        restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1)
                .post()
                .uri("/recipes/" + ownerRecipe.id() + "/unshare")
                .body(unshareRequest)
                .retrieve()
                .toBodilessEntity();

        // User 2 should no longer have access
        try {
            restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2)
                    .get()
                    .uri("/recipes/" + ownerRecipe.id())
                    .retrieve()
                    .body(RecipeDto.class);
            assertThat(false).isTrue(); // Should not reach here
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // User 1 (OWNER) should still be able to delete the recipe
        restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1)
                .delete()
                .uri("/recipes/" + ownerRecipe.id())
                .retrieve()
                .toBodilessEntity();
    }

    @Test
    void shouldAllowEditorsToShareAndUnshareButPreventUnsharingOwner() {
        // User 1 creates a recipe
        RecipeData recipeData = new RecipeData(
                List.of(new Ingredient("ingredient", "100g", null)),
                List.of(new Instruction("Step"))
        );
        CreateRecipeRequest request = new CreateRecipeRequest("Editor Sharing Test Recipe", recipeData);

        RecipeDto ownerRecipe = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1)
                .post()
                .uri("/recipes")
                .body(request)
                .retrieve()
                .body(RecipeDto.class);
        assertThat(ownerRecipe).isNotNull();

        // User 1 shares recipe with User 2 (making User 2 an EDITOR)
        ShareRecipeRequest shareRequest = new ShareRecipeRequest("user2@example.com");
        restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1)
                .post()
                .uri("/recipes/" + ownerRecipe.id() + "/share")
                .body(shareRequest)
                .retrieve()
                .toBodilessEntity();

        // User 2 (EDITOR) should be able to share the recipe with a third user
        ShareRecipeRequest editorShareRequest = new ShareRecipeRequest("user@example.com");
        restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2)
                .post()
                .uri("/recipes/" + ownerRecipe.id() + "/share")
                .body(editorShareRequest)
                .retrieve()
                .toBodilessEntity();

        // Verify the third user now has access
        RecipeDto thirdUserRecipe = restClient(TestSecurityConfiguration.AUTH_TOKEN)
                .get()
                .uri("/recipes/" + ownerRecipe.id())
                .retrieve()
                .body(RecipeDto.class);
        assertThat(thirdUserRecipe).isNotNull();
        assertThat(thirdUserRecipe.role()).isEqualTo(UserRole.EDITOR);

        // User 2 (EDITOR) should be able to unshare the recipe from the third user
        UnshareRecipeRequest editorUnshareRequest = new UnshareRecipeRequest("user@example.com");
        restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2)
                .post()
                .uri("/recipes/" + ownerRecipe.id() + "/unshare")
                .body(editorUnshareRequest)
                .retrieve()
                .toBodilessEntity();

        // Verify third user no longer has access
        try {
            restClient(TestSecurityConfiguration.AUTH_TOKEN)
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
            restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2)
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
        RecipeDto ownerStillHasAccess = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1)
                .get()
                .uri("/recipes/" + ownerRecipe.id())
                .retrieve()
                .body(RecipeDto.class);
        assertThat(ownerStillHasAccess).isNotNull();
        assertThat(ownerStillHasAccess.role()).isEqualTo(UserRole.OWNER);
    }

    @Test
    void shouldHandleSharedRecipesInUserRecipeList() {
        // User 1 creates a recipe
        RecipeData recipeData = new RecipeData(
                List.of(new Ingredient("ingredient", "100g", null)),
                List.of(new Instruction("Step"))
        );
        CreateRecipeRequest request = new CreateRecipeRequest("Recipe To Be Shared", recipeData);

        RecipeDto ownerRecipe = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1)
                .post()
                .uri("/recipes")
                .body(request)
                .retrieve()
                .body(RecipeDto.class);
        assertThat(ownerRecipe).isNotNull();

        // User 2 should not see the recipe in their list initially
        List<RecipeListDto> user2RecipesBefore = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2)
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
        restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1)
                .post()
                .uri("/recipes/" + ownerRecipe.id() + "/share")
                .body(shareRequest)
                .retrieve()
                .toBodilessEntity();

        // User 2 should now see the recipe in their list
        List<RecipeListDto> user2RecipesAfter = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2)
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