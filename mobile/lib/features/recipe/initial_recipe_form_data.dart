import 'package:image_picker/image_picker.dart';

import 'recipe_detail.dart';

class InitialRecipeFormData {
  final RecipeDetail? recipeDetail;
  final String? sourceUrl;
  final List<XFile> pendingImages;

  const InitialRecipeFormData({
    this.recipeDetail,
    this.sourceUrl,
    this.pendingImages = const [],
  });
}
