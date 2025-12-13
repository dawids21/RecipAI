package xyz.stasiak.recipai.recipes.images.exception;

public class S3StorageException extends RuntimeException {
    public S3StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
