import 'dart:ui';

import 'package:intl/intl.dart';

extension IsoDateFormat on DateTime {
  String toIso8601DateString() {
    final isoDateFormat = DateFormat('yyyy-MM-dd');
    return isoDateFormat.format(this);
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
