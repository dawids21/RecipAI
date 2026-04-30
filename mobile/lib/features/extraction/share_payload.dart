import 'dart:io';

import '../../shared/extensions.dart';

sealed class SharePayload {
  const SharePayload();

  static SharePayload? fromMap(Map<String, Object> map) {
    final type = map['type'] as String?;
    if (type == 'text') {
      final text = map['text'] as String?;
      if (text == null || text.isEmpty) return null;
      if (text.trim().isUrl) {
        return UrlSharePayload(text.trim());
      }
      return NonUrlTextSharePayload(text);
    }
    if (type == 'image') {
      final path = map['imagePath'] as String?;
      if (path == null || path.isEmpty) return null;
      return ImageSharePayload(File(path));
    }
    return null;
  }
}

class UrlSharePayload extends SharePayload {
  final String url;
  const UrlSharePayload(this.url);
}

class NonUrlTextSharePayload extends SharePayload {
  final String text;
  const NonUrlTextSharePayload(this.text);
}

class ImageSharePayload extends SharePayload {
  final File file;
  const ImageSharePayload(this.file);
}
