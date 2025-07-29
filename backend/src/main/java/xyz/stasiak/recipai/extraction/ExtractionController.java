package xyz.stasiak.recipai.extraction;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.stasiak.recipai.recipes.RecipeDto;

@RestController
@RequestMapping("/extract")
@RequiredArgsConstructor
@Slf4j
class ExtractionController {

    private final ExtractionService extractionService;

    @PostMapping("/text")
    public RecipeDto extractFromText(@Valid @RequestBody ExtractTextRequest request) {
        log.debug("Extracting recipe from text");
        return extractionService.extractFromText(request.text());
    }
}