package xyz.stasiak.recipai.config.s3;

import java.time.Duration;
import java.util.List;

class NoopS3Service implements S3Service {

    @Override
    public void putObject(String key, String contentType, byte[] content) {
    }

    @Override
    public void deleteObjects(List<String> keys) {
    }

    @Override
    public List<String> listObjects(String prefix) {
        return List.of();
    }

    @Override
    public String presignGetObject(String key, Duration expiration) {
        return "https://noop.local.test/" + key;
    }
}
