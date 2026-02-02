import 'package:intl/intl.dart';

extension IsoDateFormat on DateTime {
  String toIso8601DateString() {
    final isoDateFormat = DateFormat('yyyy-MM-dd');
    return isoDateFormat.format(this);
  }
}
