package xyz.stasiak.recipai.config.s3;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "recipai.s3")
public record S3Properties(
        String bucketName,
        String region,
        int presignedUrlExpirationMinutes
) {
}
