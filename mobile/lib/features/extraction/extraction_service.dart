import 'package:image_picker/image_picker.dart';

import 'extracted_recipe.dart';
import 'extraction_repository.dart';

class ExtractionService {
  final ExtractionRepository _extractionRepository;

  ExtractionService({required ExtractionRepository extractionRepository})
    : _extractionRepository = extractionRepository;

  Future<ExtractedRecipe> extractFromText(String htmlContent) async {
    return _extractionRepository.extractRecipeFromText(htmlContent);
  }

  Future<ExtractedRecipe> extractFromImage(XFile imageFile) async {
    return _extractionRepository.extractRecipeFromImage(imageFile);
  }
}
