package xyz.stasiak.recipai.recipes.images;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xyz.stasiak.recipai.config.s3.S3Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
class ImageService {

    private final S3Service s3Service;

    void uploadImage(UUID recipeId, UUID imageId, byte[] imageData, ContentType contentType) {
        log.debug("Uploading image for recipeId={}, imageId={}", recipeId, imageId);
        String key = buildImageKey(recipeId, imageId, contentType.toExtension());
        s3Service.putObject(key, contentType.toString(), imageData);
    }

    void uploadThumbnail(UUID recipeId, UUID imageId, byte[] thumbnailData, ContentType contentType) {
        log.debug("Uploading thumbnail for recipeId={}, imageId={}", recipeId, imageId);
        String key = buildThumbnailKey(recipeId, imageId, contentType.toExtension());
        s3Service.putObject(key, contentType.toString(), thumbnailData);
    }

    String generatePresignedUrl(String objectKey, Duration expiration) {
        log.debug("Generating presigned URL for key: {}", objectKey);
        return s3Service.presignGetObject(objectKey, expiration);
    }

    void deleteImage(UUID recipeId, UUID imageId, ContentType contentType) {
        log.debug("Deleting image for recipeId={}, imageId={}", recipeId, imageId);
        String imageKey = buildImageKey(recipeId, imageId, contentType.toExtension());
        String thumbnailKey = buildThumbnailKey(recipeId, imageId, contentType.toExtension());
        s3Service.deleteObjects(List.of(imageKey, thumbnailKey));
    }

    void deleteAllRecipeImages(UUID recipeId) {
        log.debug("Deleting all images for recipeId={}", recipeId);
        String prefix = "recipes/" + recipeId + "/";
        List<String> keys = s3Service.listObjects(prefix);
        if (!keys.isEmpty()) {
            s3Service.deleteObjects(keys);
        }
    }

    private String buildImageKey(UUID recipeId, UUID imageId, String extension) {
        return String.format("recipes/%s/%s.%s", recipeId, imageId, extension);
    }

    private String buildThumbnailKey(UUID recipeId, UUID imageId, String extension) {
        return String.format("recipes/%s/%s-thumb.%s", recipeId, imageId, extension);
    }
}
