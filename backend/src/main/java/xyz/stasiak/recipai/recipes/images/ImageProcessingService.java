package xyz.stasiak.recipai.recipes.images;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.web.multipart.MultipartFile;
import xyz.stasiak.recipai.recipes.images.exception.InvalidImageException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Slf4j
class ImageProcessingService {

    private static final int THUMBNAIL_SIZE = 300;
    private static final double THUMBNAIL_QUALITY = 0.8;
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    byte[] generateThumbnail(byte[] originalImage) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            Thumbnails.of(new ByteArrayInputStream(originalImage))
                    .size(THUMBNAIL_SIZE, THUMBNAIL_SIZE)
                    .outputFormat("jpg")
                    .outputQuality(THUMBNAIL_QUALITY)
                    .toOutputStream(outputStream);

            return outputStream.toByteArray();
        } catch (IOException e) {
            log.error("Failed to generate thumbnail", e);
            throw new InvalidImageException("Failed to process image: " + e.getMessage());
        }
    }

    void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidImageException("Image file cannot be empty");
        }

        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg") && !contentType.equals("image/png"))) {
            throw new InvalidImageException("Invalid image format. Only JPEG and PNG are allowed");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidImageException("Image size exceeds maximum allowed size of 5MB");
        }
    }
}
