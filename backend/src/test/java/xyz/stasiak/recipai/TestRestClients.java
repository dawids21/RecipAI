package xyz.stasiak.recipai;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

public final class TestRestClients {

    private TestRestClients() {
    }

    public static RestClient forToken(int port, String token) {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader("Authorization", "Bearer " + token)
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();
    }
}
