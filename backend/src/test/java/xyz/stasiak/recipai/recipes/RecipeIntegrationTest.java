package xyz.stasiak.recipai.recipes;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import xyz.stasiak.recipai.TestcontainersConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecipeIntegrationTest {

    @LocalServerPort
    private int port;

    private RestClient restClient() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void shouldCreateListAndReadRecipes() {
        // Create first recipe
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

        // Create second recipe
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

        // List all recipes - check that our created recipes are present (independent of existing data)
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

        // Read detailed recipe
        String pizzaId = pizzaResponse.id().toString();
        RecipeDto detailedRecipe = restClient()
                .get()
                .uri("/recipes/" + pizzaId)
                .retrieve()
                .body(RecipeDto.class);
        assertThat(detailedRecipe).isNotNull();
        assertThat(detailedRecipe.name()).isEqualTo("Pizza Margherita");
        assertThat(detailedRecipe.data().ingredients()).hasSize(3);
        assertThat(detailedRecipe.data().instructions()).hasSize(3);
        assertThat(detailedRecipe.data().ingredients().get(0).name()).isEqualTo("flour");
        assertThat(detailedRecipe.data().instructions().get(0).step()).isEqualTo("Make dough");
    }
}