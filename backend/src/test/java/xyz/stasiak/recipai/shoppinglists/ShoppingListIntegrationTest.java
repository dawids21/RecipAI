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
import xyz.stasiak.recipai.shoppinglists.dto.*;

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

    private ShoppingListListDto createShoppingList(RestClient client, String name) {
        CreateShoppingListRequest request = new CreateShoppingListRequest(name);
        return client
                .post()
                .uri("/shopping-lists")
                .body(request)
                .retrieve()
                .body(ShoppingListListDto.class);
    }

    private List<ShoppingListListDto> getAllShoppingLists(RestClient client) {
        return client
                .get()
                .uri("/shopping-lists")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private ShoppingListDto getShoppingList(RestClient client, UUID id) {
        return client
                .get()
                .uri("/shopping-lists/" + id)
                .retrieve()
                .body(ShoppingListDto.class);
    }

    private ShoppingListListDto updateShoppingList(RestClient client, UUID id, String newName) {
        UpdateShoppingListRequest request = new UpdateShoppingListRequest(newName);
        return client
                .put()
                .uri("/shopping-lists/" + id)
                .body(request)
                .retrieve()
                .body(ShoppingListListDto.class);
    }

    private void deleteShoppingList(RestClient client, UUID id) {
        client
                .delete()
                .uri("/shopping-lists/" + id)
                .retrieve()
                .toBodilessEntity();
    }

    private ShoppingListItemDto createShoppingListItem(RestClient client, UUID shoppingListId, String name, BigDecimal quantity, String unit) {
        CreateShoppingListItemRequest request = new CreateShoppingListItemRequest(name, quantity, unit);
        return client
                .post()
                .uri("/shopping-lists/" + shoppingListId + "/item")
                .body(request)
                .retrieve()
                .body(ShoppingListItemDto.class);
    }

    private ShoppingListItemDto createShoppingListItem(RestClient client, UUID shoppingListId, String name) {
        return createShoppingListItem(client, shoppingListId, name, BigDecimal.ONE, "unit");
    }

    private void deleteShoppingListItem(RestClient client, UUID shoppingListId, UUID itemId, Long version) {
        client
                .delete()
                .uri("/shopping-lists/" + shoppingListId + "/item/" + itemId)
                .header("If-Match", version.toString())
                .retrieve()
                .toBodilessEntity();
    }

    private ShoppingListItemDto updateShoppingListItem(RestClient client, UUID shoppingListId, UUID itemId, Long version, String name, BigDecimal quantity, String unit) {
        UpdateShoppingListItemRequest request = new UpdateShoppingListItemRequest(name, quantity, unit);
        return client
                .put()
                .uri("/shopping-lists/" + shoppingListId + "/item/" + itemId)
                .header("If-Match", version.toString())
                .body(request)
                .retrieve()
                .body(ShoppingListItemDto.class);
    }

    private ShoppingListItemDto moveShoppingListItem(RestClient client, UUID shoppingListId, UUID itemId, Long version, int targetIndex) {
        MoveShoppingListItemRequest request = new MoveShoppingListItemRequest(targetIndex);
        return client
                .post()
                .uri("/shopping-lists/" + shoppingListId + "/item/" + itemId + "/move")
                .header("If-Match", version.toString())
                .body(request)
                .retrieve()
                .body(ShoppingListItemDto.class);
    }

    private ShoppingListItemDto checkShoppingListItem(RestClient client, UUID shoppingListId, UUID itemId, Long version) {
        return client
                .post()
                .uri("/shopping-lists/" + shoppingListId + "/item/" + itemId + "/check")
                .header("If-Match", version.toString())
                .retrieve()
                .body(ShoppingListItemDto.class);
    }

    private ShoppingListItemDto uncheckShoppingListItem(RestClient client, UUID shoppingListId, UUID itemId, Long version) {
        return client
                .post()
                .uri("/shopping-lists/" + shoppingListId + "/item/" + itemId + "/uncheck")
                .header("If-Match", version.toString())
                .retrieve()
                .body(ShoppingListItemDto.class);
    }

    private void shareShoppingList(RestClient client, UUID shoppingListId, String email) {
        ShareShoppingListRequest request = new ShareShoppingListRequest(email);
        client
                .post()
                .uri("/shopping-lists/" + shoppingListId + "/share")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    private void unshareShoppingList(RestClient client, UUID shoppingListId, String email) {
        UnshareShoppingListRequest request = new UnshareShoppingListRequest(email);
        client
                .post()
                .uri("/shopping-lists/" + shoppingListId + "/unshare")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    private List<SharedUserDto> getSharedUsers(RestClient client, UUID shoppingListId) {
        return client
                .get()
                .uri("/shopping-lists/" + shoppingListId + "/users")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    @Test
    void shouldCreateAndListShoppingLists() {
        RestClient client = restClient();

        // Create first shopping list
        ShoppingListListDto response1 = createShoppingList(client, "Groceries");

        assertThat(response1).isNotNull();
        assertThat(response1.id()).isNotNull();
        assertThat(response1.name()).isEqualTo("Groceries");

        // Create second shopping list
        ShoppingListListDto response2 = createShoppingList(client, "Hardware");

        assertThat(response2).isNotNull();
        assertThat(response2.id()).isNotNull();
        assertThat(response2.name()).isEqualTo("Hardware");

        // List all shopping lists
        List<ShoppingListListDto> listResponse = getAllShoppingLists(client);

        assertThat(listResponse).isNotEmpty();
        assertThat(listResponse).hasSizeGreaterThanOrEqualTo(2);
        assertThat(listResponse)
                .extracting(ShoppingListListDto::name)
                .contains("Groceries", "Hardware");
    }

    @Test
    void shouldValidateCreateShoppingListRequest() {
        RestClient client = restClient();

        try {
            createShoppingList(client, "");
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(400);
        }
    }

    @Test
    void shouldGetShoppingListById() {
        RestClient client = restClient();

        // Create a shopping list
        ShoppingListListDto createdList = createShoppingList(client, "Weekly Groceries");

        assertThat(createdList).isNotNull();
        assertThat(createdList.id()).isNotNull();

        // Get the shopping list by ID
        ShoppingListDto response = getShoppingList(client, createdList.id());

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(createdList.id());
        assertThat(response.name()).isEqualTo("Weekly Groceries");
        assertThat(response.items()).isNotNull();
        assertThat(response.items()).isEmpty();
        assertThat(response.role()).isEqualTo(UserRole.OWNER);
    }

    @Test
    void shouldReturn404WhenShoppingListNotFound() {
        RestClient client = restClient();
        UUID nonExistentId = UUID.randomUUID();

        try {
            getShoppingList(client, nonExistentId);
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
        ShoppingListListDto user1List = createShoppingList(user1Client, "User 1 List");

        assertThat(user1List).isNotNull();
        assertThat(user1List.name()).isEqualTo("User 1 List");

        // User 2 creates a shopping list
        ShoppingListListDto user2List = createShoppingList(user2Client, "User 2 List");

        assertThat(user2List).isNotNull();
        assertThat(user2List.name()).isEqualTo("User 2 List");

        // User 1 should only see their own list
        List<ShoppingListListDto> user1Lists = getAllShoppingLists(user1Client);

        assertThat(user1Lists)
                .extracting(ShoppingListListDto::id)
                .contains(user1List.id())
                .doesNotContain(user2List.id());

        // User 2 should only see their own list
        List<ShoppingListListDto> user2Lists = getAllShoppingLists(user2Client);

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
        ShoppingListListDto user1List = createShoppingList(user1Client, "User 1 Private List");

        assertThat(user1List).isNotNull();

        // User 2 tries to access User 1's list - should get 403 Forbidden
        try {
            getShoppingList(user2Client, user1List.id());
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
        ShoppingListListDto created = createShoppingList(client, "My Shopping List");

        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("My Shopping List");

        // UPDATE shopping list
        ShoppingListListDto updated = updateShoppingList(client, created.id(), "Updated List Name");

        assertThat(updated).isNotNull();
        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.name()).isEqualTo("Updated List Name");

        // DELETE shopping list
        deleteShoppingList(client, created.id());

        // VERIFY deleted (should return 404)
        try {
            getShoppingList(client, created.id());
            fail("Should have thrown 404");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(404);
        }
    }

    @Test
    void shouldReturn404WhenUpdatingNonExistentShoppingList() {
        RestClient client = restClient();
        UUID nonExistentId = UUID.randomUUID();

        try {
            updateShoppingList(client, nonExistentId, "Updated Name");
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
            deleteShoppingList(client, nonExistentId);
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
        ShoppingListListDto list = createShoppingList(user1Client, "User 1 List");

        assertThat(list).isNotNull();

        // User 2 tries to update (no permission) - should get 403
        try {
            updateShoppingList(user2Client, list.id(), "Hacked Name");
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
        ShoppingListListDto list = createShoppingList(user1Client, "User 1 List");

        assertThat(list).isNotNull();

        // User 2 tries to delete (no permission) - should get 403
        try {
            deleteShoppingList(user2Client, list.id());
            fail("Should have thrown 403");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }
    }

    @Test
    void shouldCreateItemWithEditorPermission() {
        RestClient client = restClient();

        // User creates a shopping list (becomes OWNER)
        ShoppingListListDto list = createShoppingList(client, "Test List");
        assertThat(list).isNotNull();
        assertThat(list.id()).isNotNull();

        // Create item with valid permissions
        ShoppingListItemDto createdItem = createShoppingListItem(client, list.id(), "Milk", BigDecimal.TWO, "liters");

        assertThat(createdItem).isNotNull();
        assertThat(createdItem.id()).isNotNull();
        assertThat(createdItem.name()).isEqualTo("Milk");
        assertThat(createdItem.quantity()).isEqualTo(BigDecimal.TWO);
        assertThat(createdItem.unit()).isEqualTo("liters");
        assertThat(createdItem.checked()).isFalse();
        assertThat(createdItem.position()).isNotNull();
        assertThat(createdItem.version()).isNotNull();
    }

    @Test
    void shouldAssignPositionAutomatically() {
        RestClient client = restClient();

        // Create a shopping list
        ShoppingListListDto list = createShoppingList(restClient(), "Position Test List");
        assertThat(list).isNotNull();

        // Create first item
        ShoppingListItemDto item1 = createShoppingListItem(client, list.id(), "Item 1");

        assertThat(item1).isNotNull();
        assertThat(item1.position()).isEqualByComparingTo(BigDecimal.ONE);

        // Create second item - position should be 2.0
        ShoppingListItemDto item2 = createShoppingListItem(client, list.id(), "Item 2");

        assertThat(item2).isNotNull();
        assertThat(item2.position()).isEqualByComparingTo(BigDecimal.valueOf(2.0));
    }

    @Test
    void shouldReturn403WhenCreatingItemWithoutPermission() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates a shopping list
        ShoppingListListDto list = createShoppingList(user1Client, "Private List");
        assertThat(list).isNotNull();

        // User 2 tries to create an item (should get 403 Forbidden)
        try {
            createShoppingListItem(user2Client, list.id(), "Unauthorized Item");
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
            String responseBody = ex.getResponseBodyAsString();
            assertThat(responseBody).contains("Access denied");
        }
    }

    @Test
    void shouldReturn404WhenCreatingItemForNonExistentList() {
        RestClient client = restClient();
        UUID nonExistentId = UUID.randomUUID();

        try {
            createShoppingListItem(client, nonExistentId, "Item");
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(404);
            String responseBody = ex.getResponseBodyAsString();
            assertThat(responseBody).contains("Shopping List Not Found");
        }
    }

    @Test
    void shouldReturn400WhenCreatingItemWithInvalidData() {
        RestClient client = restClient();

        // Create a shopping list
        ShoppingListListDto list = createShoppingList(client, "Validation Test List");
        assertThat(list).isNotNull();

        // Try to create item with blank name (should fail validation)
        try {
            createShoppingListItem(client, list.id(), "");
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(400);
        }
    }

    @Test
    void shouldReturn400WhenCreatingItemWithNameTooLong() {
        RestClient client = restClient();

        // Create a shopping list
        ShoppingListListDto list = createShoppingList(client, "Validation List");
        assertThat(list).isNotNull();

        // Try to create item with name longer than 255 characters
        String longName = "a".repeat(256);

        try {
            createShoppingListItem(client, list.id(), longName);
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(400);
        }
    }

    @Test
    void shouldDeleteItemWithOwnerPermission() {
        RestClient client = restClient();

        // Create a shopping list
        ShoppingListListDto list = createShoppingList(client, "Delete Test List");
        assertThat(list).isNotNull();

        // Create an item
        ShoppingListItemDto createdItem = createShoppingListItem(client, list.id(), "Item to Delete");

        assertThat(createdItem).isNotNull();
        assertThat(createdItem.id()).isNotNull();

        // Delete the item
        deleteShoppingListItem(client, list.id(), createdItem.id(), createdItem.version());

        // Verify item is deleted by checking the shopping list
        ShoppingListDto updatedList = getShoppingList(client, list.id());
        assertThat(updatedList).isNotNull();
        assertThat(updatedList.items()).isEmpty();
    }

    @Test
    void shouldReturn403WhenDeletingItemWithoutPermission() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // Create a shopping list with user 1
        ShoppingListListDto list = createShoppingList(user1Client, "Restricted List");
        assertThat(list).isNotNull();

        // Create an item with user 1
        ShoppingListItemDto createdItem = createShoppingListItem(user1Client, list.id(), "Protected Item");

        assertThat(createdItem).isNotNull();

        // User 2 tries to delete the item (should get 403 Forbidden)
        try {
            deleteShoppingListItem(user2Client, list.id(), createdItem.id(), createdItem.version());
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }
    }

    @Test
    void shouldReturn404WhenDeletingNonExistentItem() {
        RestClient client = restClient();

        // Create a shopping list
        ShoppingListListDto list = createShoppingList(client, "Delete Non-existent Test");
        assertThat(list).isNotNull();

        UUID nonExistentItemId = UUID.randomUUID();

        // Try to delete non-existent item (should get 404)
        try {
            deleteShoppingListItem(client, list.id(), nonExistentItemId, 0L);
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(404);
            String responseBody = ex.getResponseBodyAsString();
            assertThat(responseBody).contains("Shopping list item not found");
        }
    }

    @Test
    void shouldReturn404WhenDeletingItemFromWrongList() {
        RestClient client = restClient();

        // Create list 1
        ShoppingListListDto list1 = createShoppingList(client, "List 1");
        assertThat(list1).isNotNull();

        // Create list 2
        ShoppingListListDto list2 = createShoppingList(client, "List 2");
        assertThat(list2).isNotNull();

        // Create an item in list 1
        ShoppingListItemDto createdItem = createShoppingListItem(client, list1.id(), "Item in List 1");

        assertThat(createdItem).isNotNull();

        // Try to delete the item from list 2 (should get 404)
        try {
            deleteShoppingListItem(client, list2.id(), createdItem.id(), createdItem.version());
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(404);
        }
    }

    @Test
    void shouldCreateAndRetrieveMultipleItemsInOrder() {
        RestClient client = restClient();

        // Create a shopping list
        ShoppingListListDto list = createShoppingList(client, "Multi Item List");
        assertThat(list).isNotNull();

        // Create multiple items
        createShoppingListItem(client, list.id(), "Apples", BigDecimal.valueOf(5), "pieces");
        createShoppingListItem(client, list.id(), "Bread", BigDecimal.valueOf(2), "loaves");
        createShoppingListItem(client, list.id(), "Milk", BigDecimal.valueOf(2), "liters");

        // Retrieve the shopping list and verify all items are present and in order
        ShoppingListDto updatedList = getShoppingList(client, list.id());
        assertThat(updatedList).isNotNull();
        assertThat(updatedList.items()).hasSize(3);

        // Verify items are in the correct order by position
        assertThat(updatedList.items())
                .extracting(ShoppingListItemDto::name)
                .containsExactly("Apples", "Bread", "Milk");
        assertThat(updatedList.items())
                .extracting(ShoppingListItemDto::position)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(BigDecimal.ONE, BigDecimal.valueOf(2), BigDecimal.valueOf(3));
    }

    @Test
    void shouldDeleteItemAndContinuePositioningFromMax() {
        RestClient client = restClient();

        // Create a shopping list
        ShoppingListListDto list = createShoppingList(client, "Position Continuation List");
        assertThat(list).isNotNull();

        // Create three items
        createShoppingListItem(client, list.id(), "Item 1");

        ShoppingListItemDto item2 = createShoppingListItem(client, list.id(), "Item 2");

        assertThat(item2).isNotNull();

        createShoppingListItem(client, list.id(), "Item 3");

        // Delete item 2
        deleteShoppingListItem(client, list.id(), item2.id(), item2.version());

        // Create a new item - should get position 4.0 (max was 3.0)
        ShoppingListItemDto item4 = createShoppingListItem(client, list.id(), "Item 4");

        assertThat(item4).isNotNull();
        assertThat(item4.position()).isEqualByComparingTo(BigDecimal.valueOf(4.0));

        // Verify list contains items 1, 3, 4 (not 2)
        ShoppingListDto updatedList = getShoppingList(restClient(), list.id());
        assertThat(updatedList.items()).hasSize(3);
        assertThat(updatedList.items())
                .extracting(ShoppingListItemDto::name)
                .containsExactlyInAnyOrder("Item 1", "Item 3", "Item 4");
    }

    @Test
    void shouldReturnCreatedStatusCode() {
        RestClient client = restClient();

        // Create a shopping list
        ShoppingListListDto list = createShoppingList(client, "Status Code Test");
        assertThat(list).isNotNull();

        // Create an item and verify 201 Created status
        CreateShoppingListItemRequest itemRequest = new CreateShoppingListItemRequest("Test Item", BigDecimal.ONE, "unit");

        var response = client
                .post()
                .uri("/shopping-lists/" + list.id() + "/item")
                .body(itemRequest)
                .retrieve()
                .toEntity(ShoppingListItemDto.class);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void shouldReturnNoContentStatusCodeOnDelete() {
        RestClient client = restClient();

        // Create a shopping list
        ShoppingListListDto list = createShoppingList(client, "Delete Status Code Test");
        assertThat(list).isNotNull();

        // Create an item
        ShoppingListItemDto createdItem = createShoppingListItem(client, list.id(), "Item to Delete");

        assertThat(createdItem).isNotNull();

        // Delete and verify 204 No Content status
        var response = client
                .delete()
                .uri("/shopping-lists/" + list.id() + "/item/" + createdItem.id())
                .header("If-Match", createdItem.version().toString())
                .retrieve()
                .toEntity(Void.class);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void shouldReturn412WhenDeletingItemWithWrongVersion() {
        RestClient client = restClient();

        // Create a shopping list
        ShoppingListListDto list = createShoppingList(client, "Version Mismatch Test");
        assertThat(list).isNotNull();

        // Create an item
        ShoppingListItemDto createdItem = createShoppingListItem(client, list.id(), "Item to Delete");
        assertThat(createdItem).isNotNull();
        assertThat(createdItem.version()).isNotEqualTo(999L);

        // Try to delete with wrong version (should get 412 Precondition Failed)
        try {
            deleteShoppingListItem(client, list.id(), createdItem.id(), 999L);
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(412);
            String responseBody = ex.getResponseBodyAsString();
            assertThat(responseBody).contains("Version mismatch");
            assertThat(responseBody).contains("Shopping List Item Version Mismatch");
        }
    }

    @Test
    void shouldUpdateItemSuccessfully() {
        RestClient client = restClient();

        // Create a shopping list
        ShoppingListListDto list = createShoppingList(client, "Update Test List");
        assertThat(list).isNotNull();

        // Create an item
        ShoppingListItemDto createdItem = createShoppingListItem(client, list.id(), "Original Name", BigDecimal.ONE, "unit");
        assertThat(createdItem).isNotNull();
        assertThat(createdItem.version()).isNotNull();

        // Update the item
        ShoppingListItemDto updatedItem = updateShoppingListItem(
                client, list.id(), createdItem.id(), createdItem.version(),
                "Updated Name", BigDecimal.TWO, "liters"
        );

        assertThat(updatedItem).isNotNull();
        assertThat(updatedItem.id()).isEqualTo(createdItem.id());
        assertThat(updatedItem.name()).isEqualTo("Updated Name");
        assertThat(updatedItem.quantity()).isEqualByComparingTo(BigDecimal.TWO);
        assertThat(updatedItem.unit()).isEqualTo("liters");
        assertThat(updatedItem.version()).isGreaterThan(createdItem.version());
    }

    @Test
    void shouldReturn412WhenUpdatingItemWithWrongVersion() {
        RestClient client = restClient();

        // Create a shopping list and item
        ShoppingListListDto list = createShoppingList(client, "Update Version Test");
        ShoppingListItemDto createdItem = createShoppingListItem(client, list.id(), "Item", BigDecimal.ONE, "unit");

        // Try to update with wrong version
        try {
            updateShoppingListItem(client, list.id(), createdItem.id(), 999L, "New Name", BigDecimal.ONE, "unit");
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(412);
        }
    }

    @Test
    void shouldReturn403WhenUpdatingItemWithoutPermission() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates list and item
        ShoppingListListDto list = createShoppingList(user1Client, "Restricted List");
        ShoppingListItemDto item = createShoppingListItem(user1Client, list.id(), "Item", BigDecimal.ONE, "unit");

        // User 2 tries to update
        try {
            updateShoppingListItem(user2Client, list.id(), item.id(), item.version(), "Hacked", BigDecimal.ONE, "unit");
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }
    }

    @Test
    void shouldMoveItemToTopSuccessfully() {
        RestClient client = restClient();

        // Create a shopping list
        ShoppingListListDto list = createShoppingList(client, "Move Test List");
        assertThat(list).isNotNull();

        // Create three items
        ShoppingListItemDto item1 = createShoppingListItem(client, list.id(), "Item 1");
        createShoppingListItem(client, list.id(), "Item 2");
        ShoppingListItemDto item3 = createShoppingListItem(client, list.id(), "Item 3");

        // Move item 3 to index 0 (top)
        ShoppingListItemDto movedItem = moveShoppingListItem(client, list.id(), item3.id(), item3.version(), 0);

        assertThat(movedItem).isNotNull();
        assertThat(movedItem.position()).isLessThan(item1.position());
        assertThat(movedItem.version()).isGreaterThan(item3.version());

        // Verify order in list
        ShoppingListDto updatedList = getShoppingList(client, list.id());
        assertThat(updatedList.items()).hasSize(3);
        assertThat(updatedList.items().getFirst().name()).isEqualTo("Item 3");
    }

    @Test
    void shouldMoveItemToBottomSuccessfully() {
        RestClient client = restClient();

        // Create a shopping list
        ShoppingListListDto list = createShoppingList(client, "Move Bottom Test");
        assertThat(list).isNotNull();

        // Create three items
        ShoppingListItemDto item1 = createShoppingListItem(client, list.id(), "Item 1");
        createShoppingListItem(client, list.id(), "Item 2");
        ShoppingListItemDto item3 = createShoppingListItem(client, list.id(), "Item 3");

        // Move item 1 to index 2 (after item 3)
        ShoppingListItemDto movedItem = moveShoppingListItem(client, list.id(), item1.id(), item1.version(), 2);

        assertThat(movedItem).isNotNull();
        assertThat(movedItem.position()).isGreaterThan(item3.position());

        // Verify order in list
        ShoppingListDto updatedList = getShoppingList(client, list.id());
        assertThat(updatedList.items()).hasSize(3);
        assertThat(updatedList.items().get(2).name()).isEqualTo("Item 1");
    }

    @Test
    void shouldMoveItemBetweenItemsSuccessfully() {
        RestClient client = restClient();

        // Create a shopping list
        ShoppingListListDto list = createShoppingList(client, "Move Middle Test");
        assertThat(list).isNotNull();

        // Create three items
        ShoppingListItemDto item1 = createShoppingListItem(client, list.id(), "Item 1");
        ShoppingListItemDto item2 = createShoppingListItem(client, list.id(), "Item 2");
        ShoppingListItemDto item3 = createShoppingListItem(client, list.id(), "Item 3");

        // Move item 3 to index 1 (between item 1 and item 2)
        ShoppingListItemDto movedItem = moveShoppingListItem(client, list.id(), item3.id(), item3.version(), 1);

        assertThat(movedItem).isNotNull();
        assertThat(movedItem.position()).isGreaterThan(item1.position());
        assertThat(movedItem.position()).isLessThan(item2.position());

        // Verify order in list
        ShoppingListDto updatedList = getShoppingList(client, list.id());
        assertThat(updatedList.items()).hasSize(3);
        assertThat(updatedList.items().get(1).name()).isEqualTo("Item 3");
    }

    @Test
    void shouldReturn412WhenMovingItemWithWrongVersion() {
        RestClient client = restClient();

        // Create a shopping list and items
        ShoppingListListDto list = createShoppingList(client, "Move Version Test");
        ShoppingListItemDto item1 = createShoppingListItem(client, list.id(), "Item 1");
        createShoppingListItem(client, list.id(), "Item 2");

        // Try to move with wrong version
        try {
            moveShoppingListItem(client, list.id(), item1.id(), 999L, 1);
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(412);
        }
    }

    @Test
    void shouldCheckItemSuccessfully() {
        RestClient client = restClient();

        // Create a shopping list
        ShoppingListListDto list = createShoppingList(client, "Check Test List");
        assertThat(list).isNotNull();

        // Create an item
        ShoppingListItemDto createdItem = createShoppingListItem(client, list.id(), "Item to Check");
        assertThat(createdItem.checked()).isFalse();
        assertThat(createdItem.version()).isNotNull();

        // Check the item
        ShoppingListItemDto checkedItem = checkShoppingListItem(client, list.id(), createdItem.id(), createdItem.version());

        assertThat(checkedItem).isNotNull();
        assertThat(checkedItem.checked()).isTrue();
        assertThat(checkedItem.version()).isGreaterThan(createdItem.version());
    }

    @Test
    void shouldUncheckItemSuccessfully() {
        RestClient client = restClient();

        // Create a shopping list
        ShoppingListListDto list = createShoppingList(client, "Uncheck Test List");
        assertThat(list).isNotNull();

        // Create an item
        ShoppingListItemDto createdItem = createShoppingListItem(client, list.id(), "Item to Uncheck");

        // Check the item
        ShoppingListItemDto checkedItem = checkShoppingListItem(client, list.id(), createdItem.id(), createdItem.version());
        assertThat(checkedItem.checked()).isTrue();

        // Uncheck the item
        ShoppingListItemDto uncheckedItem = uncheckShoppingListItem(client, list.id(), checkedItem.id(), checkedItem.version());

        assertThat(uncheckedItem).isNotNull();
        assertThat(uncheckedItem.checked()).isFalse();
        assertThat(uncheckedItem.version()).isGreaterThan(checkedItem.version());
    }

    @Test
    void shouldCheckAndUncheckBeIdempotent() {
        RestClient client = restClient();

        // Create a shopping list and item
        ShoppingListListDto list = createShoppingList(client, "Idempotent Test");
        ShoppingListItemDto item = createShoppingListItem(client, list.id(), "Item", BigDecimal.ONE, "unit");

        // Check the item multiple times
        ShoppingListItemDto checked1 = checkShoppingListItem(client, list.id(), item.id(), item.version());
        assertThat(checked1.checked()).isTrue();

        ShoppingListItemDto checked2 = checkShoppingListItem(client, list.id(), checked1.id(), checked1.version());
        assertThat(checked2.checked()).isTrue();

        // Uncheck the item multiple times
        ShoppingListItemDto unchecked1 = uncheckShoppingListItem(client, list.id(), checked2.id(), checked2.version());
        assertThat(unchecked1.checked()).isFalse();

        ShoppingListItemDto unchecked2 = uncheckShoppingListItem(client, list.id(), unchecked1.id(), unchecked1.version());
        assertThat(unchecked2.checked()).isFalse();
    }

    @Test
    void shouldReturn412WhenCheckingItemWithWrongVersion() {
        RestClient client = restClient();

        // Create a shopping list and item
        ShoppingListListDto list = createShoppingList(client, "Check Version Test");
        ShoppingListItemDto item = createShoppingListItem(client, list.id(), "Item", BigDecimal.ONE, "unit");

        // Try to check with wrong version
        try {
            checkShoppingListItem(client, list.id(), item.id(), 999L);
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(412);
        }
    }

    @Test
    void shouldReturn403WhenCheckingItemWithoutPermission() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates list and item
        ShoppingListListDto list = createShoppingList(user1Client, "Check Permission Test");
        ShoppingListItemDto item = createShoppingListItem(user1Client, list.id(), "Item", BigDecimal.ONE, "unit");

        // User 2 tries to check
        try {
            checkShoppingListItem(user2Client, list.id(), item.id(), item.version());
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }
    }

    @Test
    void shouldShareAndUnshareShoppingLists() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates a shopping list
        ShoppingListListDto list = createShoppingList(user1Client, "Shared List");
        assertThat(list).isNotNull();

        // User 2 cannot access initially
        try {
            getShoppingList(user2Client, list.id());
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // User 1 shares with User 2
        shareShoppingList(user1Client, list.id(), "user2@example.com");

        // User 2 can now access the list
        ShoppingListDto sharedList = getShoppingList(user2Client, list.id());
        assertThat(sharedList).isNotNull();
        assertThat(sharedList.name()).isEqualTo("Shared List");
        assertThat(sharedList.role()).isEqualTo(UserRole.EDITOR);

        // Verify shared users list
        List<SharedUserDto> sharedUsers = getSharedUsers(user1Client, list.id());
        assertThat(sharedUsers).hasSize(2);
        assertThat(sharedUsers)
                .extracting(SharedUserDto::email)
                .containsExactly("user1@example.com", "user2@example.com");
        assertThat(sharedUsers)
                .extracting(SharedUserDto::role)
                .containsExactly(UserRole.OWNER, UserRole.EDITOR);

        // User 1 unshares from User 2
        unshareShoppingList(user1Client, list.id(), "user2@example.com");

        // User 2 can no longer access
        try {
            getShoppingList(user2Client, list.id());
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }
    }

    @Test
    void shouldAllowEditorsToShareAndUnshare() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates and shares with User 2
        ShoppingListListDto list = createShoppingList(user1Client, "Editor Share Test");
        shareShoppingList(user1Client, list.id(), "user2@example.com");

        // User 2 (EDITOR) can share with another user
        shareShoppingList(user2Client, list.id(), "user@example.com");

        // Verify three users have access
        List<SharedUserDto> sharedUsers = getSharedUsers(user1Client, list.id());
        assertThat(sharedUsers).hasSize(3);

        // User 2 (EDITOR) can unshare
        unshareShoppingList(user2Client, list.id(), "user@example.com");

        // Verify only two users remain
        sharedUsers = getSharedUsers(user1Client, list.id());
        assertThat(sharedUsers).hasSize(2);
    }

    @Test
    void shouldPreventUnsharingOwner() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates and shares with User 2
        ShoppingListListDto list = createShoppingList(user1Client, "Unshare Owner Test");
        shareShoppingList(user1Client, list.id(), "user2@example.com");

        // User 2 tries to unshare User 1 (OWNER) - should fail
        try {
            unshareShoppingList(user2Client, list.id(), "user1@example.com");
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // Verify User 1 still has access
        List<SharedUserDto> sharedUsers = getSharedUsers(user1Client, list.id());
        assertThat(sharedUsers).hasSize(2);
        assertThat(sharedUsers)
                .extracting(SharedUserDto::email)
                .contains("user1@example.com");
    }

    @Test
    void shouldAllowEditorToUnshareThemselves() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates and shares with User 2
        ShoppingListListDto list = createShoppingList(user1Client, "Self Unshare Test");
        shareShoppingList(user1Client, list.id(), "user2@example.com");

        // User 2 can unshare themselves
        unshareShoppingList(user2Client, list.id(), "user2@example.com");

        // User 2 can no longer access
        try {
            getShoppingList(user2Client, list.id());
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }
    }

    @Test
    void shouldPreventOwnerFromUnsharingThemselves() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);

        // User 1 creates a list
        ShoppingListListDto list = createShoppingList(user1Client, "Owner Self Unshare Test");

        // User 1 tries to unshare themselves - should fail
        try {
            unshareShoppingList(user1Client, list.id(), "user1@example.com");
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // Verify User 1 still has access
        ShoppingListDto listDto = getShoppingList(user1Client, list.id());
        assertThat(listDto).isNotNull();
    }

    @Test
    void shouldHandleDuplicateShareAsNoOp() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);

        // User 1 creates and shares with User 2
        ShoppingListListDto list = createShoppingList(user1Client, "Duplicate Share Test");
        shareShoppingList(user1Client, list.id(), "user2@example.com");

        // Share again - should be no-op
        shareShoppingList(user1Client, list.id(), "user2@example.com");

        // Verify still only 2 users
        List<SharedUserDto> sharedUsers = getSharedUsers(user1Client, list.id());
        assertThat(sharedUsers).hasSize(2);
    }

    @Test
    void shouldHandleUnshareNonExistentAsNoOp() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);

        // User 1 creates a list
        ShoppingListListDto list = createShoppingList(user1Client, "Unshare Non-existent Test");

        // Unshare someone who doesn't have access - should be no-op
        unshareShoppingList(user1Client, list.id(), "nonexistent@example.com");

        // Verify still only 1 user
        List<SharedUserDto> sharedUsers = getSharedUsers(user1Client, list.id());
        assertThat(sharedUsers).hasSize(1);
    }

    @Test
    void shouldAllowSharedUserToEditItems() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates and shares with User 2
        ShoppingListListDto list = createShoppingList(user1Client, "Shared Edit Test");
        shareShoppingList(user1Client, list.id(), "user2@example.com");

        // User 2 can create items
        ShoppingListItemDto item = createShoppingListItem(user2Client, list.id(), "User 2 Item");
        assertThat(item).isNotNull();
        assertThat(item.name()).isEqualTo("User 2 Item");

        // User 2 can update items
        ShoppingListItemDto updated = updateShoppingListItem(user2Client, list.id(), item.id(), item.version(), "Updated Item", BigDecimal.TWO, "pieces");
        assertThat(updated.name()).isEqualTo("Updated Item");

        // User 1 can see the changes
        ShoppingListDto listDto = getShoppingList(user1Client, list.id());
        assertThat(listDto.items()).hasSize(1);
        assertThat(listDto.items().getFirst().name()).isEqualTo("Updated Item");
    }

    @Test
    void shouldPreventSharedUserFromDeletingList() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates and shares with User 2
        ShoppingListListDto list = createShoppingList(user1Client, "Delete Permission Test");
        shareShoppingList(user1Client, list.id(), "user2@example.com");

        // User 2 (EDITOR) tries to delete - should fail
        try {
            deleteShoppingList(user2Client, list.id());
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
        }

        // Verify list still exists
        ShoppingListDto listDto = getShoppingList(user1Client, list.id());
        assertThat(listDto).isNotNull();
    }
}