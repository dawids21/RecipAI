import 'dart:io';

import 'package:flutter/services.dart';

import '../get_it.dart';
import 'app_log_sink.dart';

/// Shares the current log file via the existing `recipai/share` platform
/// channel (Android `ACTION_SEND` through `ShareIntentBridge.kt`).
///
/// Android only — the native side only implements `shareFile` on Android. On
/// other platforms this no-ops cleanly rather than throwing.
Future<void> shareLogs() async {
  if (!Platform.isAndroid) return;

  final sink = getIt<AppLogSink>();
  final file = await sink.currentLogFileForSharing();

  await const MethodChannel('recipai/share').invokeMethod('shareFile', {
    'path': file.path,
    'mimeType': 'text/plain',
  });
}
