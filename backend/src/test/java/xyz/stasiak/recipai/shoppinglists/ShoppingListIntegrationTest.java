package xyz.stasiak.recipai.shoppinglists;

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
import static org.assertj.core.api.Assertions.fail;

@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ShoppingListIntegrationTest {

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
    void shouldCreateAndListShoppingLists() {
        // Create first shopping list
        CreateShoppingListRequest request1 = new CreateShoppingListRequest("Groceries");
        ShoppingListListDto response1 = restClient()
                .post()
                .uri("/shopping-lists")
                .body(request1)
                .retrieve()
                .body(ShoppingListListDto.class);

        assertThat(response1).isNotNull();
        assertThat(response1.id()).isNotNull();
        assertThat(response1.name()).isEqualTo("Groceries");

        // Create second shopping list
        CreateShoppingListRequest request2 = new CreateShoppingListRequest("Hardware");
        ShoppingListListDto response2 = restClient()
                .post()
                .uri("/shopping-lists")
                .body(request2)
                .retrieve()
                .body(ShoppingListListDto.class);

        assertThat(response2).isNotNull();
        assertThat(response2.id()).isNotNull();
        assertThat(response2.name()).isEqualTo("Hardware");

        // List all shopping lists
        List<ShoppingListListDto> listResponse = restClient()
                .get()
                .uri("/shopping-lists")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        assertThat(listResponse).isNotEmpty();
        assertThat(listResponse).hasSizeGreaterThanOrEqualTo(2);
        assertThat(listResponse)
                .extracting(ShoppingListListDto::name)
                .contains("Groceries", "Hardware");
    }

    @Test
    void shouldValidateCreateShoppingListRequest() {
        CreateShoppingListRequest request = new CreateShoppingListRequest("");

        try {
            restClient()
                    .post()
                    .uri("/shopping-lists")
                    .body(request)
                    .retrieve()
                    .body(ShoppingListListDto.class);
            // Should not reach here
            assertThat(false).isTrue();
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(400);
        }
    }

    @Test
    void shouldGetShoppingListById() {
        // Create a shopping list
        CreateShoppingListRequest request = new CreateShoppingListRequest("Weekly Groceries");
        ShoppingListListDto createdList = restClient()
                .post()
                .uri("/shopping-lists")
                .body(request)
                .retrieve()
                .body(ShoppingListListDto.class);

        assertThat(createdList).isNotNull();
        assertThat(createdList.id()).isNotNull();

        // Get the shopping list by ID
        ShoppingListDto response = restClient()
                .get()
                .uri("/shopping-lists/" + createdList.id())
                .retrieve()
                .body(ShoppingListDto.class);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(createdList.id());
        assertThat(response.name()).isEqualTo("Weekly Groceries");
        assertThat(response.items()).isNotNull();
        assertThat(response.items()).isEmpty();
        assertThat(response.role()).isEqualTo(UserRole.OWNER);
    }

    @Test
    void shouldReturn404WhenShoppingListNotFound() {
        UUID nonExistentId = UUID.randomUUID();

        try {
            restClient()
                    .get()
                    .uri("/shopping-lists/" + nonExistentId)
                    .retrieve()
                    .body(ShoppingListDto.class);
            //noinspection ResultOfMethodCallIgnored
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(404);
            String responseBody = ex.getResponseBodyAsString();
            assertThat(responseBody).contains("Shopping list not found with id: " + nonExistentId);
            assertThat(responseBody).contains("Shopping List Not Found");
        }
    }

    @Test
    void shouldIsolateShoppingListsBetweenUsers() {
        // User 1 creates a shopping list
        CreateShoppingListRequest request1 = new CreateShoppingListRequest("User 1 List");
        ShoppingListListDto user1List = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1)
                .post()
                .uri("/shopping-lists")
                .body(request1)
                .retrieve()
                .body(ShoppingListListDto.class);

        assertThat(user1List).isNotNull();
        assertThat(user1List.name()).isEqualTo("User 1 List");

        // User 2 creates a shopping list
        CreateShoppingListRequest request2 = new CreateShoppingListRequest("User 2 List");
        ShoppingListListDto user2List = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2)
                .post()
                .uri("/shopping-lists")
                .body(request2)
                .retrieve()
                .body(ShoppingListListDto.class);

        assertThat(user2List).isNotNull();
        assertThat(user2List.name()).isEqualTo("User 2 List");

        // User 1 should only see their own list
        List<ShoppingListListDto> user1Lists = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1)
                .get()
                .uri("/shopping-lists")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        assertThat(user1Lists)
                .extracting(ShoppingListListDto::id)
                .contains(user1List.id())
                .doesNotContain(user2List.id());

        // User 2 should only see their own list
        List<ShoppingListListDto> user2Lists = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2)
                .get()
                .uri("/shopping-lists")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        assertThat(user2Lists)
                .extracting(ShoppingListListDto::id)
                .contains(user2List.id())
                .doesNotContain(user1List.id());
    }

    @Test
    void shouldPreventCrossUserAccess() {
        // User 1 creates a shopping list
        CreateShoppingListRequest request = new CreateShoppingListRequest("User 1 Private List");
        ShoppingListListDto user1List = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1)
                .post()
                .uri("/shopping-lists")
                .body(request)
                .retrieve()
                .body(ShoppingListListDto.class);

        assertThat(user1List).isNotNull();

        // User 2 tries to access User 1's list - should get 404 (masked as not found)
        try {
            restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2)
                    .get()
                    .uri("/shopping-lists/" + user1List.id())
                    .retrieve()
                    .body(ShoppingListDto.class);
            //noinspection ResultOfMethodCallIgnored
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(404);
            String responseBody = ex.getResponseBodyAsString();
            assertThat(responseBody).contains("Shopping list not found with id: " + user1List.id());
            assertThat(responseBody).contains("Shopping List Not Found");
        }
    }
}