import 'package:logging/logging.dart';

import '../get_it.dart';
import 'app_log_sink.dart';

/// Wires up log capture. Capture is **always on** — it is not gated by the
/// `LOGGING_ENABLED` feature flag (the flag gates only the "Send logs" UI,
/// consistent with the feature-flag standard of gating rendering only).
///
/// Registers the [AppLogSink] in `get_it` so the share action can reach it.
Future<void> setupLogging() async {
  final sink = AppLogSink();
  await sink.init();

  Logger.root.level = Level.ALL;
  Logger.root.onRecord.listen(sink.handle);

  getIt.registerSingleton<AppLogSink>(sink);
}
