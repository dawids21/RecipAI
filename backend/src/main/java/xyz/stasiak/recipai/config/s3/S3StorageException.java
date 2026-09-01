package xyz.stasiak.recipai.config.s3;

public class S3StorageException extends RuntimeException {
    public S3StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
