package xyz.stasiak.recipai.recipes.images;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import xyz.stasiak.recipai.config.s3.S3Properties;
import xyz.stasiak.recipai.recipes.images.dto.RecipeImageDto;
import xyz.stasiak.recipai.recipes.images.exception.InvalidImageException;
import xyz.stasiak.recipai.recipes.images.exception.S3StorageException;

import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeImagesService {

    private final RecipeImagesRepository recipeImagesRepository;
    private final S3Service s3Service;
    private final ImageProcessingService imageProcessingService = new ImageProcessingService();
    private final S3Properties s3Properties;

    public void createEmptyRecipeImages(UUID recipeId) {
        createRecipeImages(recipeId);
    }

    RecipeImages createRecipeImages(UUID recipeId) {
        log.debug("Creating RecipeImages for recipeId={}", recipeId);
        RecipeImages recipeImages = new RecipeImages(recipeId);
        return recipeImagesRepository.save(recipeImages);
    }

    public List<RecipeImageDto> findImagesById(UUID recipeId) {
        return recipeImagesRepository.findById(recipeId)
                .map(recipeImages -> toDto(recipeId, recipeImages.getImages().imagesMetadata()))
                .orElse(List.of());
    }

    private List<RecipeImageDto> toDto(UUID recipeId, List<ImageMetadata> imageMetadataList) {
        if (imageMetadataList == null || imageMetadataList.isEmpty()) {
            return List.of();
        }

        Duration expiration = Duration.ofMinutes(s3Properties.presignedUrlExpirationMinutes());

        return imageMetadataList.stream()
                .map(metadata -> {
                    try {
                        String imageKey = String.format("recipes/%s/%s.%s", recipeId, metadata.id(), metadata.contentType().toExtension());
                        String thumbnailKey = String.format("recipes/%s/%s-thumb.%s", recipeId, metadata.id(), metadata.contentType().toExtension());

                        String imageUrl = s3Service.generatePresignedUrl(imageKey, expiration);
                        String thumbnailUrl = s3Service.generatePresignedUrl(thumbnailKey, expiration);

                        return new RecipeImageDto(metadata.id(), imageUrl, thumbnailUrl);
                    } catch (S3StorageException e) {
                        log.error("Failed to generate presigned URLs for recipeId={}, imageId={}", recipeId, metadata.id(), e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    public String getFirstThumbnailUrl(UUID recipeId) {
        return recipeImagesRepository.findById(recipeId)
                .map(RecipeImages::getFirstImageMetadata)
                .map(metadata -> {
                    String thumbnailKey = String.format("recipes/%s/%s-thumb.%s", recipeId, metadata.id(), metadata.contentType().toExtension());
                    Duration expiration = Duration.ofMinutes(s3Properties.presignedUrlExpirationMinutes());
                    try {
                        return s3Service.generatePresignedUrl(thumbnailKey, expiration);
                    } catch (S3StorageException e) {
                        log.error("Failed to generate presigned URL for thumbnail: recipeId={}, imageId={}", recipeId, metadata.id(), e);
                        return null;
                    }
                })
                .orElse(null);
    }

    public void uploadImages(UUID recipeId, List<UUID> imageIds, List<MultipartFile> imageFiles) {
        if (imageIds == null || imageIds.isEmpty()) {
            log.debug("No image IDs provided for recipeId={}", recipeId);
            return;
        }

        if (imageFiles == null || imageFiles.isEmpty()) {
            log.debug("No image files provided for recipeId={}", recipeId);
            return;
        }

        // Match files to metadata
        List<ImageMetadataWithFile> matchedImages = matchFilesToMetadata(imageIds, imageFiles);

        // Validate all files
        for (ImageMetadataWithFile image : matchedImages) {
            imageProcessingService.validateImageFile(image.file());
        }

        RecipeImages recipeImages = recipeImagesRepository.findById(recipeId)
                .orElseGet(() -> createRecipeImages(recipeId));

        for (ImageMetadataWithFile image : matchedImages) {
            recipeImages.addImage(new ImageMetadata(image.imageId(), image.contentType()));
        }

        recipeImagesRepository.save(recipeImages);

        uploadImagesToS3(recipeId, matchedImages);
    }

    private List<ImageMetadataWithFile> matchFilesToMetadata(List<UUID> imageIds, List<MultipartFile> files) {
        List<ImageMetadataWithFile> matched = new ArrayList<>();

        for (UUID imageId : imageIds) {
            Optional<MultipartFile> matchedFile = files.stream()
                    .filter(file -> FilenameUtils.getBaseName(file.getOriginalFilename()).equals(imageId.toString()))
                    .findFirst();
            if (matchedFile.isEmpty()) {
                throw new InvalidImageException("Image file missing for metadata entry with id: " + imageId);
            }
            MultipartFile imageFile = matchedFile.get();
            ContentType contentType = new ContentType(Objects.requireNonNullElse(imageFile.getContentType(), ""));
            matched.add(new ImageMetadataWithFile(imageId, contentType, imageFile));
        }

        return matched;
    }

    public void deleteAllImages(UUID recipeId) {
        log.debug("Deleting all imagesMetadata for recipeId={}", recipeId);
        try {
            s3Service.deleteAllRecipeImages(recipeId);
        } catch (S3StorageException e) {
            log.error("Failed to delete S3 imagesMetadata for recipe {}, manual cleanup may be required", recipeId, e);
        }
    }

    private void uploadImagesToS3(UUID recipeId, List<ImageMetadataWithFile> images) {
        for (ImageMetadataWithFile image : images) {
            UUID imageId = image.imageId();
            ContentType contentType = image.contentType();
            MultipartFile imageFile = image.file();

            try {
                byte[] imageBytes = imageFile.getBytes();

                // Upload full-size image
                s3Service.uploadImage(recipeId, imageId, imageBytes, contentType);

                // Generate and upload thumbnail
                byte[] thumbnail = imageProcessingService.generateThumbnail(imageBytes);
                s3Service.uploadThumbnail(recipeId, imageId, thumbnail, ContentType.JPEG());

                log.debug("Successfully uploaded image and thumbnail for recipeId={}, imageId={}", recipeId, imageId);
            } catch (Exception e) {
                log.error("Failed to upload image for recipeId={}, imageId={}", recipeId, imageId, e);
            }
        }
    }

    private record ImageMetadataWithFile(UUID imageId, ContentType contentType, MultipartFile file) {
    }
}
