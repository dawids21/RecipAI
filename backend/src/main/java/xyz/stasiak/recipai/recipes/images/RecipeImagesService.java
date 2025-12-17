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
import java.util.stream.Collectors;

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

    public void uploadImages(UUID recipeId, List<UUID> newImages, List<MultipartFile> newImageFiles) {
        log.debug("Updating images for recipeId={}, newImages={}", recipeId, newImages);

        for (MultipartFile newImageFile : newImageFiles) {
            imageProcessingService.validateImageFile(newImageFile);
        }

        List<ImageMetadata> newImageFilesMetadata = extractMetadata(newImageFiles);
        RecipeImages recipeImages = recipeImagesRepository.findById(recipeId)
                .orElseGet(() -> createRecipeImages(recipeId));
        RecipeImagesUpdated result = recipeImages.updateImages(newImages, newImageFilesMetadata);

        Map<UUID, MultipartFile> imageFilesById = groupFilesById(newImageFiles);
        for (ImageMetadata imageToAdd : result.toAdd()) {
            if (!imageFilesById.containsKey(imageToAdd.id())) {
                throw new InvalidImageException("Image file missing for metadata entry with id: " + imageToAdd.id());
            }
        }
        uploadImagesToS3(recipeId, result.toAdd(), newImageFiles);
        deleteImagesFromS3(recipeId, result.toDelete());

        recipeImagesRepository.save(recipeImages);
        log.info("Images updated for recipeId={}", recipeId);
    }

    private List<ImageMetadata> extractMetadata(List<MultipartFile> imageFiles) {
        return imageFiles.stream()
                .map(imageFile -> new ImageMetadata(
                        UUID.fromString(FilenameUtils.getBaseName(imageFile.getOriginalFilename())),
                        new ContentType(Objects.requireNonNullElse(imageFile.getContentType(), ""))
                ))
                .toList();
    }

    private Map<UUID, MultipartFile> groupFilesById(List<MultipartFile> imageFiles) {
        return imageFiles.stream()
                .collect(Collectors.toMap(file -> UUID.fromString(FilenameUtils.getBaseName(file.getOriginalFilename())), file -> file));
    }

    private void deleteImagesFromS3(UUID recipeId, Set<ImageMetadata> imagesToDelete) {
        for (ImageMetadata imageToDelete : imagesToDelete) {
            try {
                s3Service.deleteImage(recipeId, imageToDelete.id(), imageToDelete.contentType());
            } catch (S3StorageException e) {
                log.error("Failed to delete image from S3, continuing: recipeId={}, imageId={}",
                        recipeId, imageToDelete.id(), e);
            }
        }
    }

    private void uploadImagesToS3(UUID recipeId, Set<ImageMetadata> newImages, List<MultipartFile> newImageFiles) {
        Map<UUID, MultipartFile> imageFilesById = groupFilesById(newImageFiles);
        for (ImageMetadata image : newImages) {
            UUID imageId = image.id();
            ContentType contentType = image.contentType();
            MultipartFile imageFile = imageFilesById.get(imageId);

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

    public void deleteAllImages(UUID recipeId) {
        log.debug("Deleting all imagesMetadata for recipeId={}", recipeId);
        try {
            s3Service.deleteAllRecipeImages(recipeId);
        } catch (S3StorageException e) {
            log.error("Failed to delete S3 imagesMetadata for recipe {}, manual cleanup may be required", recipeId, e);
        }
    }
}
