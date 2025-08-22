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
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader("Authorization", "Bearer " + TestSecurityConfiguration.AUTH_TOKEN)
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
}