import 'package:flutter_test/flutter_test.dart';
import 'package:recipai_mobile/shared/extensions.dart';

void main() {
  group('String.firstUrl', () {
    test('extracts the url from text that surrounds it', () {
      expect(
        'Check this recipe: https://cookbook.com/recipe1'.firstUrl,
        'https://cookbook.com/recipe1',
      );
    });

    test('extracts the url when text follows it', () {
      expect(
        'https://cookbook.com/recipe1 looks great, try it'.firstUrl,
        'https://cookbook.com/recipe1',
      );
    });

    test('extracts the url from multi-line shared text', () {
      expect(
        'Pierogi\nhttps://cookbook.com/recipe1\nShared via Cookbook'.firstUrl,
        'https://cookbook.com/recipe1',
      );
    });

    test('returns the first url when the text has several', () {
      expect(
        'https://cookbook.com/recipe1 and https://other.com/recipe2'.firstUrl,
        'https://cookbook.com/recipe1',
      );
    });

    test('normalises a www-prefixed url to https', () {
      expect(
        'Try www.cookbook.com/recipe1 tonight'.firstUrl,
        'https://www.cookbook.com/recipe1',
      );
    });

    test('keeps query strings and fragments intact', () {
      expect(
        'here https://cookbook.com/r?id=1&x=2#steps ok'.firstUrl,
        'https://cookbook.com/r?id=1&x=2#steps',
      );
    });

    test('drops sentence punctuation that trails the url', () {
      expect(
        'Made this one: https://cookbook.com/recipe1.'.firstUrl,
        'https://cookbook.com/recipe1',
      );
      expect(
        'Made this one (https://cookbook.com/recipe1), it was good'.firstUrl,
        'https://cookbook.com/recipe1',
      );
    });

    test('keeps balanced brackets that belong to the url', () {
      expect(
        'see https://cookbook.com/wiki/Pierogi_(dish) for more'.firstUrl,
        'https://cookbook.com/wiki/Pierogi_(dish)',
      );
    });

    test('returns null for text without a url', () {
      expect('Check this recipe from my mum'.firstUrl, isNull);
    });

    test('ignores bare domains embedded in prose', () {
      expect('I made it today.Great result'.firstUrl, isNull);
    });

    test('returns the url unchanged when the text is only a url', () {
      expect(
        'https://cookbook.com/recipe1'.firstUrl,
        'https://cookbook.com/recipe1',
      );
    });
  });
}
