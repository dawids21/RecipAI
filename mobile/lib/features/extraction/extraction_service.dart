import 'package:image_picker/image_picker.dart';

import '../auth/auth_service.dart';
import 'extracted_recipe.dart';
import 'extraction_repository.dart';

class ExtractionService {
  final ExtractionRepository _extractionRepository;
  final AuthService _authService;

  ExtractionService({
    required ExtractionRepository extractionRepository,
    required AuthService authService,
  }) : _extractionRepository = extractionRepository,
       _authService = authService;

  Future<ExtractedRecipe> extractFromText(String htmlContent) async {
    final token = await _authService.idToken;
    return _extractionRepository.extractRecipeFromText(htmlContent, token);
  }

  Future<ExtractedRecipe> extractFromImage(XFile imageFile) async {
    final token = await _authService.idToken;
    return _extractionRepository.extractRecipeFromImage(imageFile, token);
  }
}
