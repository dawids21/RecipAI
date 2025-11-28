package xyz.stasiak.recipai.recipes.collections;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import xyz.stasiak.recipai.TestSecurityConfiguration;
import xyz.stasiak.recipai.TestcontainersConfiguration;
import xyz.stasiak.recipai.recipes.collections.dto.CreateRecipesCollectionRequest;
import xyz.stasiak.recipai.recipes.collections.dto.RecipesCollectionListDto;
import xyz.stasiak.recipai.recipes.collections.dto.UpdateRecipesCollectionRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@SuppressWarnings("ResultOfMethodCallIgnored")
@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecipesCollectionIntegrationTest {

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

    private RecipesCollectionListDto createRecipesCollection(RestClient client, String name) {
        CreateRecipesCollectionRequest request = new CreateRecipesCollectionRequest(name);
        return client
                .post()
                .uri("/collections")
                .body(request)
                .retrieve()
                .body(RecipesCollectionListDto.class);
    }

    private List<RecipesCollectionListDto> getAllRecipesCollections(RestClient client) {
        return client
                .get()
                .uri("/collections")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private RecipesCollectionListDto updateRecipesCollection(RestClient client, UUID id, String newName) {
        UpdateRecipesCollectionRequest request = new UpdateRecipesCollectionRequest(newName);
        return client
                .put()
                .uri("/collections/" + id)
                .body(request)
                .retrieve()
                .body(RecipesCollectionListDto.class);
    }

    private void deleteRecipesCollection(RestClient client, UUID id) {
        client
                .delete()
                .uri("/collections/" + id)
                .retrieve()
                .toBodilessEntity();
    }

    @Test
    void shouldCreateRecipesCollection() {
        RestClient client = restClient();

        RecipesCollectionListDto response = createRecipesCollection(client, "My Recipes");

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
        assertThat(response.name()).isEqualTo("My Recipes");
    }

    @Test
    void shouldGetAllRecipesCollections() {
        RestClient client = restClient();

        // Create two collections
        createRecipesCollection(client, "Italian Recipes");
        createRecipesCollection(client, "Asian Recipes");

        // Get all collections
        List<RecipesCollectionListDto> collections = getAllRecipesCollections(client);

        assertThat(collections).isNotEmpty();
        assertThat(collections)
                .extracting(RecipesCollectionListDto::name)
                .contains("Italian Recipes", "Asian Recipes");
    }

    @Test
    void shouldUpdateRecipesCollection() {
        RestClient client = restClient();

        // Create collection
        RecipesCollectionListDto created = createRecipesCollection(client, "Old Name");

        assertThat(created).isNotNull();
        assertThat(created.name()).isEqualTo("Old Name");

        // Update collection
        RecipesCollectionListDto updated = updateRecipesCollection(client, created.id(), "New Name");

        assertThat(updated).isNotNull();
        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.name()).isEqualTo("New Name");
    }

    @Test
    void shouldDeleteRecipesCollection() {
        RestClient client = restClient();

        // Create collection
        RecipesCollectionListDto created = createRecipesCollection(client, "To Be Deleted");

        assertThat(created).isNotNull();

        // Delete collection
        deleteRecipesCollection(client, created.id());

        // Verify deleted by trying to get all collections - should not contain deleted one
        List<RecipesCollectionListDto> collections = getAllRecipesCollections(client);
        assertThat(collections)
                .extracting(RecipesCollectionListDto::id)
                .doesNotContain(created.id());
    }

    @Test
    void shouldReturn404WhenRecipesCollectionNotFound() {
        RestClient client = restClient();
        UUID nonExistentId = UUID.randomUUID();

        try {
            updateRecipesCollection(client, nonExistentId, "New Name");
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(404);
            String responseBody = ex.getResponseBodyAsString();
            assertThat(responseBody).contains("Recipes collection not found with id: " + nonExistentId);
            assertThat(responseBody).contains("Recipes Collection Not Found");
        }
    }

    @Test
    void shouldReturn403WhenAccessingOthersRecipesCollection() {
        RestClient user1Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_1);
        RestClient user2Client = restClient(TestSecurityConfiguration.AUTH_TOKEN_USER_2);

        // User 1 creates a collection
        RecipesCollectionListDto user1Collection = createRecipesCollection(user1Client, "User 1 Collection");

        assertThat(user1Collection).isNotNull();

        // User 2 tries to delete User 1's collection - should get 403 Forbidden
        try {
            deleteRecipesCollection(user2Client, user1Collection.id());
            fail("Should have thrown RestClientResponseException");
        } catch (RestClientResponseException ex) {
            assertThat(ex.getStatusCode().value()).isEqualTo(403);
            String responseBody = ex.getResponseBodyAsString();
            assertThat(responseBody).contains("Access denied to recipes collection with id: " + user1Collection.id());
            assertThat(responseBody).contains("Recipes Collection Access Denied");
        }
    }
}