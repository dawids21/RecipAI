package xyz.stasiak.recipai.extraction;

import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

public record ExtractImageRequest(@NotNull MultipartFile file) {
}