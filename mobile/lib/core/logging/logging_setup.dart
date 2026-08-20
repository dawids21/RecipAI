import 'package:flutter/foundation.dart';
import 'package:logging/logging.dart';

import '../get_it.dart';
import 'app_log_sink.dart';

/// Wires up log capture. Capture is **always on** — it is not gated by any
/// feature flag.
///
/// Registers the [AppLogSink] in `get_it` so the share action can reach it.
Future<void> setupLogging() async {
  final sink = AppLogSink();
  await sink.init();

  Logger.root.level = Level.ALL;
  Logger.root.onRecord.listen(sink.handle);

  // In debug builds also echo records to the console so they show up in
  // `flutter logs` / the run console. Stripped from release/profile builds.
  if (kDebugMode) {
    Logger.root.onRecord.listen((record) {
      debugPrint('${record.level.name}  ${record.loggerName}  ${record.message}');
      if (record.error != null) {
        debugPrint('  error: ${record.error}');
      }
      if (record.stackTrace != null) {
        debugPrint(record.stackTrace.toString());
      }
    });
  }

  getIt.registerSingleton<AppLogSink>(sink);
}
