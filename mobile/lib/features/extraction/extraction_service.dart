import 'package:flutter/foundation.dart';
import 'package:image_picker/image_picker.dart';

import '../../core/async_value.dart';
import '../auth/auth_service.dart';
import '../limits/limit_balance.dart';
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

  final ValueNotifier<AsyncValue<LimitBalance>> _extractionBalance =
      ValueNotifier(const AsyncValue.loading());

  ValueListenable<AsyncValue<LimitBalance>> get extractionBalance =>
      _extractionBalance;

  bool _isLoadExtractionBalanceRunning = false;

  Future<void> loadExtractionBalance() async {
    if (_isLoadExtractionBalanceRunning) return;
    _isLoadExtractionBalanceRunning = true;
    _extractionBalance.value = await AsyncValue.guardAsync(() async {
      final token = await _authService.idToken;
      return _extractionRepository.fetchExtractionBalance(token);
    });
    _isLoadExtractionBalanceRunning = false;
  }

  void dispose() {
    _extractionBalance.dispose();
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
