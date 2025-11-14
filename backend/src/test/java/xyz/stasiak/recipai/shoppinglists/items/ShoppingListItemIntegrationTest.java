package xyz.stasiak.recipai.shoppinglists.items;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import xyz.stasiak.recipai.TestSecurityConfiguration;
import xyz.stasiak.recipai.TestcontainersConfiguration;
import xyz.stasiak.recipai.shoppinglists.dto.CreateShoppingListRequest;
import xyz.stasiak.recipai.shoppinglists.dto.ShoppingListDto;
import xyz.stasiak.recipai.shoppinglists.dto.ShoppingListListDto;
import xyz.stasiak.recipai.shoppinglists.items.dto.CreateShoppingListItemRequest;
import xyz.stasiak.recipai.shoppinglists.items.dto.ShoppingListItemDto;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@SuppressWarnings("ResultOfMethodCallIgnored")
@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ShoppingListItemIntegrationTest {

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

    private ShoppingListDto getShoppingList(RestClient client, UUID id) {
        return client
                .get()
                .uri("/shopping-lists/" + id)
                .retrieve()
                .body(ShoppingListDto.class);
    }

    @Test
    void shouldCreateItemWithEditorPermission() {
        RestClient client = restClient();

        // User creates a shopping list (becomes OWNER)
        ShoppingListListDto list = createShoppingList(client, "Test List");
        assertThat(list).isNotNull();
        assertThat(list.id()).isNotNull();

        // Create item with valid permissions
        CreateShoppingListItemRequest itemRequest = new CreateShoppingListItemRequest("Milk", BigDecimal.TWO, "liters");
        ShoppingListItemDto createdItem = client
                .post()
                .uri("/shopping-lists/" + list.id() + "/item")
                .body(itemRequest)
                .retrieve()
                .body(ShoppingListItemDto.class);

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
        CreateShoppingListItemRequest request1 = new CreateShoppingListItemRequest("Item 1", BigDecimal.ONE, "unit");
        ShoppingListItemDto item1 = client
                .post()
                .uri("/shopping-lists/" + list.id() + "/item")
                .body(request1)
                .retrieve()
                .body(ShoppingListItemDto.class);

        assertThat(item1).isNotNull();
        assertThat(item1.position()).isEqualByComparingTo(BigDecimal.ONE);

        // Create second item - position should be 2.0
        CreateShoppingListItemRequest request2 = new CreateShoppingListItemRequest("Item 2", BigDecimal.ONE, "unit");
        ShoppingListItemDto item2 = client
                .post()
                .uri("/shopping-lists/" + list.id() + "/item")
                .body(request2)
                .retrieve()
                .body(ShoppingListItemDto.class);

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
        CreateShoppingListItemRequest itemRequest = new CreateShoppingListItemRequest("Unauthorized Item", BigDecimal.ONE, "unit");

        try {
            user2Client
                    .post()
                    .uri("/shopping-lists/" + list.id() + "/item")
                    .body(itemRequest)
                    .retrieve()
                    .body(ShoppingListItemDto.class);
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
            String responseBody = ex.getResponseBodyAsString();
            assertThat(responseBody).contains("Access denied");
        }
    }

    @Test
    void shouldReturn403WhenCreatingItemForNonExistentList() {
        RestClient client = restClient();

        // Note: The permission check happens before list existence check,
        // so a non-existent list returns 403 (permission denied) rather than 404
        UUID nonExistentId = UUID.randomUUID();

        CreateShoppingListItemRequest itemRequest = new CreateShoppingListItemRequest("Item", BigDecimal.ONE, "unit");

        try {
            client
                    .post()
                    .uri("/shopping-lists/" + nonExistentId + "/item")
                    .body(itemRequest)
                    .retrieve()
                    .body(ShoppingListItemDto.class);
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
            String responseBody = ex.getResponseBodyAsString();
            assertThat(responseBody).contains("Access denied");
        }
    }

    @Test
    void shouldReturn400WhenCreatingItemWithInvalidData() {
        RestClient client = restClient();

        // Create a shopping list
        ShoppingListListDto list = createShoppingList(client, "Validation Test List");
        assertThat(list).isNotNull();

        // Try to create item with blank name (should fail validation)
        CreateShoppingListItemRequest invalidRequest = new CreateShoppingListItemRequest("", BigDecimal.ONE, "unit");

        try {
            client
                    .post()
                    .uri("/shopping-lists/" + list.id() + "/item")
                    .body(invalidRequest)
                    .retrieve()
                    .body(ShoppingListItemDto.class);
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
        CreateShoppingListItemRequest invalidRequest = new CreateShoppingListItemRequest(longName, BigDecimal.ONE, "unit");

        try {
            client
                    .post()
                    .uri("/shopping-lists/" + list.id() + "/item")
                    .body(invalidRequest)
                    .retrieve()
                    .body(ShoppingListItemDto.class);
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
        CreateShoppingListItemRequest itemRequest = new CreateShoppingListItemRequest("Item to Delete", BigDecimal.ONE, "unit");
        ShoppingListItemDto createdItem = client
                .post()
                .uri("/shopping-lists/" + list.id() + "/item")
                .body(itemRequest)
                .retrieve()
                .body(ShoppingListItemDto.class);

        assertThat(createdItem).isNotNull();
        assertThat(createdItem.id()).isNotNull();

        // Delete the item
        client
                .delete()
                .uri("/shopping-lists/" + list.id() + "/item/" + createdItem.id())
                .retrieve()
                .toBodilessEntity();

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
        CreateShoppingListItemRequest itemRequest = new CreateShoppingListItemRequest("Protected Item", BigDecimal.ONE, "unit");
        ShoppingListItemDto createdItem = user1Client
                .post()
                .uri("/shopping-lists/" + list.id() + "/item")
                .body(itemRequest)
                .retrieve()
                .body(ShoppingListItemDto.class);

        assertThat(createdItem).isNotNull();

        // User 2 tries to delete the item (should get 403 Forbidden)
        try {
            user2Client
                    .delete()
                    .uri("/shopping-lists/" + list.id() + "/item/" + createdItem.id())
                    .retrieve()
                    .toBodilessEntity();
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
            client
                    .delete()
                    .uri("/shopping-lists/" + list.id() + "/item/" + nonExistentItemId)
                    .retrieve()
                    .toBodilessEntity();
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
        CreateShoppingListItemRequest itemRequest = new CreateShoppingListItemRequest("Item in List 1", BigDecimal.ONE, "unit");
        ShoppingListItemDto createdItem = client
                .post()
                .uri("/shopping-lists/" + list1.id() + "/item")
                .body(itemRequest)
                .retrieve()
                .body(ShoppingListItemDto.class);

        assertThat(createdItem).isNotNull();

        // Try to delete the item from list 2 (should get 404)
        try {
            client
                    .delete()
                    .uri("/shopping-lists/" + list2.id() + "/item/" + createdItem.id())
                    .retrieve()
                    .toBodilessEntity();
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
        CreateShoppingListItemRequest request1 = new CreateShoppingListItemRequest("Apples", BigDecimal.valueOf(5), "pieces");
        client
                .post()
                .uri("/shopping-lists/" + list.id() + "/item")
                .body(request1)
                .retrieve()
                .body(ShoppingListItemDto.class);

        CreateShoppingListItemRequest request2 = new CreateShoppingListItemRequest("Bread", BigDecimal.valueOf(2), "loaves");
        client
                .post()
                .uri("/shopping-lists/" + list.id() + "/item")
                .body(request2)
                .retrieve()
                .body(ShoppingListItemDto.class);

        CreateShoppingListItemRequest request3 = new CreateShoppingListItemRequest("Milk", BigDecimal.valueOf(2), "liters");
        client
                .post()
                .uri("/shopping-lists/" + list.id() + "/item")
                .body(request3)
                .retrieve()
                .body(ShoppingListItemDto.class);

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
        CreateShoppingListItemRequest request1 = new CreateShoppingListItemRequest("Item 1", BigDecimal.ONE, "unit");
        client
                .post()
                .uri("/shopping-lists/" + list.id() + "/item")
                .body(request1)
                .retrieve()
                .body(ShoppingListItemDto.class);

        CreateShoppingListItemRequest request2 = new CreateShoppingListItemRequest("Item 2", BigDecimal.ONE, "unit");
        ShoppingListItemDto item2 = client
                .post()
                .uri("/shopping-lists/" + list.id() + "/item")
                .body(request2)
                .retrieve()
                .body(ShoppingListItemDto.class);

        assertThat(item2).isNotNull();

        CreateShoppingListItemRequest request3 = new CreateShoppingListItemRequest("Item 3", BigDecimal.ONE, "unit");
        client
                .post()
                .uri("/shopping-lists/" + list.id() + "/item")
                .body(request3)
                .retrieve()
                .body(ShoppingListItemDto.class);

        // Delete item 2
        client
                .delete()
                .uri("/shopping-lists/" + list.id() + "/item/" + item2.id())
                .retrieve()
                .toBodilessEntity();

        // Create a new item - should get position 4.0 (max was 3.0)
        CreateShoppingListItemRequest request4 = new CreateShoppingListItemRequest("Item 4", BigDecimal.ONE, "unit");
        ShoppingListItemDto item4 = client
                .post()
                .uri("/shopping-lists/" + list.id() + "/item")
                .body(request4)
                .retrieve()
                .body(ShoppingListItemDto.class);

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
        CreateShoppingListItemRequest itemRequest = new CreateShoppingListItemRequest("Item to Delete", BigDecimal.ONE, "unit");
        ShoppingListItemDto createdItem = client
                .post()
                .uri("/shopping-lists/" + list.id() + "/item")
                .body(itemRequest)
                .retrieve()
                .body(ShoppingListItemDto.class);

        assertThat(createdItem).isNotNull();

        // Delete and verify 204 No Content status
        var response = client
                .delete()
                .uri("/shopping-lists/" + list.id() + "/item/" + createdItem.id())
                .retrieve()
                .toEntity(Void.class);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }
}
