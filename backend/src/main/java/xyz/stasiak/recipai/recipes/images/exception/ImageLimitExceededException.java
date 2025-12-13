package xyz.stasiak.recipai.recipes.images.exception;

public class ImageLimitExceededException extends RuntimeException {
    public ImageLimitExceededException(String message) {
        super(message);
    }
}
