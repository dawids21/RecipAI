import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

extension DateTimeLocalizations on DateTime {
  static int dartFirstDayOfWeek(BuildContext context) {
    final intlFirstDay = MaterialLocalizations.of(context).firstDayOfWeekIndex;
    return intlFirstDay == 0 ? 7 : intlFirstDay;
  }

  DateTime startOfWeek(BuildContext context) {
    final firstDay = DateTimeLocalizations.dartFirstDayOfWeek(context);
    final diff = (weekday - firstDay + 7) % 7;
    return DateTime(year, month, day - diff);
  }
}

extension IsoDateFormat on DateTime {
  String toIso8601DateString() {
    final isoDateFormat = DateFormat('yyyy-MM-dd');
    return isoDateFormat.format(this);
  }

  int get daysInMonth => DateTime(year, month + 1, 0).day;
}

extension UrlString on String {
  bool get isUrl {
    final trimmed = trim();

    if (trimmed.startsWith(RegExp(r'https?://'))) {
      try {
        final uri = Uri.parse(trimmed);
        return uri.hasScheme && uri.host.isNotEmpty;
      } catch (e) {
        return false;
      }
    }

    final domainPattern = RegExp(
      r'^([a-zA-Z0-9]([a-zA-Z0-9\-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z]{2,}$|^localhost(:\d+)?$',
      caseSensitive: false,
    );

    return domainPattern.hasMatch(trimmed);
  }

  /// Returns the first URL embedded in free-form text, or null when there is
  /// none. Used for shares that mix a URL with surrounding text, e.g.
  /// "Check this recipe: https://cookbook.com/recipe1".
  ///
  /// Only scheme-prefixed and `www.`-prefixed URLs are recognised — bare
  /// domains are too easily confused with ordinary prose ("done.Next time...").
  /// The result always carries a scheme so it can be loaded directly.
  String? get firstUrl {
    for (final match in _urlInTextPattern.allMatches(this)) {
      final candidate = _stripTrailingPunctuation(match.group(0)!);
      final withScheme = candidate.startsWith(_schemePattern)
          ? candidate
          : 'https://$candidate';
      final uri = Uri.tryParse(withScheme);
      if (uri == null) continue;
      if (uri.host.contains('.') || uri.host == 'localhost') return withScheme;
    }
    return null;
  }
}

final _schemePattern = RegExp(r'^https?://', caseSensitive: false);

final _urlInTextPattern = RegExp(
  r'(?:https?://|www\.)[^\s<>"]+',
  caseSensitive: false,
);

const _trailingPunctuation = '.,;:!?\'"…»';

const _closingBrackets = {')': '(', ']': '[', '}': '{'};

/// Trailing sentence punctuation is far more likely to belong to the sentence
/// than to the URL. A closing bracket is only dropped when it has no opener
/// inside the URL, so paths like `/wiki/Pierogi_(dish)` survive.
String _stripTrailingPunctuation(String url) {
  var result = url;
  while (result.isNotEmpty) {
    final last = result[result.length - 1];
    final opener = _closingBrackets[last];
    if (opener != null) {
      if (_count(result, opener) >= _count(result, last)) break;
    } else if (!_trailingPunctuation.contains(last)) {
      break;
    }
    result = result.substring(0, result.length - 1);
  }
  return result;
}

int _count(String text, String character) => text.split(character).length - 1;

extension ColorExtension on Color {
  String toHexString() {
    final r = (0xFF0000 & toARGB32()) >> 16;
    final g = (0x00FF00 & toARGB32()) >> 8;
    final b = (0x0000FF & toARGB32());
    return '#${r.toRadixString(16).padLeft(2, '0')}${g.toRadixString(16).padLeft(2, '0')}${b.toRadixString(16).padLeft(2, '0')}';
  }

  static Color fromHexString(String hexString) {
    return Color(int.parse(hexString.replaceFirst('#', '0xFF')));
  }
}
