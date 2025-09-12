## FEATURE:

I want to add an API endpoint to extract recipe information from an image.
Users should be able to upload an image of a recipe and receive structured recipe data in response.
Add new endpoint `/extract/image` in the extraction module.
Images may be in different formats (JPEG, PNG).

## EXAMPLES:

### How to pass image to the ChatClient

```java
Media imageMedia = new Media(MimeTypeUtils.IMAGE_JPEG, imageResource);
UserMessage userMessage = UserMessage.builder()
        .media(imageMedia)
        .text("What is in this image?")
        .build();

String response = chatClient.prompt(new Prompt(userMessage))
        .call()
        .content();
```

## DOCUMENTATION:

- `docs/backend/api.md` - API documentation

## OTHER CONSIDERATIONS:

- Follow similar patterns as the existing text extraction endpoint.