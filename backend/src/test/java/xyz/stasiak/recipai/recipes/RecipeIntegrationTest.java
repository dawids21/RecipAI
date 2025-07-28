package xyz.stasiak.recipai.recipes;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateListAndReadRecipes() throws Exception {
        // Create first recipe
        CreateRecipeRequest pizzaRequest = new CreateRecipeRequest(
                "Pizza Margherita",
                objectMapper.readTree("""
                        {
                            "description": "Classic Italian pizza",
                            "ingredients": [
                                {"name": "flour", "quantity": "300g"},
                                {"name": "tomato sauce", "quantity": "200ml"},
                                {"name": "mozzarella", "quantity": "150g"}
                            ],
                            "steps": [
                                {"description": "Make dough"},
                                {"description": "Add sauce and toppings"},
                                {"description": "Bake for 15 minutes"}
                            ]
                        }
                        """)
        );

        RecipeDto pizzaResponse = restClient()
                .post()
                .uri("/recipes")
                .body(pizzaRequest)
                .retrieve()
                .body(RecipeDto.class);
        assertThat(pizzaResponse).isNotNull();
        assertThat(pizzaResponse.name()).isEqualTo("Pizza Margherita");

        // Create second recipe
        CreateRecipeRequest pastaRequest = new CreateRecipeRequest(
                "Spaghetti Carbonara",
                objectMapper.readTree("""
                        {
                            "description": "Roman pasta dish",
                            "ingredients": [
                                {"name": "spaghetti", "quantity": "400g"},
                                {"name": "eggs", "quantity": "4"},
                                {"name": "pancetta", "quantity": "200g"}
                            ],
                            "steps": [
                                {"description": "Cook pasta"},
                                {"description": "Fry pancetta"},
                                {"description": "Mix with eggs"}
                            ]
                        }
                        """)
        );

        RecipeDto pastaResponse = restClient()
                .post()
                .uri("/recipes")
                .body(pastaRequest)
                .retrieve()
                .body(RecipeDto.class);
        assertThat(pastaResponse).isNotNull();
        assertThat(pastaResponse.name()).isEqualTo("Spaghetti Carbonara");

        // List all recipes
        List<RecipeListDto> listResponse = restClient()
                .get()
                .uri("/recipes")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        assertThat(listResponse).hasSize(2);
        assertThat(listResponse)
                .extracting(RecipeListDto::name)
                .containsExactlyInAnyOrder("Pizza Margherita", "Spaghetti Carbonara");

        // Read detailed recipe
        String pizzaId = pizzaResponse.id().toString();
        RecipeDto detailedRecipe = restClient()
                .get()
                .uri("/recipes/" + pizzaId)
                .retrieve()
                .body(RecipeDto.class);
        assertThat(detailedRecipe).isNotNull();
        assertThat(detailedRecipe.name()).isEqualTo("Pizza Margherita");
        assertThat(detailedRecipe.data().get("description").asText()).isEqualTo("Classic Italian pizza");
        assertThat(detailedRecipe.data().get("ingredients").isArray()).isTrue();
        assertThat(detailedRecipe.data().get("steps").isArray()).isTrue();
    }
}