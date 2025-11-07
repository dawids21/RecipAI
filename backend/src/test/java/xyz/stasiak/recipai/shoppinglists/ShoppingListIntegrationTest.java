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

@SuppressWarnings("ResultOfMethodCallIgnored")
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
        RestClient client = restClient();

        // Create first shopping list
        CreateShoppingListRequest request1 = new CreateShoppingListRequest("Groceries");
        ShoppingListListDto response1 = client
                .post()
                .uri("/shopping-lists")
                .body(request1)
                .retrieve()
                .body(ShoppingListListDto.class);

        assertThat(response1).isNotNull();
        assertThat(response1.id()).isNotNull();
        assertThat(response1.name()).isEqualTo("Groceries");
        assertThat(response1.version()).isNotNull();

        // Create second shopping list
        CreateShoppingListRequest request2 = new CreateShoppingListRequest("Hardware");
        ShoppingListListDto response2 = client
                .post()
                .uri("/shopping-lists")
                .body(request2)
                .retrieve()
                .body(ShoppingListListDto.class);

        assertThat(response2).isNotNull();
        assertThat(response2.id()).isNotNull();
        assertThat(response2.name()).isEqualTo("Hardware");
        assertThat(response2.version()).isNotNull();

        // List all shopping lists
        List<ShoppingListListDto> listResponse = client
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
        RestClient client = restClient();
        CreateShoppingListRequest request = new CreateShoppingListRequest("");

        try {
            client
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
        RestClient client = restClient();

        // Create a shopping list
        CreateShoppingListRequest request = new CreateShoppingListRequest("Weekly Groceries");
        ShoppingListListDto createdList = client
                .post()
                .uri("/shopping-lists")
                .body(request)
                .retrieve()
                .body(ShoppingListListDto.class);

        assertThat(createdList).isNotNull();
        assertThat(createdList.id()).isNotNull();

        // Get the shopping list by ID
        ShoppingListDto response = client
                .get()
                .uri("/shopping-lists/" + createdList.id())
                .retrieve()
                .body(ShoppingListDto.class);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(createdList.id());
        assertThat(response.name()).isEqualTo("Weekly Groceries");
        assertThat(response.version()).isNotNull();
        assertThat(response.items()).isNotNull();
        assertThat(response.items()).isEmpty();
        assertThat(response.role()).isEqualTo(UserRole.OWNER);
    }

    @Test
    void shouldReturn404WhenShoppingListNotFound() {
        RestClient client = restClient();
        UUID nonExistentId = UUID.randomUUID();

        try {
            client
                    .get()
                    .uri("/shopping-lists/" + nonExistentId)
                    .retrieve()
                    .body(ShoppingListDto.class);
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
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates a shopping list
        CreateShoppingListRequest request1 = new CreateShoppingListRequest("User 1 List");
        ShoppingListListDto user1List = user1Client
                .post()
                .uri("/shopping-lists")
                .body(request1)
                .retrieve()
                .body(ShoppingListListDto.class);

        assertThat(user1List).isNotNull();
        assertThat(user1List.name()).isEqualTo("User 1 List");

        // User 2 creates a shopping list
        CreateShoppingListRequest request2 = new CreateShoppingListRequest("User 2 List");
        ShoppingListListDto user2List = user2Client
                .post()
                .uri("/shopping-lists")
                .body(request2)
                .retrieve()
                .body(ShoppingListListDto.class);

        assertThat(user2List).isNotNull();
        assertThat(user2List.name()).isEqualTo("User 2 List");

        // User 1 should only see their own list
        List<ShoppingListListDto> user1Lists = user1Client
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
        List<ShoppingListListDto> user2Lists = user2Client
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
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates a shopping list
        CreateShoppingListRequest request = new CreateShoppingListRequest("User 1 Private List");
        ShoppingListListDto user1List = user1Client
                .post()
                .uri("/shopping-lists")
                .body(request)
                .retrieve()
                .body(ShoppingListListDto.class);

        assertThat(user1List).isNotNull();

        // User 2 tries to access User 1's list - should get 403 Forbidden
        try {
            user2Client
                    .get()
                    .uri("/shopping-lists/" + user1List.id())
                    .retrieve()
                    .body(ShoppingListDto.class);
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
            String responseBody = ex.getResponseBodyAsString();
            assertThat(responseBody).contains("Access denied to shopping list with id: " + user1List.id());
            assertThat(responseBody).contains("Shopping List Access Denied");
        }
    }

    @Test
    void shouldUpdateAndDeleteShoppingList() {
        RestClient client = restClient();

        // CREATE shopping list
        CreateShoppingListRequest createRequest = new CreateShoppingListRequest("My Shopping List");
        ShoppingListListDto created = client
                .post()
                .uri("/shopping-lists")
                .body(createRequest)
                .retrieve()
                .body(ShoppingListListDto.class);

        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("My Shopping List");
        assertThat(created.version()).isNotNull();

        // UPDATE shopping list
        UpdateShoppingListRequest updateRequest = new UpdateShoppingListRequest("Updated List Name");
        ShoppingListListDto updated = client
                .put()
                .uri("/shopping-lists/" + created.id())
                .header("If-Match", "\"" + created.version() + "\"")
                .body(updateRequest)
                .retrieve()
                .body(ShoppingListListDto.class);

        assertThat(updated).isNotNull();
        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.name()).isEqualTo("Updated List Name");
        assertThat(updated.version()).isNotNull();

        // DELETE shopping list
        client
                .delete()
                .uri("/shopping-lists/" + created.id())
                .header("If-Match", "\"" + updated.version() + "\"")
                .retrieve()
                .toBodilessEntity();

        // VERIFY deleted (should return 404)
        try {
            client
                    .get()
                    .uri("/shopping-lists/" + created.id())
                    .retrieve()
                    .body(ShoppingListDto.class);
            fail("Should have thrown 404");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(404);
        }
    }

    @Test
    void shouldReturn404WhenUpdatingNonExistentShoppingList() {
        RestClient client = restClient();
        UUID nonExistentId = UUID.randomUUID();

        UpdateShoppingListRequest updateRequest = new UpdateShoppingListRequest("Updated Name");
        try {
            client
                    .put()
                    .uri("/shopping-lists/" + nonExistentId)
                    .header("If-Match", "\"0\"")
                    .body(updateRequest)
                    .retrieve()
                    .body(ShoppingListListDto.class);
            fail("Should have thrown 404");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(404);
        }
    }

    @Test
    void shouldReturn404WhenDeletingNonExistentShoppingList() {
        RestClient client = restClient();
        UUID nonExistentId = UUID.randomUUID();

        try {
            client
                    .delete()
                    .uri("/shopping-lists/" + nonExistentId)
                    .header("If-Match", "\"0\"")
                    .retrieve()
                    .toBodilessEntity();
            fail("Should have thrown 404");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(404);
        }
    }

    @Test
    void shouldReturn403WhenUnauthorizedUserTriesToUpdate() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates list (becomes OWNER)
        CreateShoppingListRequest createRequest = new CreateShoppingListRequest("User 1 List");
        ShoppingListListDto list = user1Client
                .post()
                .uri("/shopping-lists")
                .body(createRequest)
                .retrieve()
                .body(ShoppingListListDto.class);

        assertThat(list).isNotNull();

        // User 2 tries to update (no permission) - should get 403
        UpdateShoppingListRequest updateRequest = new UpdateShoppingListRequest("Hacked Name");
        try {
            user2Client
                    .put()
                    .uri("/shopping-lists/" + list.id())
                    .header("If-Match", "\"" + list.version() + "\"")
                    .body(updateRequest)
                    .retrieve()
                    .body(ShoppingListListDto.class);
            fail("Should have thrown 403");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }
    }

    @Test
    void shouldReturn403WhenUnauthorizedUserTriesToDelete() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates list (becomes OWNER)
        CreateShoppingListRequest createRequest = new CreateShoppingListRequest("User 1 List");
        ShoppingListListDto list = user1Client
                .post()
                .uri("/shopping-lists")
                .body(createRequest)
                .retrieve()
                .body(ShoppingListListDto.class);

        assertThat(list).isNotNull();

        // User 2 tries to delete (no permission) - should get 403
        try {
            user2Client
                    .delete()
                    .uri("/shopping-lists/" + list.id())
                    .header("If-Match", "\"" + list.version() + "\"")
                    .retrieve()
                    .toBodilessEntity();
            fail("Should have thrown 403");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }
    }

    @Test
    void shouldReturn412OnConcurrentUpdate() {
        RestClient client = restClient();

        // CREATE shopping list
        CreateShoppingListRequest createRequest = new CreateShoppingListRequest("Concurrent Test List");
        ShoppingListListDto created = client
                .post()
                .uri("/shopping-lists")
                .body(createRequest)
                .retrieve()
                .body(ShoppingListListDto.class);

        assertThat(created).isNotNull();
        Long originalVersion = created.version();

        // First UPDATE (should succeed)
        UpdateShoppingListRequest updateRequest1 = new UpdateShoppingListRequest("First Update");
        ShoppingListListDto firstUpdate = client
                .put()
                .uri("/shopping-lists/" + created.id())
                .header("If-Match", "\"" + originalVersion + "\"")
                .body(updateRequest1)
                .retrieve()
                .body(ShoppingListListDto.class);

        assertThat(firstUpdate).isNotNull();
        assertThat(firstUpdate.name()).isEqualTo("First Update");

        // Second UPDATE with stale version (should fail with 412)
        UpdateShoppingListRequest updateRequest2 = new UpdateShoppingListRequest("Second Update");
        try {
            client
                    .put()
                    .uri("/shopping-lists/" + created.id())
                    .header("If-Match", "\"" + originalVersion + "\"")  // Using stale version
                    .body(updateRequest2)
                    .retrieve()
                    .body(ShoppingListListDto.class);
            fail("Should have thrown 412 Precondition Failed");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(412);
            String responseBody = ex.getResponseBodyAsString();
            assertThat(responseBody).contains("has been modified");
            assertThat(responseBody).contains("Shopping List Precondition Failed");
        }
    }
}