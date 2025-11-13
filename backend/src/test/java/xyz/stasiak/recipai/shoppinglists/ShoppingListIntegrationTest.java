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

import java.math.BigDecimal;
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

    @Test
    void shouldAddItemToShoppingList() {
        RestClient client = restClient();

        // Create shopping list
        CreateShoppingListRequest createRequest = new CreateShoppingListRequest("Groceries");
        ShoppingListListDto list = client
                .post()
                .uri("/shopping-lists")
                .body(createRequest)
                .retrieve()
                .body(ShoppingListListDto.class);

        assertThat(list).isNotNull();
        Long originalVersion = list.version();

        // Add item with name, quantity, and unit
        AddShoppingListItemRequest addRequest = new AddShoppingListItemRequest("Milk", new BigDecimal("2.5"), "liters");
        ShoppingListListDto addResponse = client
                .post()
                .uri("/shopping-lists/" + list.id() + "/add")
                .body(addRequest)
                .retrieve()
                .body(ShoppingListListDto.class);

        assertThat(addResponse).isNotNull();
        assertThat(addResponse.id()).isEqualTo(list.id());
        assertThat(addResponse.name()).isEqualTo("Groceries");
        assertThat(addResponse.version()).isGreaterThan(originalVersion);

        // Verify item appears in GET response
        ShoppingListDto updatedList = client
                .get()
                .uri("/shopping-lists/" + list.id())
                .retrieve()
                .body(ShoppingListDto.class);

        assertThat(updatedList).isNotNull();
        assertThat(updatedList.items()).hasSize(1);
        assertThat(updatedList.items().getFirst().name()).isEqualTo("Milk");
        assertThat(updatedList.items().getFirst().quantity()).isEqualByComparingTo(new BigDecimal("2.5"));
        assertThat(updatedList.items().getFirst().unit()).isEqualTo("liters");
        assertThat(updatedList.items().getFirst().position()).isEqualTo(1);
        assertThat(updatedList.items().getFirst().checked()).isFalse();
        assertThat(updatedList.version()).isEqualTo(addResponse.version());
    }

    @Test
    void shouldAddItemWithNullQuantityAndUnit() {
        RestClient client = restClient();

        // Create shopping list
        CreateShoppingListRequest createRequest = new CreateShoppingListRequest("Shopping");
        ShoppingListListDto list = client
                .post()
                .uri("/shopping-lists")
                .body(createRequest)
                .retrieve()
                .body(ShoppingListListDto.class);

        assertThat(list).isNotNull();

        // Add item with only name (quantity and unit null)
        AddShoppingListItemRequest addRequest = new AddShoppingListItemRequest("Bread", null, null);
        ShoppingListListDto addResponse = client
                .post()
                .uri("/shopping-lists/" + list.id() + "/add")
                .body(addRequest)
                .retrieve()
                .body(ShoppingListListDto.class);

        assertThat(addResponse).isNotNull();
        assertThat(addResponse.id()).isEqualTo(list.id());
        assertThat(addResponse.name()).isEqualTo("Shopping");

        // Verify item appears with null quantity and unit
        ShoppingListDto updatedList = client
                .get()
                .uri("/shopping-lists/" + list.id())
                .retrieve()
                .body(ShoppingListDto.class);

        assertThat(updatedList).isNotNull();
        assertThat(updatedList.items()).hasSize(1);
        assertThat(updatedList.items().getFirst().name()).isEqualTo("Bread");
        assertThat(updatedList.items().getFirst().quantity()).isNull();
        assertThat(updatedList.items().getFirst().unit()).isNull();
    }

    @Test
    void shouldReturn404WhenAddingToNonExistentList() {
        RestClient client = restClient();
        UUID nonExistentId = UUID.randomUUID();

        AddShoppingListItemRequest addRequest = new AddShoppingListItemRequest("Item", null, null);
        try {
            client
                    .post()
                    .uri("/shopping-lists/" + nonExistentId + "/add")
                    .body(addRequest)
                    .retrieve()
                    .toBodilessEntity();
            fail("Should have thrown 404");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(404);
        }
    }

    @Test
    void shouldReturn403WhenUserHasNoPermissionToAddItem() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates list
        CreateShoppingListRequest createRequest = new CreateShoppingListRequest("User 1 List");
        ShoppingListListDto list = user1Client
                .post()
                .uri("/shopping-lists")
                .body(createRequest)
                .retrieve()
                .body(ShoppingListListDto.class);

        assertThat(list).isNotNull();

        // User 2 tries to add item (no permission) - should get 403
        AddShoppingListItemRequest addRequest = new AddShoppingListItemRequest("Hacked Item", null, null);
        try {
            user2Client
                    .post()
                    .uri("/shopping-lists/" + list.id() + "/add")
                    .body(addRequest)
                    .retrieve()
                    .toBodilessEntity();
            fail("Should have thrown 403");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }
    }

    @Test
    void shouldReturn400WhenAddingItemWithBlankName() {
        RestClient client = restClient();

        // Create shopping list
        CreateShoppingListRequest createRequest = new CreateShoppingListRequest("Groceries");
        ShoppingListListDto list = client
                .post()
                .uri("/shopping-lists")
                .body(createRequest)
                .retrieve()
                .body(ShoppingListListDto.class);

        assertThat(list).isNotNull();

        // Try to add item with blank name
        AddShoppingListItemRequest addRequest = new AddShoppingListItemRequest("", null, null);
        try {
            client
                    .post()
                    .uri("/shopping-lists/" + list.id() + "/add")
                    .body(addRequest)
                    .retrieve()
                    .toBodilessEntity();
            fail("Should have thrown 400");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(400);
        }
    }

    @Test
    void shouldRemoveItemFromShoppingList() {
        RestClient client = restClient();

        // Create shopping list
        CreateShoppingListRequest createRequest = new CreateShoppingListRequest("Groceries");
        ShoppingListListDto list = client
                .post()
                .uri("/shopping-lists")
                .body(createRequest)
                .retrieve()
                .body(ShoppingListListDto.class);

        assertThat(list).isNotNull();

        // Add item
        AddShoppingListItemRequest addRequest = new AddShoppingListItemRequest("Milk", new BigDecimal("2.5"), "liters");
        client
                .post()
                .uri("/shopping-lists/" + list.id() + "/add")
                .body(addRequest)
                .retrieve()
                .body(ShoppingListListDto.class);

        // Get updated list with item
        ShoppingListDto listWithItem = client
                .get()
                .uri("/shopping-lists/" + list.id())
                .retrieve()
                .body(ShoppingListDto.class);

        assertThat(listWithItem).isNotNull();
        assertThat(listWithItem.items()).hasSize(1);
        UUID itemId = listWithItem.items().getFirst().id();
        Long versionWithItem = listWithItem.version();

        // Remove item
        RemoveShoppingListItemRequest removeRequest = new RemoveShoppingListItemRequest(itemId);
        ShoppingListListDto removeResponse = client
                .post()
                .uri("/shopping-lists/" + list.id() + "/remove")
                .header("If-Match", "\"" + versionWithItem + "\"")
                .body(removeRequest)
                .retrieve()
                .body(ShoppingListListDto.class);

        assertThat(removeResponse).isNotNull();
        assertThat(removeResponse.id()).isEqualTo(list.id());
        assertThat(removeResponse.name()).isEqualTo("Groceries");
        assertThat(removeResponse.version()).isGreaterThan(versionWithItem);

        // Verify item no longer in list
        ShoppingListDto listAfterRemove = client
                .get()
                .uri("/shopping-lists/" + list.id())
                .retrieve()
                .body(ShoppingListDto.class);

        assertThat(listAfterRemove).isNotNull();
        assertThat(listAfterRemove.items()).isEmpty();
        assertThat(listAfterRemove.version()).isEqualTo(removeResponse.version());
    }

    @Test
    void shouldRemoveItemAndRecalculatePositions() {
        RestClient client = restClient();

        // Create shopping list
        CreateShoppingListRequest createRequest = new CreateShoppingListRequest("Groceries");
        ShoppingListListDto list = client
                .post()
                .uri("/shopping-lists")
                .body(createRequest)
                .retrieve()
                .body(ShoppingListListDto.class);

        assertThat(list).isNotNull();

        // Add three items
        client.post().uri("/shopping-lists/" + list.id() + "/add")
                .body(new AddShoppingListItemRequest("Item 1", null, null))
                .retrieve().body(ShoppingListListDto.class);
        client.post().uri("/shopping-lists/" + list.id() + "/add")
                .body(new AddShoppingListItemRequest("Item 2", null, null))
                .retrieve().body(ShoppingListListDto.class);
        client.post().uri("/shopping-lists/" + list.id() + "/add")
                .body(new AddShoppingListItemRequest("Item 3", null, null))
                .retrieve().body(ShoppingListListDto.class);

        // Get list with all three items
        ShoppingListDto listWith3Items = client
                .get()
                .uri("/shopping-lists/" + list.id())
                .retrieve()
                .body(ShoppingListDto.class);

        assertThat(listWith3Items).isNotNull();
        assertThat(listWith3Items.items()).hasSize(3);
        assertThat(listWith3Items.items().getFirst().position()).isEqualTo(1);
        assertThat(listWith3Items.items().get(1).position()).isEqualTo(2);
        assertThat(listWith3Items.items().get(2).position()).isEqualTo(3);

        // Remove middle item (Item 2)
        UUID middleItemId = listWith3Items.items().get(1).id();
        RemoveShoppingListItemRequest removeRequest = new RemoveShoppingListItemRequest(middleItemId);
        client
                .post()
                .uri("/shopping-lists/" + list.id() + "/remove")
                .header("If-Match", "\"" + listWith3Items.version() + "\"")
                .body(removeRequest)
                .retrieve()
                .body(ShoppingListListDto.class);

        // Verify positions are recalculated (1, 2 instead of 1, 3)
        ShoppingListDto listAfterRemove = client
                .get()
                .uri("/shopping-lists/" + list.id())
                .retrieve()
                .body(ShoppingListDto.class);

        assertThat(listAfterRemove).isNotNull();
        assertThat(listAfterRemove.items()).hasSize(2);
        assertThat(listAfterRemove.items().getFirst().name()).isEqualTo("Item 1");
        assertThat(listAfterRemove.items().getFirst().position()).isEqualTo(1);
        assertThat(listAfterRemove.items().get(1).name()).isEqualTo("Item 3");
        assertThat(listAfterRemove.items().get(1).position()).isEqualTo(2);
    }

    @Test
    void shouldReturn200WhenRemovingNonExistentItem() {
        RestClient client = restClient();

        // Create shopping list
        CreateShoppingListRequest createRequest = new CreateShoppingListRequest("Groceries");
        ShoppingListListDto list = client
                .post()
                .uri("/shopping-lists")
                .body(createRequest)
                .retrieve()
                .body(ShoppingListListDto.class);

        assertThat(list).isNotNull();

        // Get list to get current version
        ShoppingListDto currentList = client
                .get()
                .uri("/shopping-lists/" + list.id())
                .retrieve()
                .body(ShoppingListDto.class);

        assertThat(currentList).isNotNull();

        // Try to remove non-existent item - should return 200 (idempotent)
        UUID nonExistentItemId = UUID.randomUUID();
        RemoveShoppingListItemRequest removeRequest = new RemoveShoppingListItemRequest(nonExistentItemId);
        ShoppingListListDto removeResponse = client
                .post()
                .uri("/shopping-lists/" + list.id() + "/remove")
                .header("If-Match", "\"" + currentList.version() + "\"")
                .body(removeRequest)
                .retrieve()
                .body(ShoppingListListDto.class);

        assertThat(removeResponse).isNotNull();
        assertThat(removeResponse.id()).isEqualTo(list.id());

        // Verify list unchanged
        ShoppingListDto listAfterRemove = client
                .get()
                .uri("/shopping-lists/" + list.id())
                .retrieve()
                .body(ShoppingListDto.class);

        assertThat(listAfterRemove).isNotNull();
        assertThat(listAfterRemove.items()).isEmpty();
    }

    @Test
    void shouldReturn404WhenRemovingFromNonExistentList() {
        RestClient client = restClient();
        UUID nonExistentId = UUID.randomUUID();

        RemoveShoppingListItemRequest removeRequest = new RemoveShoppingListItemRequest(UUID.randomUUID());
        try {
            client
                    .post()
                    .uri("/shopping-lists/" + nonExistentId + "/remove")
                    .header("If-Match", "\"0\"")
                    .body(removeRequest)
                    .retrieve()
                    .toBodilessEntity();
            fail("Should have thrown 404");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(404);
        }
    }

    @Test
    void shouldReturn403WhenUserHasNoPermissionToRemoveItem() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates list and adds item
        CreateShoppingListRequest createRequest = new CreateShoppingListRequest("User 1 List");
        ShoppingListListDto list = user1Client
                .post()
                .uri("/shopping-lists")
                .body(createRequest)
                .retrieve()
                .body(ShoppingListListDto.class);

        assertThat(list).isNotNull();

        user1Client.post().uri("/shopping-lists/" + list.id() + "/add")
                .body(new AddShoppingListItemRequest("Item", null, null))
                .retrieve().body(ShoppingListListDto.class);

        ShoppingListDto listWithItem = user1Client
                .get()
                .uri("/shopping-lists/" + list.id())
                .retrieve()
                .body(ShoppingListDto.class);

        assertThat(listWithItem).isNotNull();
        assertThat(listWithItem.items()).hasSize(1);
        UUID itemId = listWithItem.items().getFirst().id();

        // User 2 tries to remove item (no permission) - should get 403
        RemoveShoppingListItemRequest removeRequest = new RemoveShoppingListItemRequest(itemId);
        try {
            user2Client
                    .post()
                    .uri("/shopping-lists/" + list.id() + "/remove")
                    .header("If-Match", "\"" + listWithItem.version() + "\"")
                    .body(removeRequest)
                    .retrieve()
                    .toBodilessEntity();
            fail("Should have thrown 403");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }
    }

    @Test
    void shouldReturn412OnVersionMismatchWhenRemovingItem() {
        RestClient client = restClient();

        // Create shopping list and add item
        CreateShoppingListRequest createRequest = new CreateShoppingListRequest("Groceries");
        ShoppingListListDto list = client
                .post()
                .uri("/shopping-lists")
                .body(createRequest)
                .retrieve()
                .body(ShoppingListListDto.class);

        assertThat(list).isNotNull();

        client.post().uri("/shopping-lists/" + list.id() + "/add")
                .body(new AddShoppingListItemRequest("Item 1", null, null))
                .retrieve().body(ShoppingListListDto.class);

        ShoppingListDto listWithItem = client
                .get()
                .uri("/shopping-lists/" + list.id())
                .retrieve()
                .body(ShoppingListDto.class);

        assertThat(listWithItem).isNotNull();
        assertThat(listWithItem.items()).hasSize(1);
        UUID itemId = listWithItem.items().getFirst().id();
        Long staleVersion = listWithItem.version();

        // Update list (add another item to change version)
        client.post().uri("/shopping-lists/" + list.id() + "/add")
                .body(new AddShoppingListItemRequest("Item 2", null, null))
                .retrieve().body(ShoppingListListDto.class);

        // Try to remove item with stale version - should get 412
        RemoveShoppingListItemRequest removeRequest = new RemoveShoppingListItemRequest(itemId);
        try {
            client
                    .post()
                    .uri("/shopping-lists/" + list.id() + "/remove")
                    .header("If-Match", "\"" + staleVersion + "\"")
                    .body(removeRequest)
                    .retrieve()
                    .toBodilessEntity();
            fail("Should have thrown 412");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(412);
        }
    }
}