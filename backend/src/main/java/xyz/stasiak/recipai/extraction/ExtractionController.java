package xyz.stasiak.recipai.extraction;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.content.Media;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import xyz.stasiak.recipai.limits.LimitBalance;

@RestController
@RequestMapping("/extract")
@RequiredArgsConstructor
@Slf4j
class ExtractionController {

    private final ExtractionService extractionService;

    @GetMapping("/balance")
    LimitBalance getBalance(@AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Getting extraction balance for user: {}", userEmail);
        return extractionService.balance(userEmail);
    }

    @PostMapping("/text")
    ExtractedRecipe extractFromText(@Valid @RequestBody ExtractTextRequest request, @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Extracting recipe from text for user: {}", userEmail);
        return extractionService.extractFromText(request.text(), userEmail);
    }

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ExtractedRecipe extractFromImage(@RequestParam("file") MultipartFile file, @AuthenticationPrincipal Jwt jwt) {
        String userEmail = jwt.getClaimAsString("email");
        log.debug("Extracting recipe from uploaded image: {} for user: {}", file.getOriginalFilename(), userEmail);

        // Validate MIME type
        String contentType = file.getContentType();
        if (!MimeTypeUtils.IMAGE_JPEG_VALUE.equals(contentType) && !MimeTypeUtils.IMAGE_PNG_VALUE.equals(contentType)) {
            throw new UnsupportedImageTypeException("Unsupported file type. Only JPEG and PNG images are supported.");
        }

        // Convert MultipartFile to Media
        Resource imageResource = file.getResource();
        MimeType mimeType = MimeTypeUtils.parseMimeType(contentType);
        Media imageMedia = Media.builder()
                .mimeType(mimeType)
                .data(imageResource)
                .build();

        return extractionService.extractFromImage(imageMedia, userEmail);
    }
}