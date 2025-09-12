package xyz.stasiak.recipai.extraction;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.content.Media;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/extract")
@RequiredArgsConstructor
@Slf4j
class ExtractionController {

    private final ExtractionService extractionService;

    @PostMapping("/text")
    public ExtractedRecipe extractFromText(@Valid @RequestBody ExtractTextRequest request) {
        log.debug("Extracting recipe from text");
        return extractionService.extractFromText(request.text());
    }

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExtractedRecipe extractFromImage(@RequestParam("file") MultipartFile file) {
        log.debug("Extracting recipe from uploaded image: {}", file.getOriginalFilename());

        // Validate MIME type
        String contentType = file.getContentType();
        if (!MimeTypeUtils.IMAGE_JPEG_VALUE.equals(contentType) && !MimeTypeUtils.IMAGE_PNG_VALUE.equals(contentType)) {
            throw new IllegalArgumentException("Unsupported file type. Only JPEG and PNG images are supported.");
        }

        // Convert MultipartFile to Media
        Resource imageResource = file.getResource();
        MimeType mimeType = MimeTypeUtils.parseMimeType(contentType);
        Media imageMedia = Media.builder()
                .mimeType(mimeType)
                .data(imageResource)
                .build();

        return extractionService.extractFromImage(imageMedia);
    }
}