package xyz.stasiak.recipai.config.s3;

import java.time.Duration;
import java.util.List;

public interface S3Service {
    void putObject(String key, String contentType, byte[] content);

    void deleteObjects(List<String> keys);

    List<String> listObjects(String prefix);

    String presignGetObject(String key, Duration expiration);
}
