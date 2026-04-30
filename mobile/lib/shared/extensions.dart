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
}

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
