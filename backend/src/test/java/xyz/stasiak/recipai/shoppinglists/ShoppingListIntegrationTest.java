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
    void shouldAllowSharedUserToViewList() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates and shares with User 2
        ShoppingListListDto list = createShoppingList(user1Client, "Shared Edit Test");
        shareShoppingList(user1Client, list.id(), "user2@example.com");

        // User 2 can access the list
        ShoppingListDto sharedList = getShoppingList(user2Client, list.id());
        assertThat(sharedList).isNotNull();
        assertThat(sharedList.name()).isEqualTo("Shared Edit Test");
        assertThat(sharedList.role()).isEqualTo(UserRole.EDITOR);

        // User 1 can also see the list
        ShoppingListDto listDto = getShoppingList(user1Client, list.id());
        assertThat(listDto).isNotNull();
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
