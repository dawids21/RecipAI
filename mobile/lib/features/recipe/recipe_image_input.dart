import 'package:image_picker/image_picker.dart';
import 'package:uuid/uuid.dart';

class RecipeImageInput {
  final String uuid;
  final XFile? file;
  final String? url;

  RecipeImageInput.existing(this.uuid, this.url) : file = null;

  RecipeImageInput.newImage(XFile imageFile)
    : uuid = const Uuid().v4(),
      file = imageFile,
      url = null;

  bool get isNewImage => file != null;

  bool get isExistingImage => url != null;
}
