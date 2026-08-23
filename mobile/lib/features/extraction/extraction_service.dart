import 'package:flutter/foundation.dart';
import 'package:image_picker/image_picker.dart';

import '../../core/async_value.dart';
import '../auth/auth_service.dart';
import '../limits/limit_usage.dart';
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

  final ValueNotifier<AsyncValue<LimitUsage>> _extractionUsage = ValueNotifier(
    const AsyncValue.loading(),
  );

  ValueListenable<AsyncValue<LimitUsage>> get extractionUsage =>
      _extractionUsage;

  bool _isLoadExtractionUsageRunning = false;

  Future<void> loadExtractionUsage() async {
    if (_isLoadExtractionUsageRunning) return;
    _isLoadExtractionUsageRunning = true;
    _extractionUsage.value = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      return _extractionRepository.fetchExtractionUsage(token);
    });
    _isLoadExtractionUsageRunning = false;
  }

  void dispose() {
    _extractionUsage.dispose();
  }

  Future<ExtractedRecipe> extractFromText(String htmlContent) async {
    final token = await _authService.idToken;
    return _extractionRepository.extractRecipeFromText(htmlContent, token);
  }

  Future<ExtractedRecipe> extractFromImage(XFile imageFile) async {
    final token = await _authService.idToken;
    return _extractionRepository.extractRecipeFromImage(imageFile, token);
  }
}
