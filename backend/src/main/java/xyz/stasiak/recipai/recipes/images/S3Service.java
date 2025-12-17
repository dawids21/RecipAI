package xyz.stasiak.recipai.recipes.images;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import xyz.stasiak.recipai.config.s3.S3Properties;
import xyz.stasiak.recipai.recipes.images.exception.S3StorageException;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class S3Service {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    void uploadImage(UUID recipeId, UUID imageId, byte[] imageData, ContentType contentType) {
        String key = buildImageKey(recipeId, imageId, contentType.toExtension());

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3Properties.bucketName())
                    .key(key)
                    .contentType(contentType.toString())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(imageData));
            log.debug("Uploaded image to S3: {}", key);
        } catch (S3Exception e) {
            log.error("Failed to upload image to S3: recipeId={}, imageId={}", recipeId, imageId, e);
            throw new S3StorageException("Failed to upload image to S3", e);
        }
    }

    void uploadThumbnail(UUID recipeId, UUID imageId, byte[] thumbnailData, ContentType contentType) {
        String key = buildThumbnailKey(recipeId, imageId, contentType.toExtension());

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3Properties.bucketName())
                    .key(key)
                    .contentType(contentType.toString())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(thumbnailData));
            log.debug("Uploaded thumbnail to S3: {}", key);
        } catch (S3Exception e) {
            log.error("Failed to upload thumbnail to S3: recipeId={}, imageId={}", recipeId, imageId, e);
            throw new S3StorageException("Failed to upload thumbnail to S3", e);
        }
    }

    String generatePresignedUrl(String objectKey, Duration expiration) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(s3Properties.bucketName())
                    .key(objectKey)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(expiration)
                    .getObjectRequest(getObjectRequest)
                    .build();

            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
            return presignedRequest.url().toString();
        } catch (S3Exception e) {
            log.error("Failed to generate presigned URL for key: {}", objectKey, e);
            throw new S3StorageException("Failed to generate presigned URL", e);
        }
    }

    void deleteImage(UUID recipeId, UUID imageId, ContentType contentType) {
        String imageKey = buildImageKey(recipeId, imageId, contentType.toExtension());
        String thumbnailKey = buildThumbnailKey(recipeId, imageId, contentType.toExtension());

        try {
            var objectsToDelete = List.of(
                    ObjectIdentifier.builder().key(imageKey).build(),
                    ObjectIdentifier.builder().key(thumbnailKey).build()
            );

            DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                    .bucket(s3Properties.bucketName())
                    .delete(Delete.builder().objects(objectsToDelete).build())
                    .build();

            s3Client.deleteObjects(deleteRequest);
            log.debug("Deleted image and thumbnail from S3: recipeId={}, imageId={}", recipeId, imageId);
        } catch (S3Exception e) {
            log.error("Failed to delete image/thumbnail from S3: recipeId={}, imageId={}", recipeId, imageId, e);
            throw new S3StorageException("Failed to delete image from S3", e);
        }
    }

    void deleteAllRecipeImages(UUID recipeId) {
        String prefix = "recipes/" + recipeId + "/";

        try {
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(s3Properties.bucketName())
                    .prefix(prefix)
                    .build();

            ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);

            if (!listResponse.contents().isEmpty()) {
                var objectsToDelete = listResponse.contents().stream()
                        .map(s3Object -> ObjectIdentifier.builder().key(s3Object.key()).build())
                        .toList();

                DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                        .bucket(s3Properties.bucketName())
                        .delete(Delete.builder().objects(objectsToDelete).build())
                        .build();

                s3Client.deleteObjects(deleteRequest);
            }

            log.debug("Deleted all images for recipeId={}", recipeId);
        } catch (S3Exception e) {
            log.error("Failed to delete all images for recipeId={}", recipeId, e);
            throw new S3StorageException("Failed to delete recipe images from S3", e);
        }
    }

    private String buildImageKey(UUID recipeId, UUID imageId, String extension) {
        return String.format("recipes/%s/%s.%s", recipeId, imageId, extension);
    }

    private String buildThumbnailKey(UUID recipeId, UUID imageId, String extension) {
        return String.format("recipes/%s/%s-thumb.%s", recipeId, imageId, extension);
    }
}
