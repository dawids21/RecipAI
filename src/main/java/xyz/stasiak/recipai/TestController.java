package xyz.stasiak.recipai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.Media;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.MalformedURLException;
import java.net.URI;

@RestController
@RequestMapping("/api")
public class TestController {
    private final ChatClient chatClient;

    public TestController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @GetMapping
    public String test() throws MalformedURLException {
        UserMessage userMessage = new UserMessage(
                "Explain what do you see on this picture?",
                new Media(
                        MimeTypeUtils.IMAGE_PNG,
                        URI.create("https://docs.spring.io/spring-ai/reference/_images/multimodal.test.png").toURL()
                )
        );
        ChatResponse chatResponse = chatClient.prompt(new Prompt(userMessage))
                .call()
                .chatResponse();
        return chatResponse.getResult().getOutput().getText();
    }
}
