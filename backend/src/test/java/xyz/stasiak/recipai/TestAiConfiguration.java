package xyz.stasiak.recipai;

import lombok.Getter;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestAiConfiguration {

    @Getter
    private final ChatClient chatClient = Mockito.mock(ChatClient.class, Mockito.RETURNS_DEEP_STUBS);

    @Bean
    @Primary
    public ChatClient mockChatClient() {
        return chatClient;
    }
}
