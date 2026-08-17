import 'package:flutter_test/flutter_test.dart';
import 'package:recipai_mobile/features/extraction/share_payload.dart';

void main() {
  group('SharePayload.fromMap text shares', () {
    test('classifies a url-only share as a url payload', () {
      final payload = SharePayload.fromMap({
        'type': 'text',
        'text': ' https://cookbook.com/recipe1 ',
      });

      expect(payload, isA<UrlSharePayload>());
      expect((payload as UrlSharePayload).url, 'https://cookbook.com/recipe1');
    });

    test('classifies a bare domain as a url payload', () {
      final payload = SharePayload.fromMap({
        'type': 'text',
        'text': 'cookbook.com',
      });

      expect(payload, isA<UrlSharePayload>());
      expect((payload as UrlSharePayload).url, 'cookbook.com');
    });

    test('extracts the url from a share that mixes text and url', () {
      final payload = SharePayload.fromMap({
        'type': 'text',
        'text': 'Check this recipe: https://cookbook.com/recipe1',
      });

      expect(payload, isA<UrlSharePayload>());
      expect((payload as UrlSharePayload).url, 'https://cookbook.com/recipe1');
    });

    test('classifies text without a url as a non-url payload', () {
      final payload = SharePayload.fromMap({
        'type': 'text',
        'text': 'Check this recipe from my mum',
      });

      expect(payload, isA<NonUrlTextSharePayload>());
      expect(
        (payload as NonUrlTextSharePayload).text,
        'Check this recipe from my mum',
      );
    });

    test('returns null for empty text', () {
      expect(SharePayload.fromMap({'type': 'text', 'text': ''}), isNull);
    });
  });

  group('SharePayload.fromMap image shares', () {
    test('classifies an image path as an image payload', () {
      final payload = SharePayload.fromMap({
        'type': 'image',
        'imagePath': '/cache/share_intent/1.jpg',
      });

      expect(payload, isA<ImageSharePayload>());
      expect(
        (payload as ImageSharePayload).file.path,
        '/cache/share_intent/1.jpg',
      );
    });

    test('returns null for an unknown type', () {
      expect(SharePayload.fromMap({'type': 'pdf'}), isNull);
    });
  });
}
